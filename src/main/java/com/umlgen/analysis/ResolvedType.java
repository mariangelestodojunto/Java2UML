package com.umlgen.analysis;

import java.util.List;

/** Resultado de resolver un nombre de tipo en su contexto. */
public record ResolvedType(Kind kind, TypeInfo projectType, String name, List<String> candidates) {

    public enum Kind {
        /** Tipo declarado en el proyecto. */
        PROJECT,
        /** Tipo externo (biblioteca estándar u otra biblioteca). */
        EXTERNAL,
        /** Parámetro de tipo genérico (T, E, K...). */
        TYPE_VARIABLE,
        /** Varios tipos del proyecto podrían corresponder: no se puede decidir con seguridad. */
        AMBIGUOUS,
        /** No se encontró: se trata como externo desconocido. */
        UNRESOLVED
    }

    static ResolvedType project(TypeInfo t) { return new ResolvedType(Kind.PROJECT, t, t.qualifiedName(), List.of()); }
    static ResolvedType external(String n) { return new ResolvedType(Kind.EXTERNAL, null, n, List.of()); }
    static ResolvedType typeVariable(String n) { return new ResolvedType(Kind.TYPE_VARIABLE, null, n, List.of()); }
    static ResolvedType ambiguous(String n, List<String> c) { return new ResolvedType(Kind.AMBIGUOUS, null, n, List.copyOf(c)); }
    static ResolvedType unresolved(String n) { return new ResolvedType(Kind.UNRESOLVED, null, n, List.of()); }

    /** Último segmento del nombre (p. ej. "List" para "java.util.List"). */
    public String simpleName() {
        int i = name.lastIndexOf('.');
        return i < 0 ? name : name.substring(i + 1);
    }
}
