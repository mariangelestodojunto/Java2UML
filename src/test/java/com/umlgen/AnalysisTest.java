package com.umlgen;

import com.umlgen.config.DiagramOptions;
import com.umlgen.model.ClassifierKind;
import com.umlgen.model.UmlAttribute;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlRelation;
import com.umlgen.model.Visibility;
import com.umlgen.service.GeneratedDiagram;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del análisis de código y la identificación de relaciones sobre el proyecto de prueba. */
class AnalysisTest {

    @Test
    void detectaTodasLasClasesDelPaquete() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        Set<String> names = d.model().classifiers().stream().map(UmlClassifier::displayName).collect(Collectors.toSet());
        assertEquals(Set.of("Autor", "Biblioteca", "GeneradorReportes", "Genero", "Libro", "Material", "Prestable",
                "Prestamo", "Revista", "Socio"), names);
        assertEquals(ClassifierKind.ABSTRACT_CLASS, TestSupport.classifier(d, "Material").kind());
        assertEquals(ClassifierKind.INTERFACE, TestSupport.classifier(d, "Prestable").kind());
        assertEquals(ClassifierKind.ENUM, TestSupport.classifier(d, "Genero").kind());
        assertEquals(java.util.List.of("NOVELA", "ENSAYO", "POESIA", "CIENCIA_FICCION"),
                TestSupport.classifier(d, "Genero").enumConstants());
    }

    @Test
    void atributosConTiposYVisibilidadCorrectos() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        UmlClassifier material = TestSupport.classifier(d, "Material");
        assertAttribute(material, "titulo", "String", Visibility.PROTECTED);
        assertAttribute(material, "anio", "int", Visibility.PRIVATE);
        assertAttribute(material, "codigoInterno", "String", Visibility.PACKAGE);
        UmlClassifier biblioteca = TestSupport.classifier(d, "Biblioteca");
        assertAttribute(biblioteca, "VERSION", "String", Visibility.PUBLIC);
        assertAttribute(biblioteca, "catalogo", "ArrayList<Material>", Visibility.PRIVATE);
        assertAttribute(biblioteca, "socios", "Socio[]", Visibility.PRIVATE);
        assertAttribute(TestSupport.classifier(d, "Prestable"), "DIAS_MAXIMOS", "int", Visibility.PUBLIC);
    }

    private static void assertAttribute(UmlClassifier c, String name, String type, Visibility v) {
        UmlAttribute a = c.attributes().stream().filter(x -> x.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(c.displayName() + " no tiene el atributo " + name));
        assertEquals(type, a.typeText(), "tipo de " + c.displayName() + "." + name);
        assertEquals(v, a.visibility(), "visibilidad de " + c.displayName() + "." + name);
    }

    @Test
    void relacionesExactasDelProyectoDePrueba() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        Set<String> expected = Set.of(
                "GENERALIZATION Libro->Material",
                "GENERALIZATION Revista->Material",
                "REALIZATION Material->Prestable",
                "ASSOCIATION Libro->Autor [- autor] (1)",
                "ASSOCIATION Libro->Genero [- genero] (1)",
                "ASSOCIATION Autor->Autor [- mentor] (0..1)",
                "ASSOCIATION Biblioteca->Material [- catalogo] (*)",
                "ASSOCIATION Biblioteca->Socio [- socios] (*)",
                "ASSOCIATION Socio->Prestamo [- prestamos] (*)",
                "ASSOCIATION Prestamo->Material [- material] (1)",
                "ASSOCIATION Prestamo->Socio [- socio] (1)",
                "DEPENDENCY Material->Socio",
                "DEPENDENCY Prestable->Socio",
                "DEPENDENCY GeneradorReportes->Material",
                "DEPENDENCY Biblioteca->GeneradorReportes",
                "DEPENDENCY Prestamo->Prestable");
        assertEquals(expected, TestSupport.relations(d));
    }

    @Test
    void noHayRelacionesDuplicadasNiDependenciasRedundantes() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        Set<String> keys = new HashSet<>();
        Set<String> structuralPairs = new HashSet<>();
        for (UmlRelation r : d.model().relations()) {
            assertTrue(keys.add(r.identityKey()), "relación duplicada: " + r);
            if (r.kind() != com.umlgen.model.RelationKind.DEPENDENCY) {
                structuralPairs.add(r.source().qualifiedName() + ">" + r.target().qualifiedName());
            }
        }
        for (UmlRelation r : d.model().relations()) {
            if (r.kind() == com.umlgen.model.RelationKind.DEPENDENCY) {
                assertFalse(structuralPairs.contains(r.source().qualifiedName() + ">" + r.target().qualifiedName()),
                        "dependencia redundante con una asociación/herencia: " + r);
            }
        }
    }

    @Test
    void reglaDeOroLosAtributosAsociadosSalenDeLaCaja() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        UmlClassifier libro = TestSupport.classifier(d, "Libro");
        for (String name : new String[]{"genero", "autor"}) { // enum y clase del dominio
            UmlAttribute a = libro.attributes().stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
            assertTrue(a.isShownAsAssociation(), name + " debe dibujarse como asociación");
        }
        UmlAttribute paginas = libro.attributes().stream().filter(a -> a.name().equals("paginas")).findFirst().orElseThrow();
        assertFalse(paginas.isShownAsAssociation());
    }

    @Test
    void multiplicidadUnoSiSeAsignaEnUnConstructor() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("mult");
        java.nio.file.Path p = java.nio.file.Files.createDirectories(dir.resolve("src/m"));
        java.nio.file.Files.writeString(p.resolve("B.java"), "package m; public class B {}");
        java.nio.file.Files.writeString(p.resolve("A.java"), String.join("\n",
                "package m;",
                "public class A {",
                "  private final B fin;",
                "  private B creado = new B();",
                "  private B enConstructor;",
                "  private B validado;",
                "  private B opcional;",
                "  private B porParametro;",
                "  private B soloSetter;",
                "  private B anulado;",
                "  public A(B b) { this.fin = b; enConstructor = new B(); this.validado = java.util.Objects.requireNonNull(b);",
                "    porParametro = b; anulado = null; }",
                "  public A() { this(new B()); }",
                "  public void setSoloSetter(B b) { soloSetter = b; }",
                "  private B conSetter;",
                "  { conSetter = new B(); }",
                "  public void cambiar(B otro) { this.conSetter = otro; }",
                "  public void local() { B porParametro = null; porParametro = new B(); }",
                "}"));
        GeneratedDiagram d = TestSupport.generate(dir, "m", new DiagramOptions());
        Set<String> rel = TestSupport.relations(d);
        for (String required : new String[]{"fin", "creado", "enConstructor", "validado", "porParametro"}) {
            assertTrue(rel.contains("ASSOCIATION A->B [- " + required + "] (1)"), required + " " + rel);
        }
        for (String optional : new String[]{"opcional", "soloSetter", "anulado", "conSetter"}) {
            assertTrue(rel.contains("ASSOCIATION A->B [- " + optional + "] (0..1)"), optional + " " + rel);
        }
    }

    @Test
    void sintaxisModernaDeJava() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.MODERNO, "moderno", new DiagramOptions());
        assertTrue(d.report().parseErrors().isEmpty(), "errores: " + d.report().parseErrors());
        Set<String> rel = TestSupport.relations(d);
        assertTrue(rel.contains("ASSOCIATION Pedido->Cliente [- cliente] (0..1)"), "componente de record: " + rel);
        assertTrue(rel.contains("ASSOCIATION Pedido->Producto [- productos] (*)"), "lista en record: " + rel);
        assertTrue(rel.contains("REALIZATION Circulo->Figura"), "sealed/permits: " + rel);
        assertTrue(rel.contains("REALIZATION Cuadrado->Figura"), rel.toString());
        assertTrue(rel.contains("DEPENDENCY Calculadora->Figura"), "switch con patrones: " + rel);
        assertEquals(ClassifierKind.CLASS, TestSupport.classifier(d, "Pedido").kind());
    }

    @Test
    void noRelacionaClasesPorCoincidenciaDeNombre() throws Exception {
        DiagramOptions o = new DiagramOptions();
        o.includeExternalClasses = true;
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", o);
        assertTrue(d.model().classifiers().stream().noneMatch(c -> c.qualifiedName().equals("com.ejemplo.otro.Autor")));
        // con clases externas activadas aparece Validador (otro paquete), marcado como externo
        UmlClassifier validador = TestSupport.classifier(d, "Validador");
        assertTrue(validador.isExternal());
        assertTrue(TestSupport.relations(d).contains("DEPENDENCY Socio->Validador"));
    }

    @Test
    void sinClasesExternasLaRelacionSeInformaComoOmitida() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", new DiagramOptions());
        assertTrue(d.report().omitted().stream().anyMatch(s -> s.contains("Validador")));
    }

    @Test
    void casosEspeciales() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.ESPECIALES, "especial", new DiagramOptions());
        Set<String> rel = TestSupport.relations(d);
        assertTrue(rel.contains("GENERALIZATION Auditable->Pagable"), "herencia entre interfaces");
        assertTrue(rel.contains("REALIZATION RepoClientes->Repositorio"), "implementa interfaz genérica");
        assertTrue(rel.contains("ASSOCIATION Cliente->Pedido [- pedidosPorCodigo] (*)"), "Map con valores del proyecto");
        assertTrue(rel.contains("ASSOCIATION Cliente->Direccion [- direccion] (0..1)"), "Optional");
        assertTrue(rel.contains("ASSOCIATION Pedido->Item [- lotes] (*)"), "colección anidada");
        assertTrue(rel.contains("ASSOCIATION Nodo->Nodo [- siguiente] (0..1)"), "autoasociación genérica");
        assertEquals(ClassifierKind.ENUM, TestSupport.classifier(d, "Cliente.Categoria").kind(), "enum anidado");
        // el parámetro de tipo T / E no genera relaciones
        assertTrue(rel.stream().noneMatch(s -> s.contains("->T") || s.contains("->E ")));
        // ambigüedades informadas y no dibujadas
        assertTrue(d.report().ambiguous().stream().anyMatch(s -> s.contains("Factura.items") && s.contains("ambiguo")));
        assertTrue(d.report().ambiguous().stream().anyMatch(s -> s.contains("Supplier")));
        assertTrue(rel.stream().noneMatch(s -> s.startsWith("ASSOCIATION Factura")));
        // archivo con error de sintaxis: se informa y no interrumpe
        assertTrue(d.report().parseErrors().stream().anyMatch(s -> s.startsWith("Roto.java")));
    }

    /**
     * Proyecto agenda-vehiculos: exactamente 5 cajas (Test se omite) y exactamente estas 7 relaciones.
     */
    @Test
    void agendaVehiculosCincoCajasSieteRelaciones() throws Exception {
        GeneratedDiagram d = TestSupport.generate(TestSupport.VEHICULOS, "vehiculos", new DiagramOptions());
        Set<String> names = d.model().classifiers().stream().map(UmlClassifier::displayName).collect(Collectors.toSet());
        assertEquals(Set.of("Agenda", "Persona", "Vehiculo", "Fecha", "PlantaVTV"), names);
        assertEquals(Set.of(
                "ASSOCIATION Persona->Vehiculo [- vehiculo] (0..1)",
                "ASSOCIATION Vehiculo->Fecha [- fechaFabricacion] (1)",
                "ASSOCIATION Vehiculo->Fecha [- fechaUltimaVTV] (0..1)",
                "ASSOCIATION Persona->Fecha [- fechaNacimiento] (0..1)",
                "ASSOCIATION Agenda->Persona [- personas] (*)",
                "DEPENDENCY PlantaVTV->Vehiculo",
                "DEPENDENCY PlantaVTV->Fecha"), TestSupport.relations(d));
        assertTrue(d.report().omitted().stream().anyMatch(o -> o.startsWith("Test (clase de ejecución")));
    }

    /** Multiplicidad indicada con un comentario: tiene prioridad sobre la regla automática. */
    @Test
    void marcaDeMultiplicidadEnElCodigo() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("marcas");
        java.nio.file.Path p = java.nio.file.Files.createDirectories(dir.resolve("src/m"));
        java.nio.file.Files.writeString(p.resolve("Fecha.java"), "package m; public class Fecha {}");
        java.nio.file.Files.writeString(p.resolve("Plan.java"), "package m; public enum Plan { A, B }");
        java.nio.file.Files.writeString(p.resolve("Socio.java"), String.join("\n",
                "package m;",
                "import java.util.List;",
                "public class Socio {",
                "  private Fecha alta;          // 1",
                "  private Fecha baja;          // 0..1",
                "  // 1",
                "  private Plan plan;",
                "  private List<Fecha> pagos;   // 1..*",
                "  private Fecha nacimiento;    // comentario común, no es una marca",
                "  public Socio(Fecha alta, Fecha nacimiento) { this.alta = null; this.nacimiento = nacimiento; }",
                "  public void setPlan(Plan p) { if (p != null) { plan = p; } }",
                "}"));
        GeneratedDiagram d = TestSupport.generate(dir, "m", new DiagramOptions());
        Set<String> rel = TestSupport.relations(d);
        assertTrue(rel.contains("ASSOCIATION Socio->Fecha [- alta] (1)"), "marca al final de la línea: " + rel);
        assertTrue(rel.contains("ASSOCIATION Socio->Fecha [- baja] (0..1)"), rel.toString());
        assertTrue(rel.contains("ASSOCIATION Socio->Plan [- plan] (1)"), "marca en la línea anterior: " + rel);
        assertTrue(rel.contains("ASSOCIATION Socio->Fecha [- pagos] (1..*)"), "colección con marca: " + rel);
        assertTrue(rel.contains("ASSOCIATION Socio->Fecha [- nacimiento] (1)"), "sin marca válida: regla automática");
    }

    @Test
    void opcionesParaVerClasesDeEjecucionYTodasLasDependencias() throws Exception {
        DiagramOptions o = new DiagramOptions();
        o.excludeRunnerClasses = false;
        o.omitNavigableDependencies = false;
        GeneratedDiagram d = TestSupport.generate(TestSupport.VEHICULOS, "vehiculos", o);
        Set<String> rel = TestSupport.relations(d);
        assertTrue(rel.contains("DEPENDENCY Agenda->Fecha"), rel.toString());
        assertTrue(rel.contains("DEPENDENCY Test->Agenda"), rel.toString());
        // aun así, nunca una dependencia donde ya hay asociación (Persona usa Vehiculo en agregarVehiculo)
        assertFalse(rel.contains("DEPENDENCY Persona->Vehiculo"));
    }

    @Test
    void claseQueSoloTieneMainNoSeDiagrama() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("runner");
        java.nio.file.Path p = java.nio.file.Files.createDirectories(dir.resolve("src/r"));
        java.nio.file.Files.writeString(p.resolve("Lanzador.java"),
                "package r; public class Lanzador { public static void main(String[] a) { new Dominio(); } }");
        java.nio.file.Files.writeString(p.resolve("Dominio.java"),
                "package r; public class Dominio { private int x; public static void main(String[] a) {} }");
        GeneratedDiagram d = TestSupport.generate(dir, "r", new DiagramOptions());
        Set<String> names = d.model().classifiers().stream().map(UmlClassifier::displayName).collect(Collectors.toSet());
        assertEquals(Set.of("Dominio"), names, "Lanzador sólo tiene main; Dominio además tiene un atributo");
    }

    @Test
    void opcionesExcluyenInterfacesYEnums() throws Exception {
        DiagramOptions o = new DiagramOptions();
        o.includeInterfaces = false;
        o.includeEnums = false;
        o.showDependencies = false;
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", o);
        assertEquals(0, d.model().count(ClassifierKind.INTERFACE));
        assertEquals(0, d.model().count(ClassifierKind.ENUM));
        assertEquals(0, TestSupport.count(d, com.umlgen.model.RelationKind.DEPENDENCY));
        assertEquals(0, TestSupport.count(d, com.umlgen.model.RelationKind.REALIZATION));
        assertTrue(d.report().omitted().stream().anyMatch(s -> s.contains("Prestable")));
    }

    @Test
    void subpaquetesIncluyenClasesAnidadasEnCarpetas() throws Exception {
        DiagramOptions o = new DiagramOptions();
        o.includeSubpackages = true;
        GeneratedDiagram d = TestSupport.generate(TestSupport.BIBLIOTECA, "com.ejemplo.biblioteca", o);
        assertFalse(TestSupport.classifier(d, "Validador").isExternal());
    }
}
