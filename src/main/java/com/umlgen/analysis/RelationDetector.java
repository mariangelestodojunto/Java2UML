package com.umlgen.analysis;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.umlgen.config.DiagramOptions;
import com.umlgen.model.AnalysisReport;
import com.umlgen.model.RelationKind;
import com.umlgen.model.UmlAttribute;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlRelation;
import com.umlgen.model.Visibility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Detecta las relaciones UML que parten de un clasificador:
 * herencia (extends), implementación (implements), asociación (atributos) y dependencia (uso en operaciones).
 *
 * <p>Reglas: el atributo cuyo tipo es otra clase o enum del diagrama se dibuja
 * como asociación navegable ("lt=&lt;-") y se quita de la caja; la multiplicidad es "1" si se asigna en
 * algún constructor (o es final o se inicializa al declararla), "0..1" si no, y "*" para colecciones y arrays; nunca se duplican relaciones; si hay asociación/herencia entre
 * dos clases no se agrega además una dependencia; no se infieren composición ni agregación. Lo que no puede
 * determinarse con seguridad se informa como ambiguo.</p>
 */
public final class RelationDetector {
    private final TypeReferenceResolver resolver;
    private final TypeUsageAnalyzer usage;
    private final DiagramOptions options;
    private final AnalysisReport report;
    /** Devuelve el clasificador del diagrama para un tipo del proyecto, o null si no se dibuja. */
    private final BiFunction<TypeInfo, String, UmlClassifier> targetProvider;

    private final Map<String, UmlRelation> relations = new LinkedHashMap<>();

    public RelationDetector(TypeReferenceResolver resolver, DiagramOptions options, AnalysisReport report,
                            BiFunction<TypeInfo, String, UmlClassifier> targetProvider) {
        this.resolver = resolver;
        this.usage = new TypeUsageAnalyzer(resolver);
        this.options = options;
        this.report = report;
        this.targetProvider = targetProvider;
    }

    public List<UmlRelation> relations() {
        absorbDependencies();
        List<UmlRelation> sorted = new ArrayList<>(relations.values());
        sorted.sort(java.util.Comparator.comparing(r -> r.kind().ordinal()));
        return sorted;
    }

    public void detect(TypeInfo info, UmlClassifier source) {
        detectSupertypes(info, source);
        detectAssociations(info, source);
        if (options.showDependencies) {
            detectDependencies(info, source);
        }
    }

    // ---------------------------------------------------------------- herencia e implementación

    private void detectSupertypes(TypeInfo info, UmlClassifier source) {
        TypeDeclaration<?> td = info.declaration();
        if (td instanceof ClassOrInterfaceDeclaration cid) {
            for (ClassOrInterfaceType t : cid.getExtendedTypes()) {
                addSupertype(source, t, RelationKind.GENERALIZATION);
            }
        }
        // clases, enums y records pueden implementar interfaces
        if (td instanceof NodeWithImplements<?> withImplements) {
            for (ClassOrInterfaceType t : withImplements.getImplementedTypes()) {
                addSupertype(source, t, RelationKind.REALIZATION);
            }
        }
    }

    private void addSupertype(UmlClassifier source, ClassOrInterfaceType type, RelationKind kind) {
        ResolvedType r = resolver.resolve(type);
        String what = kind == RelationKind.GENERALIZATION ? "extiende a" : "implementa";
        switch (r.kind()) {
            case PROJECT -> {
                UmlClassifier target = targetProvider.apply(r.projectType(),
                        source.displayName() + " " + what + " " + r.projectType().displayName());
                if (target != null && target != source) {
                    add(new UmlRelation(kind, source, target, null, null, "declaración de " + source.displayName()));
                }
            }
            case AMBIGUOUS -> report.addAmbiguous(source.displayName() + " " + what + " '" + r.name()
                    + "', que es ambiguo (" + String.join(", ", r.candidates()) + "): no se dibuja");
            default -> report.addOmitted(source.displayName() + " " + what + " " + type.asString()
                    + " (tipo externo al proyecto, no se dibuja)");
        }
    }

    // ---------------------------------------------------------------- asociaciones

    private void detectAssociations(TypeInfo info, UmlClassifier source) {
        for (FieldSpec field : fieldsOf(info.declaration())) {
            String where = source.displayName() + "." + field.name;
            List<TypeUse> uses = usage.structuralUses(field.type,
                    msg -> report.addAmbiguous(where + ": " + msg));
            for (TypeUse use : uses) {
                UmlClassifier target = targetProvider.apply(use.target(),
                        "atributo " + where + " de tipo " + field.type.asString());
                if (target == null) {
                    continue;
                }
                String role = field.visibility.symbol() + " " + field.name; // p. ej. "- vehiculo"
                String multiplicity = use.multiplicity() != null ? use.multiplicity()
                        : field.required ? "1" : "0..1";
                add(new UmlRelation(RelationKind.ASSOCIATION, source, target, role, multiplicity,
                        "atributo " + where));
                // regla de oro: el atributo representado por la flecha no se repite dentro de la caja
                markAttribute(source, field.name);
            }
        }
    }

    private static void markAttribute(UmlClassifier c, String name) {
        for (UmlAttribute a : c.attributes()) {
            if (a.name().equals(name)) {
                a.setShownAsAssociation(true);
            }
        }
    }

    // ---------------------------------------------------------------- dependencias

    private void detectDependencies(TypeInfo info, UmlClassifier source) {
        TypeDeclaration<?> td = info.declaration();
        Map<TypeInfo, String> used = new LinkedHashMap<>();
        Set<String> fieldNames = new HashSet<>();
        for (FieldSpec f : fieldsOf(td)) {
            fieldNames.add(f.name);
        }
        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof TypeDeclaration<?>) {
                continue; // los tipos anidados se analizan por separado
            }
            String where;
            if (member instanceof CallableDeclaration<?> cd) {
                where = (member instanceof MethodDeclaration ? "método " : "constructor ") + cd.getNameAsString() + "()";
            } else if (member instanceof FieldDeclaration fd) {
                where = "inicialización de " + fd.getVariable(0).getNameAsString();
            } else if (member instanceof InitializerDeclaration) {
                where = "bloque de inicialización";
            } else {
                continue;
            }
            List<Node> scanned = new ArrayList<>();
            if (member instanceof FieldDeclaration fd) {
                for (VariableDeclarator v : fd.getVariables()) {
                    v.getInitializer().ifPresent(scanned::add);
                }
            } else {
                scanned.add(member);
            }
            for (Node n : scanned) {
                for (TypeInfo t : usage.allReferences(n, msg -> report.addAmbiguous(
                        source.displayName() + " (" + where + "): " + msg))) {
                    used.putIfAbsent(t, where);
                }
                for (TypeInfo t : staticReferences(n, fieldNames)) {
                    used.putIfAbsent(t, where);
                }
            }
        }
        for (Map.Entry<TypeInfo, String> e : used.entrySet()) {
            TypeInfo t = e.getKey();
            if (t.declaration() == td) {
                continue;
            }
            UmlClassifier target = targetProvider.apply(t,
                    source.displayName() + " usa " + t.displayName() + " en " + e.getValue());
            if (target == null || target == source || hasStructuralRelation(source, target)) {
                continue;
            }
            add(new UmlRelation(RelationKind.DEPENDENCY, source, target, null, null,
                    "uso en " + e.getValue() + " de " + source.displayName()));
        }
    }

    /**
     * Referencias a tipos usados por su nombre en expresiones (llamadas estáticas como Util.calcular(),
     * constantes como Tipo.VALOR o referencias a métodos Clase::metodo), descartando variables.
     */
    private Set<TypeInfo> staticReferences(Node node, Set<String> fieldNames) {
        Set<String> variables = new HashSet<>(fieldNames);
        for (VariableDeclarator v : node.findAll(VariableDeclarator.class)) {
            variables.add(v.getNameAsString());
        }
        for (Parameter p : node.findAll(Parameter.class)) {
            variables.add(p.getNameAsString());
        }
        for (LambdaExpr l : node.findAll(LambdaExpr.class)) {
            for (Parameter p : l.getParameters()) {
                variables.add(p.getNameAsString());
            }
        }
        Set<TypeInfo> result = new HashSet<>();
        for (NameExpr ne : node.findAll(NameExpr.class)) {
            String name = ne.getNameAsString();
            if (variables.contains(name) || !Character.isUpperCase(name.charAt(0))) {
                continue;
            }
            boolean usedAsScope = ne.getParentNode().map(p ->
                    (p instanceof MethodCallExpr mc && mc.getScope().map(s -> s == ne).orElse(false))
                            || (p instanceof FieldAccessExpr fa && fa.getScope() == ne)
                            || (p instanceof MethodReferenceExpr mr && mr.getScope() == ne)).orElse(false);
            if (usedAsScope) {
                ResolvedType r = resolver.resolveName(name, ne);
                if (r.kind() == ResolvedType.Kind.PROJECT) {
                    result.add(r.projectType());
                }
            }
        }
        return result;
    }

    private boolean hasStructuralRelation(UmlClassifier source, UmlClassifier target) {
        for (UmlRelation r : relations.values()) {
            if (r.kind() != RelationKind.DEPENDENCY && r.source() == source && r.target() == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * Absorción de dependencias, aplicada cuando ya se conocen todas las relaciones:
     * <ol>
     *   <li>Si entre A y B hay una asociación, herencia o implementación (en cualquier sentido), no se dibuja
     *       además una dependencia entre ellas: la relación estructural prevalece.</li>
     *   <li>Opcional ({@code omitNavigableDependencies}): si A llega a B navegando sus asociaciones
     *       (A → X → … → B), la dependencia A → B también se omite, porque A ya conoce a B a través de X.
     *       Ej.: Agenda recibe una Fecha sólo para pasársela a Persona (Agenda → Persona → Fecha).</li>
     * </ol>
     */
    private void absorbDependencies() {
        Set<String> structuralPairs = new HashSet<>();
        Map<UmlClassifier, Set<UmlClassifier>> navigation = new java.util.IdentityHashMap<>();
        for (UmlRelation r : relations.values()) {
            if (r.kind() != RelationKind.DEPENDENCY) {
                structuralPairs.add(pairKey(r.source(), r.target()));
                structuralPairs.add(pairKey(r.target(), r.source()));
            }
            if (r.kind() == RelationKind.ASSOCIATION) {
                navigation.computeIfAbsent(r.source(), k -> new HashSet<>()).add(r.target());
            }
        }
        java.util.Iterator<UmlRelation> it = relations.values().iterator();
        while (it.hasNext()) {
            UmlRelation r = it.next();
            if (r.kind() != RelationKind.DEPENDENCY) {
                continue;
            }
            if (structuralPairs.contains(pairKey(r.source(), r.target()))) {
                it.remove();
            } else if (options.omitNavigableDependencies && reachable(navigation, r.source(), r.target())) {
                report.addOmitted("Dependencia " + r.source().displayName() + " → " + r.target().displayName()
                        + " absorbida: " + r.source().displayName() + " ya llega a " + r.target().displayName()
                        + " por sus asociaciones");
                it.remove();
            }
        }
    }

    private static String pairKey(UmlClassifier a, UmlClassifier b) {
        return a.qualifiedName() + ">" + b.qualifiedName();
    }

    private static boolean reachable(Map<UmlClassifier, Set<UmlClassifier>> nav, UmlClassifier from, UmlClassifier to) {
        java.util.Deque<UmlClassifier> pending = new java.util.ArrayDeque<>(nav.getOrDefault(from, Set.of()));
        Set<UmlClassifier> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            UmlClassifier c = pending.pop();
            if (c == to) {
                return true;
            }
            if (seen.add(c)) {
                pending.addAll(nav.getOrDefault(c, Set.of()));
            }
        }
        return false;
    }

    private boolean add(UmlRelation r) {
        return relations.putIfAbsent(r.identityKey(), r) == null;
    }

    // ---------------------------------------------------------------- atributos (incluye records)

    /** @param required la referencia nunca queda en null (ver {@link #isRequired}): multiplicidad 1; si no, 0..1. */
    private record FieldSpec(String name, Type type, Visibility visibility, boolean required) {
    }

    /**
     * Una referencia simple es
     * obligatoria (multiplicidad 1) cuando se fija al construir el objeto y después no cambia:
     * <ul>
     *   <li>es final, o</li>
     *   <li>se inicializa con un valor al declararla o se le asigna un valor distinto de null en algún
     *       constructor (aunque venga por parámetro), <b>y</b> ningún método la vuelve a asignar.</li>
     * </ul>
     * Si no se asigna en ningún constructor, o si un método la modifica (un setter, agregarX/quitarX...),
     * es opcional: 0..1. Ejemplos: fechaFabricacion → 1; fechaNacimiento (tiene
     * setFechaNacimiento), fechaUltimaVTV y vehiculo → 0..1.
     */
    static boolean isRequired(TypeDeclaration<?> td, FieldDeclaration fd, VariableDeclarator var) {
        if (fd.isFinal()) {
            return true;
        }
        String name = var.getNameAsString();
        boolean setAtConstruction = var.getInitializer().map(RelationDetector::assignsValue).orElse(false);
        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof ConstructorDeclaration || member instanceof InitializerDeclaration) {
                if (assignments(member, name).stream().anyMatch(a -> assignsValue(a.getValue()))) {
                    setAtConstruction = true;
                }
            } else if (member instanceof MethodDeclaration && !assignments(member, name).isEmpty()) {
                return false; // un método la modifica: puede cambiar o quedar en null
            }
        }
        return setAtConstruction;
    }

    /**
     * Asignaciones al atributo dentro de un constructor, método o bloque. Un nombre sin "this." sólo cuenta
     * si no hay un parámetro o variable local con el mismo nombre que lo oculte.
     */
    private static List<AssignExpr> assignments(Node member, String field) {
        Set<String> shadowing = new HashSet<>();
        member.findAll(Parameter.class).forEach(p -> shadowing.add(p.getNameAsString()));
        member.findAll(VariableDeclarator.class).forEach(v -> shadowing.add(v.getNameAsString()));
        boolean shadowed = shadowing.contains(field);
        List<AssignExpr> result = new ArrayList<>();
        for (AssignExpr a : member.findAll(AssignExpr.class)) {
            Expression t = a.getTarget();
            boolean viaThis = t instanceof FieldAccessExpr f && f.getNameAsString().equals(field) && f.getScope() instanceof ThisExpr;
            boolean viaName = t instanceof NameExpr n && n.getNameAsString().equals(field) && !shadowed;
            if (viaThis || viaName) {
                result.add(a);
            }
        }
        return result;
    }

    private static boolean assignsValue(Expression value) {
        return !(value instanceof NullLiteralExpr);
    }

    @SuppressWarnings("unchecked")
    private static List<FieldSpec> fieldsOf(TypeDeclaration<?> td) {
        List<FieldSpec> result = new ArrayList<>();
        boolean inInterface = td instanceof ClassOrInterfaceDeclaration cid && cid.isInterface();
        if (td.getClass().getSimpleName().equals("RecordDeclaration")) {
            try {
                for (Parameter p : (NodeList<Parameter>) td.getClass().getMethod("getParameters").invoke(td)) {
                    result.add(new FieldSpec(p.getNameAsString(), p.getType(), Visibility.PRIVATE, false));
                }
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // sin soporte de records
            }
        }
        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof FieldDeclaration fd) {
                Visibility v = ClassifierFactory.visibility(fd.getModifiers(), inInterface, false);
                for (VariableDeclarator var : fd.getVariables()) {
                    result.add(new FieldSpec(var.getNameAsString(), var.getType(), v, isRequired(td, fd, var)));
                }
            }
        }
        return result;
    }
}
