package com.umlgen.service;

import com.umlgen.analysis.AnalysisResult;
import com.umlgen.analysis.ModelBuilder;
import com.umlgen.config.DiagramOptions;
import com.umlgen.layout.BoxSizer;
import com.umlgen.layout.DiagramLayout;
import com.umlgen.layout.GridLayoutEngine;
import com.umlgen.layout.LabelPlacer;
import com.umlgen.layout.LayoutEngine;
import com.umlgen.model.AnalysisReport;
import com.umlgen.model.UmlClassifier;
import com.umlgen.parser.JavaSourceParser;
import com.umlgen.parser.ParsedSource;
import com.umlgen.project.JavaSourceFile;
import com.umlgen.project.ProjectScanner;
import com.umlgen.project.ProjectStructure;
import com.umlgen.uxf.PanelTextFormatter;
import com.umlgen.uxf.UxfValidator;
import com.umlgen.uxf.UxfWriter;
import com.umlgen.uxf.ValidationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Orquesta el proceso completo: exploración → análisis → modelo UML → distribución → .uxf → validación. */
public final class DiagramGenerator {

    public ProjectStructure scan(Path projectRoot) throws GenerationException {
        try {
            ProjectStructure s = new ProjectScanner().scan(projectRoot);
            if (s.isEmpty()) {
                throw new GenerationException("La carpeta seleccionada no contiene archivos .java.\n"
                        + "Elegí la carpeta raíz de un proyecto Java (la que contiene \"src\").");
            }
            return s;
        } catch (IOException e) {
            throw new GenerationException("No se pudo explorar la carpeta: " + e.getMessage(), e);
        }
    }

    public GeneratedDiagram generate(ProjectStructure project, String packageName, DiagramOptions options,
                                     ProgressListener progress) throws GenerationException {
        DiagramOptions opts = options.copy();
        List<JavaSourceFile> selected = project.filesOf(packageName, opts.includeSubpackages, opts.excludeTestSources);
        if (selected.isEmpty()) {
            throw new GenerationException("El paquete " + ProjectStructure.displayName(packageName)
                    + " no contiene archivos .java" + (opts.includeSubpackages ? "" : " (probá incluir subpaquetes)") + ".");
        }

        AnalysisReport report = new AnalysisReport();
        project.warnings().forEach(report::addWarning);

        // Se analiza todo el proyecto para resolver referencias, pero sólo se dibuja el paquete elegido
        List<JavaSourceFile> all = new ArrayList<>();
        for (JavaSourceFile f : project.files()) {
            if (!(opts.excludeTestSources && f.testSource())) {
                all.add(f);
            }
        }
        Set<Path> selectedPaths = new HashSet<>();
        selected.forEach(f -> selectedPaths.add(f.path()));

        JavaSourceParser parser = new JavaSourceParser();
        List<ParsedSource> parsed = new ArrayList<>();
        AnalysisReport outsideReport = new AnalysisReport();
        for (int i = 0; i < all.size(); i++) {
            JavaSourceFile f = all.get(i);
            progress.progress(5 + (int) (60.0 * i / all.size()), "Analizando " + f.path().getFileName());
            boolean inSelection = selectedPaths.contains(f.path());
            ParsedSource ps = parser.parse(f, inSelection ? report : outsideReport);
            if (ps != null) {
                parsed.add(ps);
            }
        }
        if (!outsideReport.parseErrors().isEmpty()) {
            report.addWarning(outsideReport.parseErrors().size()
                    + " archivo(s) fuera del paquete tienen errores de sintaxis; sus clases no se pueden resolver");
        }
        report.setFilesAnalyzed(selected.size());

        progress.progress(70, "Identificando clases y relaciones");
        AnalysisResult analysis = new ModelBuilder(opts).build(parsed, selectedPaths, report);
        if (analysis.model().classifiers().isEmpty()) {
            String detail = report.parseErrors().isEmpty() ? "" : "\nErrores:\n  " + String.join("\n  ", report.parseErrors());
            throw new GenerationException("No se encontraron clases para dibujar en el paquete "
                    + ProjectStructure.displayName(packageName) + "." + detail);
        }

        progress.progress(85, "Distribuyendo el diagrama");
        PanelTextFormatter formatter = new PanelTextFormatter(opts);
        BoxSizer sizer = new BoxSizer();
        Map<UmlClassifier, int[]> sizes = new HashMap<>();
        for (UmlClassifier c : analysis.model().classifiers()) {
            sizes.put(c, sizer.size(formatter.lines(c)));
        }
        DiagramLayout layout = opts.layeredLayout
                ? new LayoutEngine().layout(analysis.model().classifiers(), analysis.model().relations(), sizes)
                : new GridLayoutEngine().layout(analysis.model().classifiers(), analysis.model().relations(), sizes);
        new LabelPlacer().place(layout);
        progress.progress(100, "Diagrama generado");
        return new GeneratedDiagram(packageName, opts, analysis.model(), layout, report);
    }

    /** Escribe el .uxf y lo valida releyéndolo desde disco. */
    public ValidationResult export(GeneratedDiagram diagram, Path file) throws GenerationException {
        UxfWriter writer = new UxfWriter(new PanelTextFormatter(diagram.options()));
        try {
            writer.write(diagram.layout(), file);
        } catch (IOException | RuntimeException e) {
            throw new GenerationException("No se pudo escribir el archivo " + file + ":\n" + e.getMessage(), e);
        }
        return new UxfValidator().validate(file);
    }
}
