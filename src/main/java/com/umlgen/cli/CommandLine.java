package com.umlgen.cli;

import com.umlgen.config.DiagramOptions;
import com.umlgen.project.ProjectStructure;
import com.umlgen.service.DiagramGenerator;
import com.umlgen.service.GeneratedDiagram;
import com.umlgen.service.GenerationException;
import com.umlgen.uxf.ValidationResult;

import java.nio.file.Path;

/**
 * Modo línea de comandos (útil para automatizar o probar):
 * <pre>
 * java -jar java2uml.jar --cli &lt;carpetaProyecto&gt; &lt;paquete&gt; &lt;salida.uxf&gt; [opciones]
 *   --subpaquetes --metodos --constructores --parametros --externas --sin-dependencias
 *   --sin-interfaces --sin-enums --sin-atributos --incluir-tests --incluir-ejecucion --todas-las-dependencias
 *   --capas --constructores-servicio --accesores
 * java -jar java2uml.jar --cli &lt;carpetaProyecto&gt; --listar     (muestra los paquetes)
 * </pre>
 * Use "." como paquete para el paquete por defecto.
 */
public final class CommandLine {
    private CommandLine() {
    }

    public static int run(String[] args) {
        if (args.length < 3 || (args.length < 4 && !"--listar".equals(args[2]))) {
            System.err.println("Uso: --cli <carpetaProyecto> <paquete> <salida.uxf> [opciones]  |  --cli <carpetaProyecto> --listar");
            return 2;
        }
        DiagramGenerator generator = new DiagramGenerator();
        try {
            ProjectStructure project = generator.scan(Path.of(args[1]));
            if ("--listar".equals(args[2])) {
                for (String p : project.packages()) {
                    System.out.println(ProjectStructure.displayName(p) + " (" + project.fileCount(p) + " archivos)");
                }
                return 0;
            }
            String pkg = ".".equals(args[2]) ? "" : args[2];
            DiagramOptions o = new DiagramOptions();
            for (int i = 4; i < args.length; i++) {
                switch (args[i]) {
                    case "--subpaquetes" -> o.includeSubpackages = true;
                    case "--metodos" -> o.showMethods = true;
                    case "--constructores" -> o.showConstructors = true;
                    case "--parametros" -> o.showParameterNames = true;
                    case "--externas" -> o.includeExternalClasses = true;
                    case "--sin-dependencias" -> o.showDependencies = false;
                    case "--sin-interfaces" -> o.includeInterfaces = false;
                    case "--sin-enums" -> o.includeEnums = false;
                    case "--sin-atributos" -> o.showAttributes = false;
                    case "--incluir-tests" -> o.excludeTestSources = false;
                    case "--incluir-ejecucion" -> o.excludeRunnerClasses = false;
                    case "--todas-las-dependencias" -> o.omitNavigableDependencies = false;
                    case "--capas" -> o.layeredLayout = true;
                    case "--accesores" -> o.hideSimpleAccessors = false;
                    case "--constructores-servicio" -> o.hideServiceConstructors = false;
                    default -> System.err.println("Opción desconocida ignorada: " + args[i]);
                }
            }
            GeneratedDiagram d = generator.generate(project, pkg, o,
                    (p, m) -> System.err.println("[" + p + "%] " + m));
            System.out.println(d.summary());
            ValidationResult v = generator.export(d, Path.of(args[3]));
            System.out.println("Archivo generado: " + Path.of(args[3]).toAbsolutePath());
            System.out.println("Validación: " + (v.isValid() ? "OK" : "CON ERRORES")
                    + " (" + v.classCount() + " cajas, " + v.relationCount() + " relaciones)");
            v.errors().forEach(e -> System.out.println("  ERROR: " + e));
            v.warnings().forEach(w -> System.out.println("  Aviso: " + w));
            return v.isValid() ? 0 : 1;
        } catch (GenerationException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
