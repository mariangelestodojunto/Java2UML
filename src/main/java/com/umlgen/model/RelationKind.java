package com.umlgen.model;

/** Tipos de relación UML soportados y su estilo de línea en UMLetino. */
public enum RelationKind {
    /** extends: línea continua con punta abierta (no se usan triángulos huecos). */
    GENERALIZATION("herencia", "<-"),
    /** implements: línea discontinua con punta abierta (no se usan triángulos huecos). */
    REALIZATION("implementación", "<."),
    /** atributo de otra clase o enum del dominio: línea continua con punta abierta (navegabilidad) hacia el contenido. */
    ASSOCIATION("asociación", "<-"),
    /** uso en parámetros, retornos o cuerpo de métodos: línea discontinua con punta abierta, sin texto. */
    DEPENDENCY("dependencia", "<.");

    private final String label;
    private final String umletLineType;

    RelationKind(String label, String umletLineType) {
        this.label = label;
        this.umletLineType = umletLineType;
    }

    public String label() {
        return label;
    }

    /** Valor de "lt=" en UMLetino. */
    public String umletLineType() {
        return umletLineType;
    }

    /**
     * true si la punta de la flecha se dibuja en el ÚLTIMO punto de la línea. Todos los tipos actuales
     * ("&lt;-" y "&lt;.") la dibujan en el PRIMER punto, que es el destino; allí se
     * ubica también la multiplicidad m1.
     */
    public boolean arrowAtEnd() {
        return false;
    }
}
