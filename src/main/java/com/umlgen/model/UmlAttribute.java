package com.umlgen.model;

/** Atributo (campo) de un clasificador, tal como está escrito en el código fuente. */
public final class UmlAttribute {
    private final Visibility visibility;
    private final String name;
    private final String typeText;
    private final boolean isStatic;
    private final boolean isFinal;
    /** Valor literal asignado en la declaración (p. ej. "100", "\"1.0\""), o null. */
    private final String literalValue;
    /** true cuando el atributo se dibuja como asociación y por lo tanto no se lista dentro de la caja. */
    private boolean shownAsAssociation;

    public UmlAttribute(Visibility visibility, String name, String typeText, boolean isStatic, boolean isFinal) {
        this(visibility, name, typeText, isStatic, isFinal, null);
    }

    public UmlAttribute(Visibility visibility, String name, String typeText, boolean isStatic, boolean isFinal,
                        String literalValue) {
        this.literalValue = literalValue;
        this.visibility = visibility;
        this.name = name;
        this.typeText = typeText;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    public Visibility visibility() { return visibility; }
    public String name() { return name; }
    public String typeText() { return typeText; }
    public boolean isStatic() { return isStatic; }
    public boolean isFinal() { return isFinal; }
    public String literalValue() { return literalValue; }
    /** Constante con valor literal: static final con un literal en la declaración. */
    public boolean isLiteralConstant() { return isStatic && isFinal && literalValue != null; }
    /**
     * Valor por defecto de instancia (final NO static con un literal, p. ej. VELOCIDAD_MAXIMA_DEFAULT = 100):
     * no se dibuja.
     */
    public boolean isInstanceDefaultValue() { return !isStatic && isFinal && literalValue != null; }
    public boolean isShownAsAssociation() { return shownAsAssociation; }
    public void setShownAsAssociation(boolean value) { this.shownAsAssociation = value; }
}
