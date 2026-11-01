package com.umlgen.layout;

import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlRelation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado de la distribución automática: posición/tamaño de cada caja y recorrido de cada relación.
 * En cada recorrido el PRIMER punto es el extremo destino (donde UMLetino dibuja la punta de flecha).
 */
public final class DiagramLayout {
    private final Map<UmlClassifier, Rect> boxes = new LinkedHashMap<>();
    private final Map<UmlRelation, List<Point>> routes = new LinkedHashMap<>();
    /** Ubicación del texto central cuando la ubicación por defecto de UMLetino choca con otros elementos. */
    private final Map<UmlRelation, LabelPlacement> labelPositions = new LinkedHashMap<>();

    public Map<UmlClassifier, Rect> boxes() { return boxes; }
    public Map<UmlRelation, List<Point>> routes() { return routes; }
    public Map<UmlRelation, LabelPlacement> labelPositions() { return labelPositions; }
}
