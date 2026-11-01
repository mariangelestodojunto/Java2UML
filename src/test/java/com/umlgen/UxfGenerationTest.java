package com.umlgen;

import com.umlgen.config.DiagramOptions;
import com.umlgen.layout.Rect;
import com.umlgen.service.DiagramGenerator;
import com.umlgen.service.GeneratedDiagram;
import com.umlgen.service.GenerationException;
import com.umlgen.uxf.PanelTextFormatter;
import com.umlgen.uxf.UxfValidator;
import com.umlgen.uxf.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del generador UXF, la distribución automática y la validación. */
class UxfGenerationTest {

    @Test
    void generaUnUxfValidoSinSuperposiciones() throws Exception {
        for (boolean methods : new boolean[]{false, true}) {
            DiagramOptions o = new DiagramOptions();
            o.showMethods = methods;
            o.showConstructors = methods;
            GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", o);
            Path out = Files.createTempFile("biblioteca", ".uxf");
            ValidationResult v = new DiagramGenerator().export(d, out);
            assertTrue(v.isValid(), "errores: " + v.errors());
            assertEquals(10, v.classCount());
            assertEquals(d.model().relations().size(), v.relationCount());
            assertTrue(v.warnings().isEmpty(), "avisos de diseño: " + v.warnings());
            String xml = Files.readString(out);
            assertTrue(xml.startsWith("<diagram program=\"umletino\" version=\"15.1\"><zoom_level>10</zoom_level>"));
            Files.delete(out);
        }
    }

    @Test
    void cajasNoSeSuperponen() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.ESPECIALES, "especial", new DiagramOptions());
        List<Rect> boxes = new ArrayList<>(d.layout().boxes().values());
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(boxes.get(i).intersects(boxes.get(j)), "superposición entre " + boxes.get(i) + " y " + boxes.get(j));
            }
        }
    }

    @Test
    void formatoDeCajas() throws Exception {
        DiagramOptions o = new DiagramOptions();
        o.showMethods = true;
        o.showConstructors = true;
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", o);
        PanelTextFormatter f = new PanelTextFormatter(o);
        assertEquals("<<enum>>\nGenero\n--\n_+ NOVELA_\n_+ ENSAYO_\n_+ POESIA_\n_+ CIENCIA_FICCION_",
                f.text(TestSupport.classifier(d, "Genero")));
        String material = f.text(TestSupport.classifier(d, "Material"));
        assertTrue(material.startsWith("/Material/\n--\n# titulo: String\n- anio: int\n~ codigoInterno: String\n"
                + "- prestado: boolean\n--\n+ Material(String, int)\n+ prestar(Socio): boolean\n+ devolver(): void\n"), material);
        assertTrue(material.contains("/+ calcularMulta(int): double/"), "método abstracto en itálica");
        String prestable = f.text(TestSupport.classifier(d, "Prestable"));
        assertTrue(prestable.startsWith("<<interface>>\nPrestable\n--\n_+ DIAS_MAXIMOS: int = 14_\n--"), prestable);
        String biblioteca = f.text(TestSupport.classifier(d, "Biblioteca"));
        assertTrue(biblioteca.contains("_+ VERSION: String = \"1.0\"_"), "constante con valor literal: " + biblioteca);
        String socio = f.text(TestSupport.classifier(d, "Socio"));
        assertTrue(socio.contains("- dni: String\n"), "final sin literal: atributo normal");
        assertFalse(socio.contains("readOnly"));
        assertFalse(socio.contains("prestamos"), "colección asociada fuera de la caja");
        String libro = f.text(TestSupport.classifier(d, "Libro"));
        assertFalse(libro.contains("autor"), "regla de oro: el atributo asociado no está en la caja");
        assertFalse(libro.contains("genero"), "también para enums");
        assertTrue(libro.contains("- paginas: int"));
        String generador = f.text(TestSupport.classifier(d, "GeneradorReportes"));
        assertTrue(generador.contains("_+ imprimir(List<Material>): void_"), generador);
    }

    @Test
    void tresCompartimentosSalvoEnumsSinOperaciones() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        PanelTextFormatter f = new PanelTextFormatter(new DiagramOptions());
        for (var c : d.model().classifiers()) {
            long separators = f.lines(c).stream().filter(PanelTextFormatter.SEPARATOR::equals).count();
            assertEquals(c.isEnum() ? 1 : 2, separators, "compartimentos de " + c.displayName());
        }
    }

    @Test
    void finalDeInstanciaConLiteralNoSeDibuja() throws Exception {
        DiagramOptions o = new DiagramOptions();
        GeneratedDiagram d = TestSupport.generate(TestSupport.VEHICULOS, "vehiculos", o);
        String vehiculo = new PanelTextFormatter(o).text(TestSupport.classifier(d, "Vehiculo"));
        assertFalse(vehiculo.contains("VELOCIDAD_POR_DEFECTO"), "final no static con literal se omite: " + vehiculo);
        assertTrue(vehiculo.contains("- velocidadMaxima: int"));
        // un static final con literal sí se muestra (Biblioteca.VERSION) y un final sin literal también (Socio.dni)
        GeneratedDiagram b = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", o);
        PanelTextFormatter f = new PanelTextFormatter(o);
        assertTrue(f.text(TestSupport.classifier(b, "Biblioteca")).contains("_+ VERSION: String = \"1.0\"_"));
        assertTrue(f.text(TestSupport.classifier(b, "Socio")).contains("- dni: String"));
    }

    @Test
    void lasClasesDeServicioNoMuestranConstructores() throws Exception {
        DiagramOptions o = new DiagramOptions();
        o.showMethods = true;
        o.showConstructors = true;
        GeneratedDiagram d = TestSupport.generate(TestSupport.VEHICULOS, "vehiculos", o);
        PanelTextFormatter f = new PanelTextFormatter(o);
        String planta = f.text(TestSupport.classifier(d, "PlantaVTV"));
        assertFalse(planta.contains("+ PlantaVTV("), planta);
        assertTrue(planta.contains("+ realizarRevision(Vehiculo, Fecha): boolean"));
        assertTrue(f.text(TestSupport.classifier(d, "Agenda")).contains("+ Agenda()"), "Agenda no es servicio");
        assertTrue(f.text(TestSupport.classifier(d, "Fecha")).contains("+ Fecha(int, int, int)"));
    }

    @Test
    void usarSoloUnEnumNoConvierteEnServicio() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("servicio");
        java.nio.file.Path p = java.nio.file.Files.createDirectories(dir.resolve("src/juego"));
        java.nio.file.Files.writeString(p.resolve("Resultado.java"), "package juego; public enum Resultado { GANA, PIERDE }");
        java.nio.file.Files.writeString(p.resolve("Jugador.java"), String.join("\n",
                "package juego;",
                "public class Jugador {",
                "  private int puntos;",
                "  public Jugador(int puntos) { this.puntos = puntos; }",
                "  public Resultado competir(Jugador otro) { return puntos > otro.puntos ? Resultado.GANA : Resultado.PIERDE; }",
                "}"));
        DiagramOptions o = new DiagramOptions();
        o.showMethods = true;
        o.showConstructors = true;
        GeneratedDiagram d = TestSupport.generate(dir, "juego", o);
        String jugador = new PanelTextFormatter(o).text(TestSupport.classifier(d, "Jugador"));
        assertTrue(jugador.contains("+ Jugador(int)"), "el constructor debe mostrarse: " + jugador);
        assertTrue(TestSupport.relations(d).contains("DEPENDENCY Jugador->Resultado"));
    }

    /** Disposición 2D: Agenda | Persona | Fecha arriba; Vehiculo y PlantaVTV debajo. */
    @Test
    void disposicionEnCuadricula() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.VEHICULOS, "vehiculos", new DiagramOptions());
        var box = new java.util.HashMap<String, com.umlgen.layout.Rect>();
        d.layout().boxes().forEach((c, r) -> box.put(c.displayName(), r));
        Rect agenda = box.get("Agenda"), persona = box.get("Persona"), fecha = box.get("Fecha");
        Rect vehiculo = box.get("Vehiculo"), planta = box.get("PlantaVTV");
        assertTrue(agenda.right() < persona.x() && persona.right() < fecha.x(), "fila superior en orden");
        assertTrue(agenda.bottom() < vehiculo.y() + 1 || agenda.y() < vehiculo.y(), "fila superior arriba");
        assertTrue(vehiculo.y() > persona.bottom() && planta.y() > fecha.bottom(), "fila inferior debajo");
        assertTrue(centerX(vehiculo) > persona.x() && centerX(vehiculo) < persona.right(), "Vehiculo debajo de Persona");
        assertTrue(centerX(planta) > fecha.x() && centerX(planta) < fecha.right(), "PlantaVTV debajo de Fecha");
    }

    private static int centerX(Rect r) {
        return r.x() + r.w() / 2;
    }

    @Test
    void estaticoNoFinalSoloSubrayado() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.ESPECIALES, "especial", new DiagramOptions());
        PanelTextFormatter f = new PanelTextFormatter(new DiagramOptions());
        String item = f.text(TestSupport.classifier(d, "Item"));
        assertTrue(item.contains("_- contador: int_"), "static no final, sin valor: " + item);
    }

    @Test
    void sintaxisDeRelacionesEnElXml() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        String xml = new com.umlgen.uxf.UxfWriter(new PanelTextFormatter(d.options())).toXml(d.layout());
        assertTrue(xml.contains("<panel_attributes>lt=&lt;-\nm1=1\n"), "asociación obligatoria");
        assertTrue(xml.contains("<panel_attributes>lt=&lt;-\nm1=0..1\n"), "asociación no asignada en constructores");
        assertTrue(xml.contains("<panel_attributes>lt=&lt;-\nm1=*\n"), "asociación 1 a N");
        assertTrue(xml.contains("- catalogo</panel_attributes>"));
        assertFalse(xml.contains("lt=&lt;&lt;"), "sin triángulos huecos");
        assertTrue(xml.contains("<panel_attributes>lt=&lt;-</panel_attributes>"), "herencia: sólida, punta abierta");
        assertTrue(xml.contains("<panel_attributes>lt=&lt;.</panel_attributes>"), "dependencia/implementación sin texto");
        assertFalse(xml.contains("use&gt;&gt;"), "sin <<use>>");
    }

    @Test
    void validadorDetectaArchivosIncorrectos() {
        UxfValidator v = new UxfValidator();
        assertFalse(v.validateXml("<diagram><element>").isValid());
        String overlap = "<diagram program=\"umletino\" version=\"15.1\"><zoom_level>10</zoom_level>"
                + box(0, 0) + box(50, 50) + "</diagram>";
        assertFalse(v.validateXml(overlap).isValid());
        String ok = "<diagram program=\"umletino\" version=\"15.1\"><zoom_level>10</zoom_level>"
                + box(0, 0) + box(300, 0) + "</diagram>";
        assertTrue(v.validateXml(ok).isValid());
    }

    private static String box(int x, int y) {
        return "<element><id>UMLClass</id><coordinates><x>" + x + "</x><y>" + y + "</y><w>100</w><h>100</h>"
                + "</coordinates><panel_attributes>A</panel_attributes><additional_attributes></additional_attributes></element>";
    }

    @Test
    void erroresClarosAnteProyectoOPaqueteVacio() throws Exception {
        Path empty = Files.createTempDirectory("vacio");
        GenerationException e = assertThrows(GenerationException.class, () -> new DiagramGenerator().scan(empty));
        assertTrue(e.getMessage().contains("no contiene archivos .java"));
        DiagramGenerator g = new DiagramGenerator();
        var s = g.scan(TestSupport.BIBLIOTECA);
        GenerationException e2 = assertThrows(GenerationException.class,
                () -> g.generate(s, "com.ejemplo", new DiagramOptions(), com.umlgen.service.ProgressListener.NONE));
        assertTrue(e2.getMessage().contains("no contiene archivos .java"));
        GenerationException e3 = assertThrows(GenerationException.class,
                () -> g.generate(s, "com.ejemplo.roto", new DiagramOptions(), com.umlgen.service.ProgressListener.NONE));
        assertTrue(e3.getMessage().contains("No se encontraron clases"));
    }

    @Test
    void errorDeEscrituraSeInformaSinCerrarLaAplicacion() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        Path file = Files.createTempFile("no-es-carpeta", ".txt");
        assertThrows(GenerationException.class, () -> new DiagramGenerator().export(d, file.resolve("x.uxf")));
    }
}
