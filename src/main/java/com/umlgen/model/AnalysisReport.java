package com.umlgen.model;

import java.util.ArrayList;
import java.util.List;

/** Resumen del análisis: qué se procesó, qué se omitió y qué resultó ambiguo. */
public final class AnalysisReport {
    private final List<String> parseErrors = new ArrayList<>();
    private final List<String> omitted = new ArrayList<>();
    private final List<String> ambiguous = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private int filesAnalyzed;

    public void addParseError(String s) { parseErrors.add(s); }
    public void addOmitted(String s) { if (!omitted.contains(s)) omitted.add(s); }
    public void addAmbiguous(String s) { if (!ambiguous.contains(s)) ambiguous.add(s); }
    public void addWarning(String s) { if (!warnings.contains(s)) warnings.add(s); }
    public void setFilesAnalyzed(int n) { filesAnalyzed = n; }

    public List<String> parseErrors() { return parseErrors; }
    public List<String> omitted() { return omitted; }
    public List<String> ambiguous() { return ambiguous; }
    public List<String> warnings() { return warnings; }
    public int filesAnalyzed() { return filesAnalyzed; }
}
