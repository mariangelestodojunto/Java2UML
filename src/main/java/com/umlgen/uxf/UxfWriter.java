package com.umlgen.uxf;

import com.umlgen.layout.DiagramLayout;
import com.umlgen.layout.LabelPlacement;
import com.umlgen.layout.Point;
import com.umlgen.layout.Rect;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlRelation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Genera el XML .uxf con exactamente la misma estructura que los archivos guardados por UMLetino 15.1:
 * <pre>
 * &lt;diagram program="umletino" version="15.1"&gt;&lt;zoom_level&gt;10&lt;/zoom_level&gt;
 *   &lt;element&gt;&lt;id&gt;UMLClass&lt;/id&gt;&lt;coordinates&gt;...&lt;/coordinates&gt;
 *     &lt;panel_attributes&gt;...&lt;/panel_attributes&gt;&lt;additional_attributes&gt;&lt;/additional_attributes&gt;&lt;/element&gt;
 *   &lt;element&gt;&lt;id&gt;Relation&lt;/id&gt;... &lt;additional_attributes&gt;x1;y1;x2;y2...&lt;/additional_attributes&gt;&lt;/element&gt;
 * &lt;/diagram&gt;
 * </pre>
 * En las relaciones los puntos son relativos a la esquina del elemento. Asociaciones, herencias e
 * implementaciones empiezan en el destino (allí van m1 y la punta "&lt;&lt;"); las dependencias ("lt=.&gt;")
 * van del origen al destino porque su punta se dibuja en el último punto.
 */
public final class UxfWriter {
    static final String PROGRAM = "umletino";
    static final String VERSION = "15.1";
    static final int ZOOM = 10;
    private static final int RELATION_PADDING = 10;
    private static final double LABEL_CHAR_WIDTH = 7.4;

    private final PanelTextFormatter formatter;

    public UxfWriter(PanelTextFormatter formatter) {
        this.formatter = formatter;
    }

    public String toXml(DiagramLayout layout) {
        StringBuilder sb = new StringBuilder();
        sb.append("<diagram program=\"").append(PROGRAM).append("\" version=\"").append(VERSION).append("\">");
        sb.append("<zoom_level>").append(ZOOM).append("</zoom_level>");
        for (Map.Entry<UmlClassifier, Rect> e : layout.boxes().entrySet()) {
            appendElement(sb, "UMLClass", e.getValue(), formatter.text(e.getKey()), "");
        }
        for (Map.Entry<UmlRelation, List<Point>> e : layout.routes().entrySet()) {
            appendRelation(sb, e.getKey(), e.getValue(), layout.labelPositions().get(e.getKey()));
        }
        sb.append("</diagram>");
        return sb.toString();
    }

    /** Escribe primero en un temporal y luego lo mueve, para no dejar archivos a medio escribir. */
    public void write(DiagramLayout layout, Path file) throws IOException {
        String xml = toXml(layout);
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = Files.createTempFile(dir, "uxf", ".tmp");
        try {
            Files.writeString(tmp, xml, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ------------------------------------------------------------------------------------------

    private void appendRelation(StringBuilder sb, UmlRelation r, List<Point> targetFirst, LabelPlacement placement) {
        // "lt=.>" dibuja la punta en el último punto: la línea se escribe del origen al destino
        List<Point> pts = new ArrayList<>(targetFirst);
        if (r.kind().arrowAtEnd()) {
            java.util.Collections.reverse(pts);
        }
        List<String> panel = new ArrayList<>();
        panel.add("lt=" + r.kind().umletLineType());
        if (r.multiplicity() != null) {
            panel.add("m1=" + r.multiplicity()); // m1 = extremo destino (primer punto)
        }
        String middle = r.middleText();
        if (middle != null) {
            if (placement != null && placement.side() != null) {
                panel.add("cbase=" + placement.side());
            }
            if (placement != null && placement.dy() != 0) {
                panel.add("cpos=0," + placement.dy());
            }
            panel.add(middle);
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : pts) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        // espacio para los textos: rol en el medio de la línea y multiplicidad junto al destino
        if (middle != null) {
            Point mid = midpoint(pts);
            int half = (int) Math.ceil(middle.length() * LABEL_CHAR_WIDTH) + 10;
            int dy = placement == null ? 0 : Math.abs(placement.dy());
            minX = Math.min(minX, mid.x() - half);
            maxX = Math.max(maxX, mid.x() + half);
            minY = Math.min(minY, mid.y() - 20 - dy);
            maxY = Math.max(maxY, mid.y() + 20 + dy);
        }
        if (r.multiplicity() != null) {
            Point first = targetFirst.get(0);
            int ext = (int) Math.ceil(r.multiplicity().length() * LABEL_CHAR_WIDTH) + 20;
            minX = Math.min(minX, first.x() - ext);
            maxX = Math.max(maxX, first.x() + ext);
            minY = Math.min(minY, first.y() - 25);
            maxY = Math.max(maxY, first.y() + 25);
        }
        int x = minX - RELATION_PADDING;
        int y = minY - RELATION_PADDING;
        int w = maxX - minX + 2 * RELATION_PADDING;
        int h = maxY - minY + 2 * RELATION_PADDING;

        StringBuilder points = new StringBuilder();
        for (Point p : pts) {
            if (points.length() > 0) {
                points.append(';');
            }
            points.append(p.x() - x).append(';').append(p.y() - y);
        }
        appendElement(sb, "Relation", new Rect(x, y, w, h), String.join("\n", panel), points.toString());
    }

    /** Punto medio de la poligonal (por longitud), donde UMLetino ubica el texto libre de la relación. */
    static Point midpoint(List<Point> pts) {
        double total = 0;
        for (int i = 1; i < pts.size(); i++) {
            total += pts.get(i - 1).x() == pts.get(i).x() && pts.get(i - 1).y() == pts.get(i).y() ? 0
                    : Math.hypot(pts.get(i).x() - pts.get(i - 1).x(), pts.get(i).y() - pts.get(i - 1).y());
        }
        double half = total / 2;
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1);
            Point b = pts.get(i);
            double len = Math.hypot(b.x() - a.x(), b.y() - a.y());
            if (half <= len && len > 0) {
                double t = half / len;
                return new Point((int) Math.round(a.x() + t * (b.x() - a.x())), (int) Math.round(a.y() + t * (b.y() - a.y())));
            }
            half -= len;
        }
        return pts.get(pts.size() - 1);
    }

    private static void appendElement(StringBuilder sb, String id, Rect r, String panel, String additional) {
        sb.append("<element><id>").append(id).append("</id><coordinates>")
                .append("<x>").append(r.x()).append("</x>")
                .append("<y>").append(r.y()).append("</y>")
                .append("<w>").append(r.w()).append("</w>")
                .append("<h>").append(r.h()).append("</h>")
                .append("</coordinates><panel_attributes>").append(escape(panel))
                .append("</panel_attributes><additional_attributes>").append(escape(additional))
                .append("</additional_attributes></element>");
    }

    /** Escapa los caracteres especiales de XML y descarta caracteres de control inválidos. */
    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\n' || c == '\t') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
