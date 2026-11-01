package com.umlgen.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Resultado de explorar un proyecto: carpetas de código fuente, archivos y paquetes. */
public final class ProjectStructure {
    /** Nombre usado para mostrar el paquete por defecto (sin declaración package). */
    public static final String DEFAULT_PACKAGE_LABEL = "(paquete por defecto)";

    private final Path projectRoot;
    private final List<JavaSourceFile> files;
    private final Set<Path> sourceRoots = new TreeSet<>();
    private final Map<String, List<JavaSourceFile>> filesByPackage = new TreeMap<>();
    private final List<String> warnings;

    public ProjectStructure(Path projectRoot, List<JavaSourceFile> files, List<String> warnings) {
        this.projectRoot = projectRoot;
        this.files = List.copyOf(files);
        this.warnings = List.copyOf(warnings);
        for (JavaSourceFile f : files) {
            sourceRoots.add(f.sourceRoot());
            filesByPackage.computeIfAbsent(f.packageName(), k -> new ArrayList<>()).add(f);
        }
    }

    public Path projectRoot() { return projectRoot; }
    public List<JavaSourceFile> files() { return files; }
    public Set<Path> sourceRoots() { return Collections.unmodifiableSet(sourceRoots); }
    public List<String> warnings() { return warnings; }

    /** Paquetes declarados (el paquete por defecto es la cadena vacía), ordenados alfabéticamente. */
    public Set<String> packages() {
        return Collections.unmodifiableSet(filesByPackage.keySet());
    }

    public int fileCount(String pkg) {
        List<JavaSourceFile> l = filesByPackage.get(pkg);
        return l == null ? 0 : l.size();
    }

    /** Archivos de un paquete y, opcionalmente, de sus subpaquetes. */
    public List<JavaSourceFile> filesOf(String pkg, boolean includeSubpackages, boolean excludeTests) {
        List<JavaSourceFile> result = new ArrayList<>();
        for (Map.Entry<String, List<JavaSourceFile>> e : filesByPackage.entrySet()) {
            String p = e.getKey();
            boolean match = p.equals(pkg)
                    || (includeSubpackages && (pkg.isEmpty() || p.startsWith(pkg + ".")));
            if (match) {
                for (JavaSourceFile f : e.getValue()) {
                    if (!(excludeTests && f.testSource())) {
                        result.add(f);
                    }
                }
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public static String displayName(String pkg) {
        return pkg.isEmpty() ? DEFAULT_PACKAGE_LABEL : pkg;
    }
}
