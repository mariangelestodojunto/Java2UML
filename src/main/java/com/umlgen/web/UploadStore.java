package com.umlgen.web;

import com.umlgen.project.ProjectStructure;
import com.umlgen.service.DiagramGenerator;
import com.umlgen.service.GenerationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Guarda temporalmente los proyectos subidos: extrae sólo los .java (de archivos sueltos o de un .zip),
 * con límites de tamaño y protección contra rutas maliciosas ("zip slip"). Los proyectos se borran solos
 * a los 30 minutos sin uso.
 */
final class UploadStore {
    static final long MAX_TOTAL_BYTES = 80L * 1024 * 1024;   // tamaño descomprimido total
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;     // por archivo .java
    static final int MAX_FILES = 5000;
    static final int MAX_PROJECTS = 100;
    static final long TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.$\\s]+?)\\s*;", Pattern.MULTILINE);

    private final Map<String, UploadedProject> projects = new ConcurrentHashMap<>();
    private final Path root;
    private final DiagramGenerator generator = new DiagramGenerator();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "limpieza-proyectos");
        t.setDaemon(true);
        return t;
    });

    UploadStore() throws IOException {
        root = Files.createTempDirectory("java2uml-web");
        cleaner.scheduleAtFixedRate(this::expire, 1, 1, TimeUnit.MINUTES);
    }

    UploadedProject get(String id) {
        UploadedProject p = id == null ? null : projects.get(id);
        if (p != null) {
            p.lastAccess = System.currentTimeMillis();
        }
        return p;
    }

    /** Crea un proyecto a partir de las partes subidas (.java y/o .zip). */
    UploadedProject create(List<MultipartParser.Part> parts) throws GenerationException, IOException {
        String id = UUID.randomUUID().toString();
        Path dir = Files.createDirectory(root.resolve(id));
        Counter counter = new Counter();
        try {
            for (MultipartParser.Part part : parts) {
                String name = part.filename() == null ? "" : part.filename().toLowerCase();
                if (name.endsWith(".zip")) {
                    extractZip(part.content(), dir, counter);
                } else if (name.endsWith(".java")) {
                    storeJava(baseName(part.filename()), part.content(), dir, counter);
                }
            }
            if (counter.files == 0) {
                throw new GenerationException("No se encontraron archivos .java. Subí archivos .java sueltos, "
                        + "una carpeta o un .zip del proyecto.");
            }
            ProjectStructure structure = generator.scan(dir);
            UploadedProject p = new UploadedProject(id, dir, structure);
            evictIfFull();
            projects.put(id, p);
            return p;
        } catch (GenerationException | IOException | RuntimeException e) {
            deleteRecursively(dir);
            throw e;
        }
    }

    private static final class Counter {
        int files;
        long bytes;
    }

    private void extractZip(byte[] zip, Path dir, Counter counter) throws IOException, GenerationException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String entry = e.getName().replace('\\', '/');
                if (e.isDirectory() || !entry.toLowerCase().endsWith(".java") || entry.contains("__MACOSX/")) {
                    continue;
                }
                storeJava(baseName(entry), readLimited(in), dir, counter);
            }
        } catch (IllegalArgumentException e) {
            throw new GenerationException("No se pudo leer el .zip (¿nombres con caracteres no válidos?): " + e.getMessage());
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException, GenerationException {
        byte[] data = in.readNBytes((int) MAX_FILE_BYTES + 1);
        if (data.length > MAX_FILE_BYTES) {
            throw new GenerationException("Un archivo .java supera el máximo de 2 MB.");
        }
        return data;
    }

    /**
     * Guarda el .java dentro de una carpeta que coincide con su "package", sin usar la ruta original
     * (así no importa cómo se armó el .zip y no hay riesgo de escribir fuera de la carpeta temporal).
     */
    private static void storeJava(String fileName, byte[] content, Path dir, Counter counter)
            throws IOException, GenerationException {
        if (content.length > MAX_FILE_BYTES) {
            throw new GenerationException("El archivo " + fileName + " supera el máximo de 2 MB.");
        }
        counter.files++;
        counter.bytes += content.length;
        if (counter.files > MAX_FILES) {
            throw new GenerationException("El proyecto tiene más de " + MAX_FILES + " archivos .java.");
        }
        if (counter.bytes > MAX_TOTAL_BYTES) {
            throw new GenerationException("El proyecto supera los 80 MB de código fuente.");
        }
        String safeName = fileName.replaceAll("[^\\p{L}\\p{N}_$.\\-]", "_");
        if (!safeName.endsWith(".java") || safeName.startsWith(".")) {
            return;
        }
        String source = new String(content, StandardCharsets.UTF_8);
        String pkg = packageOf(source);
        Path target = dir;
        for (String segment : pkg.isEmpty() ? new String[0] : pkg.split("\\.")) {
            target = target.resolve(segment);
        }
        Path file = target.resolve(safeName);
        int n = 1;
        while (Files.exists(file)) { // dos archivos con el mismo nombre y paquete: se guarda aparte
            Path alt = dir.resolve("_copia" + n++);
            for (String segment : pkg.isEmpty() ? new String[0] : pkg.split("\\.")) {
                alt = alt.resolve(segment);
            }
            target = alt;
            file = target.resolve(safeName);
        }
        if (!file.normalize().startsWith(dir)) {
            return;
        }
        Files.createDirectories(target);
        Files.write(file, content);
    }

    static String packageOf(String source) {
        String noComments = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
        Matcher m = PACKAGE.matcher(noComments);
        if (!m.find()) {
            return "";
        }
        String pkg = m.group(1).replaceAll("\\s+", "");
        return pkg.matches("[\\p{L}_$][\\p{L}\\p{N}_$]*(\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*") ? pkg : "";
    }

    private static String baseName(String path) {
        String p = path.replace('\\', '/');
        return p.substring(p.lastIndexOf('/') + 1);
    }

    private void evictIfFull() {
        while (projects.size() >= MAX_PROJECTS) {
            projects.values().stream().min(Comparator.comparingLong(p -> p.lastAccess))
                    .ifPresent(p -> remove(p.id));
        }
    }

    private void expire() {
        long now = System.currentTimeMillis();
        Iterator<UploadedProject> it = projects.values().iterator();
        while (it.hasNext()) {
            UploadedProject p = it.next();
            if (now - p.lastAccess > TTL_MILLIS) {
                remove(p.id);
            }
        }
    }

    private void remove(String id) {
        UploadedProject p = projects.remove(id);
        if (p != null) {
            deleteRecursively(p.directory);
        }
    }

    static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // se reintenta en la próxima limpieza
                }
            });
        } catch (IOException ignored) {
            // carpeta ya borrada
        }
    }

    void shutdown() {
        cleaner.shutdownNow();
        projects.keySet().forEach(this::remove);
        deleteRecursively(root);
    }
}
