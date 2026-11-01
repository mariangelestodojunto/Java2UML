package com.umlgen.model;

/** Tipos de clasificador UML que se representan en el diagrama. */
public enum ClassifierKind {
    CLASS("clase"), ABSTRACT_CLASS("clase abstracta"), INTERFACE("interfaz"), ENUM("enumeración");

    private final String label;

    ClassifierKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
