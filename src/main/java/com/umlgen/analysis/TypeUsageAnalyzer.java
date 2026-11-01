package com.umlgen.analysis;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Analiza tipos de Java para descubrir qué tipos del proyecto aparecen y cómo.
 * <ul>
 *   <li>{@link #structuralUses}: para atributos. Reconoce referencias directas, arrays y colecciones
 *       (multiplicidad "*"), y Optional (0..1). Los genéricos externos desconocidos se informan como ambiguos.</li>
 *   <li>{@link #allReferences}: para dependencias. Cualquier tipo del proyecto que aparezca en el nodo.</li>
 * </ul>
 */
public final class TypeUsageAnalyzer {
    private final TypeReferenceResolver resolver;

    public TypeUsageAnalyzer(TypeReferenceResolver resolver) {
        this.resolver = resolver;
    }

    public List<TypeUse> structuralUses(Type type, Consumer<String> ambiguityReporter) {
        List<TypeUse> uses = new ArrayList<>();
        collectStructural(type, null, uses, ambiguityReporter);
        return uses;
    }

    private void collectStructural(Type type, String multiplicity, List<TypeUse> out, Consumer<String> amb) {
        if (type instanceof ArrayType at) {
            collectStructural(at.getComponentType(), "*", out, amb);
        } else if (type instanceof WildcardType wt) {
            wt.getExtendedType().ifPresent(t -> collectStructural(t, multiplicity, out, amb));
            wt.getSuperType().ifPresent(t -> collectStructural(t, multiplicity, out, amb));
        } else if (type instanceof ClassOrInterfaceType ct) {
            ResolvedType r = resolver.resolve(ct);
            List<Type> args = ct.getTypeArguments().map(List::copyOf).orElse(List.of());
            switch (r.kind()) {
                case PROJECT -> {
                    out.add(new TypeUse(r.projectType(), multiplicity));
                    for (TypeInfo inArgs : projectTypesIn(args)) {
                        amb.accept("argumento genérico " + inArgs.displayName() + " dentro del tipo del proyecto "
                                + r.projectType().displayName() + " (relación no determinada con seguridad)");
                    }
                }
                case EXTERNAL, UNRESOLVED -> {
                    String simple = r.simpleName();
                    if (JavaStandardTypes.isCollection(simple)) {
                        for (Type a : args) {
                            collectStructural(a, "*", out, amb);
                        }
                    } else if (JavaStandardTypes.isOptional(simple)) {
                        for (Type a : args) {
                            collectStructural(a, multiplicity == null ? "0..1" : multiplicity, out, amb);
                        }
                    } else {
                        for (TypeInfo inArgs : projectTypesIn(args)) {
                            amb.accept(inArgs.displayName() + " usado como argumento del tipo genérico externo "
                                    + simple + " (relación no determinada con seguridad)");
                        }
                    }
                }
                case AMBIGUOUS -> amb.accept("el tipo '" + r.name() + "' es ambiguo, candidatos: "
                        + String.join(", ", r.candidates()));
                case TYPE_VARIABLE -> {
                    // un parámetro de tipo genérico no es una clase concreta
                }
            }
        }
    }

    private Set<TypeInfo> projectTypesIn(List<Type> types) {
        Set<TypeInfo> result = new LinkedHashSet<>();
        for (Type t : types) {
            result.addAll(allReferences(t, s -> { }));
        }
        return result;
    }

    /** Todos los tipos del proyecto que aparecen en el nodo (tipos, "new", casts, genéricos, etc.). */
    public Set<TypeInfo> allReferences(Node node, Consumer<String> ambiguityReporter) {
        Set<TypeInfo> result = new LinkedHashSet<>();
        List<ClassOrInterfaceType> types = new ArrayList<>();
        if (node instanceof ClassOrInterfaceType self) {
            types.add(self);
        }
        types.addAll(node.findAll(ClassOrInterfaceType.class));
        Set<ClassOrInterfaceType> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ClassOrInterfaceType ct : types) {
            if (!seen.add(ct) || isScopeOfAnotherType(ct)) {
                continue;
            }
            ResolvedType r = resolver.resolve(ct);
            if (r.kind() == ResolvedType.Kind.PROJECT) {
                result.add(r.projectType());
            } else if (r.kind() == ResolvedType.Kind.AMBIGUOUS) {
                ambiguityReporter.accept("el tipo '" + r.name() + "' es ambiguo, candidatos: "
                        + String.join(", ", r.candidates()));
            }
        }
        return result;
    }

    /** En "Map.Entry", el nodo "Map" es sólo el calificador de "Entry" y no un uso independiente. */
    private static boolean isScopeOfAnotherType(ClassOrInterfaceType ct) {
        return ct.getParentNode()
                .filter(p -> p instanceof ClassOrInterfaceType parent
                        && parent.getScope().map(s -> s == ct).orElse(false))
                .isPresent();
    }
}
