package com.umlgen.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.umlgen.project.JavaSourceFile;

/** Unidad de compilación ya analizada junto con su archivo de origen. */
public record ParsedSource(JavaSourceFile file, CompilationUnit unit) {
}
