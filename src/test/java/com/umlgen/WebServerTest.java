package com.umlgen;

import com.umlgen.web.WebServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueba de punta a punta de la interfaz web: subir un .zip, detectar el paquete y generar el .uxf. */
class WebServerTest {

    @Test
    void subirZipYGenerarDiagrama() throws Exception {
        WebServer server = new WebServer(0);
        server.start();
        try {
            String base = "http://localhost:" + server.port();
            HttpClient http = HttpClient.newHttpClient();

            HttpResponse<String> index = http.send(HttpRequest.newBuilder(URI.create(base + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, index.statusCode());
            assertTrue(index.body().contains("Java2UML"));

            HttpResponse<String> upload = http.send(HttpRequest.newBuilder(URI.create(base + "/api/proyecto"))
                    .header("Content-Type", "application/zip")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(zip(TestSupport.BIBLIOTECA))).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, upload.statusCode(), upload.body());
            assertTrue(upload.body().contains("\"sugerido\":\"com.ejemplo.biblioteca\""), upload.body());
            String id = upload.body().replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

            HttpResponse<String> gen = http.send(HttpRequest.newBuilder(URI.create(base + "/api/generar"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("id=" + id + "&paquete=com.ejemplo.biblioteca")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, gen.statusCode(), gen.body());
            assertTrue(gen.body().contains("\"valido\":true"));
            assertTrue(gen.body().contains("\"clases\":10"));
            assertTrue(gen.body().contains("lt=&lt;-\\nm1=*"), "asociación 1 a N en el .uxf");

            HttpResponse<String> expired = http.send(HttpRequest.newBuilder(URI.create(base + "/api/generar"))
                    .POST(HttpRequest.BodyPublishers.ofString("id=x&paquete=y")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, expired.statusCode());
        } finally {
            server.stop();
        }
    }

    private static byte[] zip(Path dir) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes); Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                z.putNextEntry(new ZipEntry(dir.relativize(p).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(p));
                z.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
