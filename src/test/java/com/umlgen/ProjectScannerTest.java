package com.umlgen;

import com.umlgen.project.ProjectScanner;
import com.umlgen.project.ProjectStructure;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScannerTest {

    @Test
    void detectaPaquetesYCarpetaDeFuentes() throws Exception {
        ProjectStructure s = new ProjectScanner().scan(TestSupport.BIBLIOTECA);
        assertEquals(Set.of("com.ejemplo.biblioteca", "com.ejemplo.biblioteca.util", "com.ejemplo.otro", "com.ejemplo.roto"),
                s.packages());
        assertEquals(1, s.sourceRoots().size());
        assertTrue(s.sourceRoots().iterator().next().endsWith("src"));
        assertEquals(10, s.fileCount("com.ejemplo.biblioteca"));
    }

    @Test
    void estructuraMavenYPaquetePorDefecto() throws Exception {
        Path root = Files.createTempDirectory("maven");
        Path main = Files.createDirectories(root.resolve("src/main/java/a/b"));
        Path test = Files.createDirectories(root.resolve("src/test/java/a/b"));
        Path target = Files.createDirectories(root.resolve("target/classes/a/b"));
        Files.writeString(main.resolve("X.java"), "/* package falso; */\npackage a.b;\nclass X {}");
        Files.writeString(test.resolve("XTest.java"), "package a.b;\nclass XTest {}");
        Files.writeString(target.resolve("Copia.java"), "package a.b;\nclass Copia {}");
        Files.writeString(root.resolve("Suelta.java"), "class Suelta {}");
        ProjectStructure s = new ProjectScanner().scan(root);
        assertEquals(Set.of("", "a.b"), s.packages());
        assertEquals(1, s.filesOf("a.b", false, true).size(), "excluye src/test y target");
        assertEquals(2, s.filesOf("a.b", false, false).size());
        assertEquals(1, s.filesOf("", false, true).size());
    }
}
