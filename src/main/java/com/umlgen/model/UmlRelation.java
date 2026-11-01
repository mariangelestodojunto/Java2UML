package com.umlgen.model;

import java.util.Objects;

/**
 * Relación dirigida entre dos clasificadores. "source" es quien declara la relación
 * (la subclase, la clase que tiene el atributo, la que usa a otra) y "target" el referenciado.
 */
public final class UmlRelation {
    private final RelationKind kind;
    private final UmlClassifier source;
    private final UmlClassifier target;
    /** Rol en el extremo destino, p. ej. "- personas". Puede ser null. */
    private final String roleLabel;
    /** Multiplicidad en el extremo destino. null si no se puede determinar con seguridad. */
    private final String multiplicity;
    /** Descripción de dónde se detectó (para el resumen). */
    private final String origin;

    public UmlRelation(RelationKind kind, UmlClassifier source, UmlClassifier target,
                       String roleLabel, String multiplicity, String origin) {
        this.kind = Objects.requireNonNull(kind);
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
        this.roleLabel = roleLabel;
        this.multiplicity = multiplicity;
        this.origin = origin;
    }

    public RelationKind kind() { return kind; }
    public UmlClassifier source() { return source; }
    public UmlClassifier target() { return target; }
    public String roleLabel() { return roleLabel; }
    public String multiplicity() { return multiplicity; }
    public String origin() { return origin; }
    public boolean isSelfRelation() { return source == target; }

    /** Texto que se escribe en el medio de la línea: el nombre del atributo (las dependencias no llevan texto). */
    public String middleText() {
        return kind == RelationKind.DEPENDENCY ? null : roleLabel;
    }

    /** Clave para evitar duplicados: mismo tipo, mismos extremos y mismo rol. */
    public String identityKey() {
        return kind + "|" + source.qualifiedName() + "|" + target.qualifiedName() + "|" + (roleLabel == null ? "" : roleLabel);
    }

    @Override
    public String toString() {
        String role = roleLabel == null ? "" : " [" + roleLabel + "]";
        String mult = multiplicity == null ? "" : " (" + multiplicity + ")";
        return kind.label() + ": " + source.displayName() + " → " + target.displayName() + role + mult;
    }
}
