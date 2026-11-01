package com.umlgen.model;

import java.util.List;

/** Método o constructor de un clasificador. */
public final class UmlOperation {
    private final Visibility visibility;
    private final String name;
    private final List<UmlParameter> parameters;
    private final String returnType;
    private final boolean constructor;
    private final boolean isStatic;
    private final boolean isAbstract;

    public UmlOperation(Visibility visibility, String name, List<UmlParameter> parameters, String returnType,
                        boolean constructor, boolean isStatic, boolean isAbstract) {
        this.visibility = visibility;
        this.name = name;
        this.parameters = List.copyOf(parameters);
        this.returnType = returnType;
        this.constructor = constructor;
        this.isStatic = isStatic;
        this.isAbstract = isAbstract;
    }

    public Visibility visibility() { return visibility; }
    public String name() { return name; }
    public List<UmlParameter> parameters() { return parameters; }
    /** Tipo de retorno; null para constructores. */
    public String returnType() { return returnType; }
    public boolean isConstructor() { return constructor; }
    public boolean isStatic() { return isStatic; }
    public boolean isAbstract() { return isAbstract; }
}
