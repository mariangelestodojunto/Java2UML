package com.umlgen.analysis;

import com.umlgen.model.AnalysisReport;
import com.umlgen.model.UmlModel;

/** Modelo UML resultante del análisis más el reporte con advertencias, omisiones y ambigüedades. */
public record AnalysisResult(UmlModel model, AnalysisReport report) {
}
