package com.umlgen.uxf;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida un .uxf releyéndolo como XML: estructura UMLetino, coordenadas, superposición de cajas,
 * extremos de las relaciones apoyados sobre cajas y líneas que atraviesan otras cajas.
 */
public final class UxfValidator {
    private static final int TOLERANCE = 3;

    public ValidationResult validate(Path file) {
        try {
            return validateXml(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            ValidationResult r = new ValidationResult();
            r.error("No se pudo leer el archivo generado: " + e.getMessage());
            return r;
        }
    }

    public ValidationResult validateXml(String xml) {
        ValidationResult result = new ValidationResult();
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            doc = b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            result.error("El XML no está bien formado: " + e.getMessage());
            return result;
        }
        Element root = doc.getDocumentElement();
        if (!"diagram".equals(root.getTagName())) {
            result.error("El elemento raíz debe ser <diagram> y es <" + root.getTagName() + ">");
            return result;
        }
        if (root.getAttribute("program").isEmpty()) {
            result.error("Falta el atributo program en <diagram>");
        }
        if (root.getElementsByTagName("zoom_level").getLength() != 1) {
            result.error("Debe haber exactamente un <zoom_level>");
        }

        List<int[]> boxes = new ArrayList<>();
        List<String> boxNames = new ArrayList<>();
        List<int[][]> relationPoints = new ArrayList<>();
        NodeList elements = root.getElementsByTagName("element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            String id = text(el, "id");
            int[] rect;
            try {
                Element coords = child(el, "coordinates");
                rect = new int[]{num(coords, "x"), num(coords, "y"), num(coords, "w"), num(coords, "h")};
            } catch (RuntimeException e) {
                result.error("Elemento " + (i + 1) + " (" + id + "): coordenadas inválidas");
                continue;
            }
            if (rect[2] <= 0 || rect[3] <= 0) {
                result.error("Elemento " + (i + 1) + " (" + id + "): ancho/alto debe ser positivo");
            }
            if (child(el, "panel_attributes") == null || child(el, "additional_attributes") == null) {
                result.error("Elemento " + (i + 1) + " (" + id + "): faltan panel_attributes o additional_attributes");
                continue;
            }
            if ("UMLClass".equals(id)) {
                result.classCount++;
                boxes.add(rect);
                String panel = text(el, "panel_attributes");
                String first = panel.split("\n")[0];
                boxNames.add(first.startsWith("<<") && panel.contains("\n") ? panel.split("\n")[1] : first);
            } else if ("Relation".equals(id)) {
                result.relationCount++;
                String panel = text(el, "panel_attributes");
                if (!panel.startsWith("lt=")) {
                    result.warning("Relación " + result.relationCount + " sin tipo de línea (lt=)");
                }
                String[] parts = text(el, "additional_attributes").split(";");
                if (parts.length < 4 || parts.length % 2 != 0) {
                    result.error("Relación " + result.relationCount + ": lista de puntos inválida");
                    continue;
                }
                int[][] pts = new int[parts.length / 2][2];
                try {
                    for (int p = 0; p < pts.length; p++) {
                        double px = Double.parseDouble(parts[2 * p].trim());
                        double py = Double.parseDouble(parts[2 * p + 1].trim());
                        if (px < 0 || py < 0 || px > rect[2] || py > rect[3]) {
                            result.warning("Relación " + result.relationCount + ": punto fuera de su área");
                        }
                        pts[p][0] = rect[0] + (int) Math.round(px);
                        pts[p][1] = rect[1] + (int) Math.round(py);
                    }
                } catch (NumberFormatException e) {
                    result.error("Relación " + result.relationCount + ": coordenadas no numéricas");
                    continue;
                }
                relationPoints.add(pts);
            } else {
                result.warning("Elemento de tipo desconocido: " + id);
            }
        }

        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                if (overlap(boxes.get(i), boxes.get(j))) {
                    result.error("Las cajas \"" + boxNames.get(i) + "\" y \"" + boxNames.get(j) + "\" se superponen");
                }
            }
        }
        int crossingCount = 0;
        for (int r = 0; r < relationPoints.size(); r++) {
            int[][] pts = relationPoints.get(r);
            int startBox = boxTouching(boxes, pts[0]);
            int endBox = boxTouching(boxes, pts[pts.length - 1]);
            if (startBox < 0 || endBox < 0) {
                result.warning("Relación " + (r + 1) + ": un extremo no está apoyado sobre ninguna caja");
            }
            for (int s = 0; s + 1 < pts.length; s++) {
                for (int b = 0; b < boxes.size(); b++) {
                    if (b == startBox || b == endBox) {
                        continue;
                    }
                    if (segmentCrossesBox(pts[s], pts[s + 1], boxes.get(b))) {
                        crossingCount++;
                        result.warning("Una línea atraviesa la caja \"" + boxNames.get(b) + "\"");
                    }
                }
            }
        }
        return result;
    }

    private static boolean overlap(int[] a, int[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    /** Índice de la caja cuyo borde (con tolerancia) contiene el punto, o -1. */
    private static int boxTouching(List<int[]> boxes, int[] p) {
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            if (p[0] >= b[0] - TOLERANCE && p[0] <= b[0] + b[2] + TOLERANCE
                    && p[1] >= b[1] - TOLERANCE && p[1] <= b[1] + b[3] + TOLERANCE) {
                return i;
            }
        }
        return -1;
    }

    /** true si el segmento pasa por el interior de la caja (reducida unos píxeles). */
    static boolean segmentCrossesBox(int[] a, int[] b, int[] box) {
        double x0 = box[0] + TOLERANCE, y0 = box[1] + TOLERANCE;
        double x1 = box[0] + box[2] - TOLERANCE, y1 = box[1] + box[3] - TOLERANCE;
        double t0 = 0, t1 = 1;
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {a[0] - x0, x1 - a[0], a[1] - y0, y1 - a[1]};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) {
                    return false;
                }
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0) {
                    t0 = Math.max(t0, t);
                } else {
                    t1 = Math.min(t1, t);
                }
            }
        }
        return t0 < t1;
    }

    private static Element child(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    private static String text(Element parent, String tag) {
        Element e = child(parent, tag);
        return e == null ? "" : e.getTextContent();
    }

    private static int num(Element parent, String tag) {
        return Integer.parseInt(text(parent, tag).trim());
    }
}
