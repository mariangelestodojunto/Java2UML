package com.umlgen.web;

import com.umlgen.project.ProjectStructure;

import java.nio.file.Path;

/** Proyecto subido por un usuario de la interfaz web, guardado temporalmente en disco. */
final class UploadedProject {
    final String id;
    final Path directory;
    final ProjectStructure structure;
    volatile long lastAccess = System.currentTimeMillis();

    UploadedProject(String id, Path directory, ProjectStructure structure) {
        this.id = id;
        this.directory = directory;
        this.structure = structure;
    }
}
