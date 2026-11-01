package com.umlgen;

import com.umlgen.config.DiagramOptions;
import com.umlgen.model.RelationKind;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlRelation;
import com.umlgen.project.ProjectStructure;
import com.umlgen.service.DiagramGenerator;
import com.umlgen.service.GeneratedDiagram;
import com.umlgen.service.ProgressListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Utilidades compartidas por las pruebas: generan diagramas de los proyectos de ejemplo. */
final class TestSupport {
    static final Path BIBLIOTECA = locate("ejemplo/proyecto-prueba");
    static final Path ESPECIALES = locate("ejemplo/casos-especiales");
    static final Path VEHICULOS = locate("ejemplo/agenda-vehiculos");
    static final Path MODERNO = locate("ejemplo/java-moderno");

    private TestSupport() {
    }

    private static Path locate(String relative) {
        Path p = Path.of(relative);
        if (!Files.isDirectory(p)) {
            p = Path.of(System.getProperty("user.dir")).resolve(relative);
        }
        return p;
    }

    static GeneratedDiagram generate(Path project, String pkg, DiagramOptions options) throws Exception {
        DiagramGenerator g = new DiagramGenerator();
        ProjectStructure s = g.scan(project);
        return g.generate(s, pkg, options, ProgressListener.NONE);
    }

    /** Relaciones como texto compacto "TIPO Origen->Destino [rol] (mult)" para comparar fácilmente. */
    static Set<String> relations(GeneratedDiagram d) {
        Set<String> out = new TreeSet<>();
        for (UmlRelation r : d.model().relations()) {
            out.add(r.kind() + " " + r.source().displayName() + "->" + r.target().displayName()
                    + (r.roleLabel() == null ? "" : " [" + r.roleLabel() + "]")
                    + (r.multiplicity() == null ? "" : " (" + r.multiplicity() + ")"));
        }
        return out;
    }

    static UmlClassifier classifier(GeneratedDiagram d, String displayName) {
        return d.model().classifiers().stream().filter(c -> c.displayName().equals(displayName)).findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la clase " + displayName));
    }

    static long count(GeneratedDiagram d, RelationKind k) {
        return d.relationCount(k);
    }
}
