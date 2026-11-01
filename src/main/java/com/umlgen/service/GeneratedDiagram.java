package com.umlgen.service;

import com.umlgen.config.DiagramOptions;
import com.umlgen.layout.DiagramLayout;
import com.umlgen.model.AnalysisReport;
import com.umlgen.model.ClassifierKind;
import com.umlgen.model.RelationKind;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlModel;
import com.umlgen.model.UmlRelation;
import com.umlgen.project.ProjectStructure;

/** Diagrama listo para exportar: modelo, distribución y reporte del análisis. */
public record GeneratedDiagram(String packageName, DiagramOptions options, UmlModel model, DiagramLayout layout,
                               AnalysisReport report) {

    public long relationCount(RelationKind kind) {
        return model.relations().stream().filter(r -> r.kind() == kind).count();
    }

    /** Resumen legible del análisis, en español. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Paquete: ").append(ProjectStructure.displayName(packageName))
                .append(options.includeSubpackages ? " (con subpaquetes)" : "").append('\n');
        sb.append("Archivos analizados: ").append(report.filesAnalyzed()).append("\n\n");
        long classes = model.count(ClassifierKind.CLASS) + model.count(ClassifierKind.ABSTRACT_CLASS);
        sb.append("Clases procesadas: ").append(classes)
                .append(" (abstractas: ").append(model.count(ClassifierKind.ABSTRACT_CLASS)).append(")\n");
        sb.append("Interfaces encontradas: ").append(model.count(ClassifierKind.INTERFACE)).append('\n');
        sb.append("Enumeraciones encontradas: ").append(model.count(ClassifierKind.ENUM)).append('\n');
        long ext = model.classifiers().stream().filter(UmlClassifier::isExternal).count();
        if (ext > 0) {
            sb.append("Clases externas incluidas: ").append(ext).append('\n');
        }
        sb.append("\nRelaciones identificadas: ").append(model.relations().size()).append('\n');
        for (RelationKind k : RelationKind.values()) {
            sb.append("  - ").append(capitalize(k.label())).append(": ").append(relationCount(k)).append('\n');
        }
        if (!model.relations().isEmpty()) {
            sb.append('\n');
            for (UmlRelation r : model.relations()) {
                sb.append("  • ").append(r).append('\n');
            }
            if (model.relations().stream().anyMatch(r -> "*".equals(r.multiplicity()))) {
                sb.append("  (multiplicidad \"*\" inferida de colecciones/arrays)\n");
            }
        }
        section(sb, "Errores de análisis", report.parseErrors());
        section(sb, "Relaciones ambiguas (no dibujadas)", report.ambiguous());
        section(sb, "Elementos omitidos", report.omitted());
        section(sb, "Advertencias", report.warnings());
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, java.util.List<String> items) {
        sb.append('\n').append(title).append(": ").append(items.size()).append('\n');
        for (String s : items) {
            sb.append("  • ").append(s).append('\n');
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
