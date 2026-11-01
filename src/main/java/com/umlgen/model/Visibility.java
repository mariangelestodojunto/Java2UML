package com.umlgen.model;

/** Visibilidad UML de un miembro, con su símbolo estándar. */
public enum Visibility {
    PUBLIC("+"), PRIVATE("-"), PROTECTED("#"), PACKAGE("~");

    private final String symbol;

    Visibility(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
