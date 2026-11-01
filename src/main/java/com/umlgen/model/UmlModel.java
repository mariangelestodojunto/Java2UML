package com.umlgen.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Modelo UML completo: clasificadores a dibujar y relaciones detectadas entre ellos. */
public final class UmlModel {
    private final Map<String, UmlClassifier> classifiers = new LinkedHashMap<>();
    private final List<UmlRelation> relations = new ArrayList<>();

    public void addClassifier(UmlClassifier c) {
        classifiers.putIfAbsent(c.qualifiedName(), c);
    }

    public UmlClassifier find(String qualifiedName) {
        return classifiers.get(qualifiedName);
    }

    public Collection<UmlClassifier> classifiers() {
        return classifiers.values();
    }

    public List<UmlRelation> relations() {
        return relations;
    }

    public long count(ClassifierKind kind) {
        return classifiers.values().stream().filter(c -> c.kind() == kind).count();
    }
}
