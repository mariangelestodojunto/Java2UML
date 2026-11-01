package com.umlgen.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resuelve referencias a tipos aplicando las reglas de visibilidad de nombres de Java (JLS §6.4):
 * parámetros de tipo &gt; tipos anidados &gt; imports simples &gt; mismo paquete &gt; imports "*" &gt; java.lang.
 *
 * <p>Se trabaja sólo con el código fuente (sin classpath), por lo que funciona aun con proyectos que
 * no compilan o cuyas bibliotecas no están disponibles. Nunca se relacionan dos clases sólo por tener
 * el mismo nombre simple: si la resolución no es segura el resultado es AMBIGUOUS o UNRESOLVED.</p>
 */
public final class TypeReferenceResolver {
    private final TypeIndex index;

    public TypeReferenceResolver(TypeIndex index) {
        this.index = index;
    }

    /** Nombre del tipo con su calificación pero sin argumentos genéricos, p. ej. "Map.Entry". */
    public static String qualifiedText(ClassOrInterfaceType t) {
        StringBuilder sb = new StringBuilder(t.getNameAsString());
        Optional<ClassOrInterfaceType> scope = t.getScope();
        while (scope.isPresent()) {
            sb.insert(0, scope.get().getNameAsString() + ".");
            scope = scope.get().getScope();
        }
        return sb.toString();
    }

    public ResolvedType resolve(ClassOrInterfaceType type) {
        return resolveName(qualifiedText(type), type);
    }

    /** Resuelve un nombre (simple o calificado) en el contexto del nodo dado. */
    public ResolvedType resolveName(String name, Node context) {
        if (!name.contains(".")) {
            return resolveSimple(name, context);
        }
        TypeInfo direct = index.get(name);
        if (direct != null) {
            return ResolvedType.project(direct);
        }
        String[] parts = name.split("\\.");
        String first = parts[0];
        String rest = name.substring(first.length() + 1);
        if (Character.isUpperCase(first.charAt(0))) {
            ResolvedType head = resolveSimple(first, context);
            if (head.kind() == ResolvedType.Kind.PROJECT) {
                TypeInfo nested = index.get(head.projectType().qualifiedName() + "." + rest);
                return nested != null ? ResolvedType.project(nested) : ResolvedType.unresolved(name);
            }
            if (head.kind() == ResolvedType.Kind.EXTERNAL) {
                return ResolvedType.external(head.name() + "." + rest);
            }
            return head.kind() == ResolvedType.Kind.AMBIGUOUS ? head : ResolvedType.unresolved(name);
        }
        // nombre calificado con paquete: puede ser un tipo anidado de un tipo del proyecto
        for (int i = parts.length - 1; i > 0; i--) {
            String prefix = String.join(".", java.util.Arrays.copyOfRange(parts, 0, i));
            if (index.get(prefix) != null) {
                return ResolvedType.unresolved(name);
            }
        }
        return ResolvedType.external(name);
    }

    private ResolvedType resolveSimple(String n, Node context) {
        // 1. parámetros de tipo visibles (de métodos y de clases contenedoras)
        if (typeVariablesInScope(context).contains(n)) {
            return ResolvedType.typeVariable(n);
        }
        // 2. la propia clase, sus tipos anidados y los de las clases que la contienen
        for (TypeDeclaration<?> enclosing : enclosingTypes(context)) {
            TypeInfo enc = index.of(enclosing);
            if (enc == null) {
                continue;
            }
            if (enc.simpleName().equals(n)) {
                return ResolvedType.project(enc);
            }
            TypeInfo member = index.get(enc.qualifiedName() + "." + n);
            if (member != null) {
                return ResolvedType.project(member);
            }
        }
        CompilationUnit cu = context.findCompilationUnit().orElse(null);
        String pkg = cu == null ? "" : cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        List<ImportDeclaration> imports = cu == null ? List.of() : cu.getImports();

        // 3. imports simples (import a.b.C;)
        for (ImportDeclaration imp : imports) {
            if (!imp.isStatic() && !imp.isAsterisk()) {
                String fqn = imp.getNameAsString();
                if (fqn.equals(n) || fqn.endsWith("." + n)) {
                    TypeInfo t = index.get(fqn);
                    return t != null ? ResolvedType.project(t) : ResolvedType.external(fqn);
                }
            }
        }
        // 4. mismo paquete
        TypeInfo samePackage = index.get(pkg.isEmpty() ? n : pkg + "." + n);
        if (samePackage != null) {
            return ResolvedType.project(samePackage);
        }
        // 5. imports a demanda (import a.b.*;)
        List<String> candidates = new ArrayList<>();
        String standardCandidate = null;
        boolean unknownWildcard = false;
        for (ImportDeclaration imp : imports) {
            if (!imp.isStatic() && imp.isAsterisk()) {
                String base = imp.getNameAsString();
                TypeInfo t = index.get(base + "." + n);
                if (t != null) {
                    candidates.add(t.qualifiedName());
                } else if (JavaStandardTypes.isKnownInPackage(base, n)) {
                    standardCandidate = base + "." + n;
                } else if (!isProjectPackage(base)) {
                    unknownWildcard = true;
                }
            }
        }
        if (!candidates.isEmpty()) {
            if (candidates.size() == 1 && standardCandidate == null) {
                return ResolvedType.project(index.get(candidates.get(0)));
            }
            List<String> all = new ArrayList<>(candidates);
            if (standardCandidate != null) {
                all.add(standardCandidate);
            }
            return ResolvedType.ambiguous(n, all);
        }
        if (standardCandidate != null) {
            return ResolvedType.external(standardCandidate);
        }
        // 6. java.lang y nombres estándar muy conocidos
        if (JavaStandardTypes.isJavaLang(n)) {
            return ResolvedType.external("java.lang." + n);
        }
        if (JavaStandardTypes.isKnownStandardName(n)) {
            return ResolvedType.external(n);
        }
        return unknownWildcard ? ResolvedType.external(n) : ResolvedType.unresolved(n);
    }

    private boolean isProjectPackage(String name) {
        for (TypeInfo t : index.all()) {
            if (t.packageName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Tipos que contienen al nodo, del más interno al más externo. */
    public static List<TypeDeclaration<?>> enclosingTypes(Node node) {
        List<TypeDeclaration<?>> result = new ArrayList<>();
        Node current = node;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> td) {
                result.add(td);
            }
            current = current.getParentNode().orElse(null);
        }
        return result;
    }

    private static Set<String> typeVariablesInScope(Node node) {
        Set<String> names = new HashSet<>();
        Node current = node;
        while (current != null) {
            if (current instanceof CallableDeclaration<?> cd) {
                for (TypeParameter tp : cd.getTypeParameters()) {
                    names.add(tp.getNameAsString());
                }
            } else if (current instanceof ClassOrInterfaceDeclaration cid) {
                for (TypeParameter tp : cid.getTypeParameters()) {
                    names.add(tp.getNameAsString());
                }
                if (cid.isStatic() || cid.isInterface()) {
                    // un tipo anidado estático no ve los parámetros de tipo de su contenedor
                    break;
                }
            }
            current = current.getParentNode().orElse(null);
        }
        return names;
    }
}
