package com.umlgen.analysis;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.umlgen.config.DiagramOptions;
import com.umlgen.model.AnalysisReport;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlModel;
import com.umlgen.model.UmlRelation;
import com.umlgen.model.RelationKind;
import com.umlgen.parser.ParsedSource;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construye el modelo UML del paquete seleccionado.
 * Se indexan TODOS los tipos del proyecto (para resolver referencias correctamente), pero sólo se dibujan
 * los del paquete elegido; las clases de otros paquetes se agregan únicamente si la opción lo permite.
 */
public final class ModelBuilder {
    private final DiagramOptions options;

    public ModelBuilder(DiagramOptions options) {
        this.options = options;
    }

    /**
     * @param allSources     todas las unidades analizadas del proyecto
     * @param selectedFiles  archivos que pertenecen al paquete (y subpaquetes) seleccionado
     */
    public AnalysisResult build(List<ParsedSource> allSources, Set<Path> selectedFiles, AnalysisReport report) {
        TypeIndex index = new TypeIndex(allSources, report);
        TypeReferenceResolver resolver = new TypeReferenceResolver(index);
        ClassifierFactory factory = new ClassifierFactory();
        UmlModel model = new UmlModel();

        Map<TypeInfo, UmlClassifier> included = new LinkedHashMap<>();
        Set<TypeInfo> inScopeButExcluded = new HashSet<>();
        for (TypeInfo t : index.all()) {
            if (!selectedFiles.contains(t.source().file().path())) {
                continue;
            }
            if (options.excludeRunnerClasses && isRunnerClass(t)) {
                report.addOmitted(t.displayName() + " (clase de ejecución: no es una entidad del dominio)");
                continue;
            }
            if (isExcludedByOptions(t)) {
                inScopeButExcluded.add(t);
                report.addOmitted(t.displayName() + " (" + kindLabel(t) + " excluida por las opciones del diagrama)");
                continue;
            }
            UmlClassifier c = factory.create(t);
            included.put(t, c);
            model.addClassifier(c);
        }

        Map<TypeInfo, UmlClassifier> externals = new LinkedHashMap<>();
        RelationDetector detector = new RelationDetector(resolver, options, report, (target, context) -> {
            UmlClassifier c = included.get(target);
            if (c != null) {
                return c;
            }
            if (inScopeButExcluded.contains(target) || isExcludedByOptions(target)) {
                report.addOmitted("Relación no dibujada: " + context + " (" + kindLabel(target)
                        + " excluida por las opciones)");
                return null;
            }
            if (!options.includeExternalClasses) {
                report.addOmitted("Relación no dibujada: " + context + " — " + target.qualifiedName()
                        + " está fuera del paquete seleccionado (activá \"Mostrar clases externas\")");
                return null;
            }
            return externals.computeIfAbsent(target, t -> {
                UmlClassifier ext = factory.create(t);
                ext.setExternal(true);
                return ext;
            });
        });

        for (Map.Entry<TypeInfo, UmlClassifier> e : included.entrySet()) {
            detector.detect(e.getKey(), e.getValue());
        }
        externals.values().forEach(model::addClassifier);
        model.relations().addAll(detector.relations());
        markServiceClasses(model);
        return new AnalysisResult(model, report);
    }

    /**
     * Clase de servicio (p. ej. PlantaVTV): opera sobre otras clases que recibe por parámetro, pero no
     * guarda referencias a ellas ni las demás clases la referencian. Es decir: tiene al menos una dependencia
     * saliente hacia una clase que no es enum y ninguna otra relación (ni asociaciones, ni herencia, ni
     * relaciones entrantes).
     */
    static void markServiceClasses(UmlModel model) {
        for (UmlClassifier c : model.classifiers()) {
            boolean usesOthers = false;
            boolean other = false;
            for (UmlRelation r : model.relations()) {
                if (r.source() == c && r.target() == c) {
                    other = true;
                } else if (r.source() == c) {
                    if (r.kind() == RelationKind.DEPENDENCY) {
                        // usar sólo enums (p. ej. devolver un ResultadoCombate) no la convierte en servicio
                        if (!r.target().isEnum()) {
                            usesOthers = true;
                        }
                    } else {
                        other = true;
                    }
                } else if (r.target() == c) {
                    other = true;
                }
            }
            c.setService(usesOthers && !other && c.kind() == com.umlgen.model.ClassifierKind.CLASS);
        }
    }

    /** Nombres típicos de clases que sólo ejecutan el programa o las pruebas del TP. */
    static final Set<String> RUNNER_NAMES = Set.of("Test", "Main", "App");

    /**
     * Clase de ejecución: se llama Test, Main o App, o su único contenido es el método main(...)
     * (ningún atributo, constructor ni otro método).
     */
    static boolean isRunnerClass(TypeInfo t) {
        if (!(t.declaration() instanceof ClassOrInterfaceDeclaration cid) || cid.isInterface()) {
            return false;
        }
        if (RUNNER_NAMES.contains(cid.getNameAsString())) {
            return true;
        }
        boolean hasMain = false;
        for (BodyDeclaration<?> member : cid.getMembers()) {
            if (member instanceof MethodDeclaration m && m.getNameAsString().equals("main") && m.isStatic()) {
                hasMain = true;
            } else if (!(member instanceof TypeDeclaration<?>)) {
                return false;
            }
        }
        return hasMain;
    }

    private boolean isExcludedByOptions(TypeInfo t) {
        if (!options.includeInterfaces && t.declaration() instanceof ClassOrInterfaceDeclaration cid && cid.isInterface()) {
            return true;
        }
        return !options.includeEnums && t.declaration() instanceof EnumDeclaration;
    }

    private static String kindLabel(TypeInfo t) {
        if (t.declaration() instanceof EnumDeclaration) {
            return "enumeración";
        }
        if (t.declaration() instanceof ClassOrInterfaceDeclaration cid && cid.isInterface()) {
            return "interfaz";
        }
        return "clase";
    }
}
