package com.umlgen.analysis;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.umlgen.parser.ParsedSource;

/** Tipo declarado en el proyecto (de nivel superior o anidado). */
public record TypeInfo(String qualifiedName, String displayName, String simpleName, String packageName,
                       TypeDeclaration<?> declaration, ParsedSource source) {
}
