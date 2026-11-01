package com.umlgen.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.umlgen.app.App;
import com.umlgen.config.DiagramOptions;
import com.umlgen.model.ClassifierKind;
import com.umlgen.project.ProjectStructure;
import com.umlgen.service.DiagramGenerator;
import com.umlgen.service.GeneratedDiagram;
import com.umlgen.service.GenerationException;
import com.umlgen.service.ProgressListener;
import com.umlgen.uxf.PanelTextFormatter;
import com.umlgen.uxf.UxfValidator;
import com.umlgen.uxf.UxfWriter;
import com.umlgen.uxf.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Interfaz web liviana con el servidor HTTP que trae el JDK (com.sun.net.httpserver), sin dependencias.
 * <ul>
 *   <li>GET  /                → página (src/main/resources/web/index.html)</li>
 *   <li>POST /api/proyecto    → multipart con .java y/o .zip; devuelve paquetes y el sugerido</li>
 *   <li>POST /api/generar     → id + paquete + opciones; devuelve el .uxf y el resumen</li>
 *   <li>GET  /salud           → "ok" (verificación de salud para Render/Railway)</li>
 * </ul>
 */
public final class WebServer {
    static final int MAX_UPLOAD_BYTES = 25 * 1024 * 1024;

    private final HttpServer server;
    private final UploadStore store;
    private final DiagramGenerator generator = new DiagramGenerator();
    private final byte[] indexHtml;

    public WebServer(int port) throws IOException {
        store = new UploadStore();
        try (InputStream in = WebServer.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                throw new IOException("No se encontró /web/index.html en el classpath");
            }
            indexHtml = in.readAllBytes();
        }
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
        server.setExecutor(pool);
        server.createContext("/", this::handleRoot);
        server.createContext("/salud", ex -> send(ex, 200, "text/plain; charset=utf-8", "ok".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/api/proyecto", ex -> safely(ex, this::handleUpload));
        server.createContext("/api/generar", ex -> safely(ex, this::handleGenerate));
    }

    public void start() {
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            store.shutdown();
        }));
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
        store.shutdown();
    }

    /** Puerto: argumento, o variable PORT (la usan Render, Railway, Koyeb...), o 8080. */
    public static int resolvePort(String arg) {
        String value = arg != null ? arg : System.getenv("PORT");
        if (value == null || value.isBlank()) {
            return 8080;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Puerto inválido: " + value);
        }
    }

    public static void main(String portArg) throws IOException {
        WebServer ws = new WebServer(resolvePort(portArg));
        ws.start();
        System.out.println(App.NAME + " " + App.VERSION + " — interfaz web en http://localhost:" + ws.port()
                + "  (Ctrl+C para detener)");
    }

    // ------------------------------------------------------------------------------------------ handlers

    private void handleRoot(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
            send(ex, 405, "text/plain; charset=utf-8", "Método no permitido".getBytes(StandardCharsets.UTF_8));
        } else if ("/".equals(path) || "/index.html".equals(path)) {
            send(ex, 200, "text/html; charset=utf-8", indexHtml);
        } else {
            send(ex, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8));
        }
    }

    private interface Handler {
        Object handle(HttpExchange ex) throws Exception;
    }

    /** Ejecuta el handler y responde JSON; los errores se devuelven como {"error": "..."} sin tumbar el servidor. */
    private void safely(HttpExchange ex, Handler h) throws IOException {
        try {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, Map.of("error", "Usá POST"));
                return;
            }
            sendJson(ex, 200, h.handle(ex));
        } catch (GenerationException e) {
            sendJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("error", "Error inesperado: " + e));
        }
    }

    private Object handleUpload(HttpExchange ex) throws Exception {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        byte[] body = readBody(ex);
        List<MultipartParser.Part> parts;
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            parts = MultipartParser.parse(body, MultipartParser.boundary(contentType));
        } else if (contentType != null && contentType.contains("zip")) {
            parts = List.of(new MultipartParser.Part("archivo", "proyecto.zip", body));
        } else {
            throw new GenerationException("Formato no soportado: enviá multipart/form-data o application/zip");
        }
        UploadedProject p = store.create(parts);
        ProjectStructure s = p.structure;
        List<Map<String, Object>> packages = new ArrayList<>();
        String suggested = null;
        int best = -1;
        for (String pkg : s.packages()) {
            int n = s.fileCount(pkg);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", pkg);
            m.put("etiqueta", ProjectStructure.displayName(pkg));
            m.put("archivos", n);
            packages.add(m);
            if (n > best) { // paquete con más clases = el más probable de diagramar
                best = n;
                suggested = pkg;
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", p.id);
        r.put("archivos", s.files().size());
        r.put("paquetes", packages);
        r.put("sugerido", suggested);
        return r;
    }

    private Object handleGenerate(HttpExchange ex) throws Exception {
        Map<String, String> form = parseForm(new String(readBody(ex), StandardCharsets.UTF_8));
        UploadedProject p = store.get(form.get("id"));
        if (p == null) {
            throw new GenerationException("El proyecto expiró o no existe. Volvé a subir los archivos.");
        }
        String pkg = form.getOrDefault("paquete", "");
        DiagramOptions o = new DiagramOptions();
        o.showAttributes = flag(form, "atributos", true);
        o.showMethods = flag(form, "metodos", false);
        o.showConstructors = o.showMethods;
        o.showDependencies = flag(form, "dependencias", true);
        o.includeSubpackages = flag(form, "subpaquetes", false);
        o.includeExternalClasses = flag(form, "externas", false);

        GeneratedDiagram d = generator.generate(p.structure, pkg, o, ProgressListener.NONE);
        String xml = new UxfWriter(new PanelTextFormatter(d.options())).toXml(d.layout());
        ValidationResult v = new UxfValidator().validateXml(xml);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("uxf", xml);
        r.put("nombreArchivo", (pkg.isEmpty() ? "diagrama" : pkg.substring(pkg.lastIndexOf('.') + 1)) + ".uxf");
        r.put("clases", d.model().classifiers().size());
        r.put("interfaces", d.model().count(ClassifierKind.INTERFACE));
        r.put("enums", d.model().count(ClassifierKind.ENUM));
        r.put("relaciones", d.model().relations().size());
        r.put("valido", v.isValid());
        r.put("resumen", d.summary());
        return r;
    }

    // ------------------------------------------------------------------------------------------ utilidades

    private static boolean flag(Map<String, String> form, String key, boolean def) {
        String v = form.get(key);
        return v == null ? def : v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on");
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> m = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            m.put(k, v);
        }
        return m;
    }

    private static byte[] readBody(HttpExchange ex) throws IOException, GenerationException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_UPLOAD_BYTES + 1);
            if (data.length > MAX_UPLOAD_BYTES) {
                throw new GenerationException("El archivo subido supera el máximo de 25 MB.");
            }
            return data;
        }
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.of(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        boolean head = "HEAD".equals(ex.getRequestMethod());
        ex.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        } else {
            ex.close();
        }
    }
}
