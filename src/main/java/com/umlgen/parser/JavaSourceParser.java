package com.umlgen.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.umlgen.model.AnalysisReport;
import com.umlgen.project.JavaSourceFile;
import com.umlgen.project.ProjectScanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Análisis sintáctico de archivos Java con JavaParser.
 * Los archivos con errores de sintaxis se informan en el reporte y se omiten, sin interrumpir el proceso.
 */
public final class JavaSourceParser {
    private final JavaParser parser;

    public JavaSourceParser() {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(highestStableLanguageLevel());
        config.setAttributeComments(false);
        this.parser = new JavaParser(config);
    }

    /** Versión de Java más alta que se acepta (Java 8 a 25 inclusive). */
    static final int MAX_JAVA_VERSION = 25;

    /**
     * Nivel de lenguaje del parser: el más alto disponible hasta Java 25 (con JavaParser 3.26+ es JAVA_25,
     * que incluye todo lo de JAVA_21: records, sealed, pattern matching, switch con patrones, text blocks...).
     * Al ser un superconjunto, también acepta código Java 8, 11 o 17.
     */
    static ParserConfiguration.LanguageLevel highestStableLanguageLevel() {
        ParserConfiguration.LanguageLevel best = ParserConfiguration.LanguageLevel.JAVA_8;
        int bestVersion = 8;
        for (ParserConfiguration.LanguageLevel level : ParserConfiguration.LanguageLevel.values()) {
            String n = level.name();
            if (n.matches("JAVA_\\d+")) {
                int v = Integer.parseInt(n.substring(5));
                if (v > bestVersion && v <= MAX_JAVA_VERSION) {
                    bestVersion = v;
                    best = level;
                }
            }
        }
        return best;
    }

    public List<ParsedSource> parseAll(List<JavaSourceFile> files, AnalysisReport report) {
        List<ParsedSource> result = new ArrayList<>();
        for (JavaSourceFile f : files) {
            ParsedSource ps = parse(f, report);
            if (ps != null) {
                result.add(ps);
            }
        }
        return result;
    }

    public ParsedSource parse(JavaSourceFile file, AnalysisReport report) {
        String name = file.path().getFileName().toString();
        try {
            String source = ProjectScanner.readSource(file.path());
            ParseResult<CompilationUnit> r = parser.parse(source);
            if (r.isSuccessful() && r.getResult().isPresent()) {
                return new ParsedSource(file, r.getResult().get(), source);
            }
            String msg = r.getProblems().isEmpty() ? "error desconocido" : describe(r.getProblems().get(0));
            report.addParseError(name + ": error de sintaxis — " + msg + " (archivo omitido)");
        } catch (IOException e) {
            report.addParseError(name + ": no se pudo leer el archivo — " + e.getMessage());
        } catch (RuntimeException e) {
            report.addParseError(name + ": error inesperado al analizar — " + e);
        }
        return null;
    }

    private static String describe(Problem p) {
        String location = p.getLocation()
                .flatMap(l -> l.getBegin().getRange())
                .map(range -> "línea " + range.begin.line + ": ")
                .orElse("");
        String msg = p.getMessage();
        int nl = msg.indexOf('\n');
        if (nl > 0) {
            msg = msg.substring(0, nl);
        }
        int expected = msg.indexOf(", expected");
        if (expected > 0) {
            msg = msg.substring(0, expected);
        }
        if (msg.length() > 150) {
            msg = msg.substring(0, 150) + "…";
        }
        return location + msg;
    }
}
