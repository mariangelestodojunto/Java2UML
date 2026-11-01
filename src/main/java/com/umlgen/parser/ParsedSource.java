package com.umlgen.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.umlgen.project.JavaSourceFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unidad de compilación ya analizada junto con su archivo de origen y su texto. */
public record ParsedSource(JavaSourceFile file, CompilationUnit unit, String[] lines) {

    /** Multiplicidad UML válida: 1, 0..1, *, 1..*, 0..*, 2..5, etc. */
    private static final Pattern MULTIPLICITY = Pattern.compile("(\\d+|\\*)(\\.\\.(\\d+|\\*))?");
    private static final Pattern TRAILING = Pattern.compile(";\\s*//\\s*(\\S+)\\s*$");
    private static final Pattern WHOLE_LINE = Pattern.compile("^\\s*//\\s*(\\S+)\\s*$");

    public ParsedSource(JavaSourceFile file, CompilationUnit unit, String source) {
        this(file, unit, source.split("\\R", -1));
    }

    /**
     * Multiplicidad indicada a mano para un atributo mediante un comentario con sólo la multiplicidad:
     * al final de la línea de la declaración ({@code private Fecha fecha; // 0..1}) o en la línea anterior
     * ({@code // 1}). Devuelve null si no hay marca.
     */
    public String declaredMultiplicity(Node declaration) {
        if (declaration.getBegin().isEmpty() || declaration.getEnd().isEmpty()) {
            return null;
        }
        int endLine = declaration.getEnd().get().line - 1;   // índices desde 0
        int beginLine = declaration.getBegin().get().line - 1;
        if (endLine >= 0 && endLine < lines.length) {
            String found = valid(TRAILING.matcher(lines[endLine]));
            if (found != null) {
                return found;
            }
        }
        int above = beginLine - 1;
        if (above >= 0 && above < lines.length) {
            return valid(WHOLE_LINE.matcher(lines[above]));
        }
        return null;
    }

    private static String valid(Matcher m) {
        if (m.find()) {
            String value = m.group(1);
            if (MULTIPLICITY.matcher(value).matches()) {
                return value;
            }
        }
        return null;
    }
}
