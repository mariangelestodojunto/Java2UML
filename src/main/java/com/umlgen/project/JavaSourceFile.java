package com.umlgen.project;

import java.nio.file.Path;

/** Archivo .java encontrado en el proyecto con el paquete que declara. */
public record JavaSourceFile(Path path, String packageName, Path sourceRoot, boolean testSource) {
}
