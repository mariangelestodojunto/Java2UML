package com.umlgen.uxf;

import java.util.ArrayList;
import java.util.List;

/** Resultado de validar un archivo .uxf. */
public final class ValidationResult {
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    int classCount;
    int relationCount;

    public void error(String s) { errors.add(s); }
    public void warning(String s) { warnings.add(s); }
    public List<String> errors() { return errors; }
    public List<String> warnings() { return warnings; }
    public boolean isValid() { return errors.isEmpty(); }
    public int classCount() { return classCount; }
    public int relationCount() { return relationCount; }
}
