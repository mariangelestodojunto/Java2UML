package com.umlgen.project;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explora la carpeta de un proyecto Java: encuentra los .java, lee su declaración "package"
 * y deduce automáticamente las carpetas raíz de código fuente (src, src/main/java, etc.).
 * Funciona con proyectos de Eclipse, Maven, Gradle, NetBeans o carpetas sueltas.
 */
public final class ProjectScanner {

    /** Carpetas que nunca contienen código fuente del proyecto. */
    private static final Set<String> IGNORED_DIRS = Set.of(
            "target", "build", "bin", "out", "node_modules", ".git", ".svn", ".idea", ".settings",
            ".gradle", ".mvn", ".vscode", "classes", "dist");

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([\\p{L}_$][\\p{L}\\p{N}_$]*(?:\\s*\\.\\s*[\\p{L}_$][\\p{L}\\p{N}_$]*)*)\\s*;",
                    Pattern.MULTILINE);

    public ProjectStructure scan(Path projectRoot) throws IOException {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            throw new IOException("La carpeta seleccionada no existe o no es una carpeta: " + projectRoot);
        }
        List<JavaSourceFile> files = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Path root = projectRoot.toAbsolutePath().normalize();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root)) {
                    String name = dir.getFileName().toString();
                    if (IGNORED_DIRS.contains(name) || name.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (attrs.isRegularFile() && name.endsWith(".java")
                        && !name.equals("module-info.java") && !name.equals("package-info.java")) {
                    try {
                        String pkg = readPackage(file);
                        files.add(new JavaSourceFile(file, pkg, sourceRootOf(file, pkg), isTestPath(root, file)));
                    } catch (IOException e) {
                        warnings.add("No se pudo leer " + root.relativize(file) + ": " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                warnings.add("Archivo o carpeta inaccesible: " + file + " (" + exc.getMessage() + ")");
                return FileVisitResult.CONTINUE;
            }
        });
        return new ProjectStructure(root, files, warnings);
    }

    /** Lee el contenido de un .java tolerando archivos guardados en UTF-8 o en Windows-1252/ISO-8859-1. */
    public static String readSource(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (MalformedInputException | java.nio.charset.UnmappableCharacterException e) {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
    }

    static String readPackage(Path file) throws IOException {
        String src = stripComments(readSource(file));
        Matcher m = PACKAGE_PATTERN.matcher(src);
        return m.find() ? m.group(1).replaceAll("\\s+", "") : "";
    }

    /** Elimina comentarios para que un "package" comentado no confunda la detección. */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    /** Si la ruta del archivo termina en la ruta del paquete, la raíz es lo que queda por encima. */
    private static Path sourceRootOf(Path file, String pkg) {
        Path dir = file.getParent();
        if (pkg.isEmpty()) {
            return dir;
        }
        String[] parts = pkg.split("\\.");
        Path current = dir;
        for (int i = parts.length - 1; i >= 0; i--) {
            if (current == null || current.getFileName() == null
                    || !current.getFileName().toString().equals(parts[i])) {
                return dir; // la carpeta no coincide con el paquete: usamos la carpeta del archivo
            }
            current = current.getParent();
        }
        return current == null ? dir : current;
    }

    private static boolean isTestPath(Path root, Path file) {
        String rel = root.relativize(file).toString().replace('\\', '/');
        return rel.startsWith("src/test/") || rel.contains("/src/test/") || rel.startsWith("test/");
    }
}
