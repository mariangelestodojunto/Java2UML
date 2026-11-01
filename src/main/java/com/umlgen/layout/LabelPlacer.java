package com.umlgen.layout;

import com.umlgen.model.UmlRelation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elige de qué lado de la línea se escribe el rol de cada asociación ("- personas") para que los textos
 * no se pisen entre sí ni con las cajas. Reproduce la ubicación que calcula UMLetino para el texto central
 * (punto medio de la poligonal; a la derecha si el tramo es vertical, arriba si es horizontal) y, si hay
 * choque, usa las propiedades "cbase" (l = izquierda, d = abajo) y "cpos" (desplazamiento) de UMLetino.
 */
public final class LabelPlacer {
    private static final double CHAR_WIDTH = 6.8;   // fuente de 12 px de UMLetino
    private static final int TEXT_HEIGHT = 14;
    private static final int DIST = 4;

    public void place(DiagramLayout layout) {
        List<Rect> fixed = new ArrayList<>(layout.boxes().values());
        for (Map.Entry<UmlRelation, List<Point>> e : layout.routes().entrySet()) {
            if (e.getKey().multiplicity() != null) {
                fixed.add(multiplicityArea(e.getValue(), e.getKey().multiplicity()));
            }
        }
        List<UmlRelation> labeled = new ArrayList<>();
        Map<UmlRelation, List<LabelPlacement>> options = new HashMap<>();
        Map<UmlRelation, LabelPlacement> chosen = new HashMap<>();
        for (Map.Entry<UmlRelation, List<Point>> e : layout.routes().entrySet()) {
            if (e.getKey().middleText() != null) {
                labeled.add(e.getKey());
                boolean horizontal = isHorizontalAt(e.getValue());
                List<LabelPlacement> opts = new ArrayList<>();
                for (int dy : new int[]{0, -18, 18, -36, 36}) {
                    opts.add(new LabelPlacement(null, dy));
                    opts.add(new LabelPlacement(horizontal ? "d" : "l", dy));
                }
                options.put(e.getKey(), opts);
                chosen.put(e.getKey(), LabelPlacement.DEFAULT);
            }
        }
        // búsqueda local: cada texto elige el lado con menos choques, hasta que nada cambie
        for (int pass = 0; pass < 6; pass++) {
            boolean changed = false;
            for (UmlRelation r : labeled) {
                LabelPlacement best = chosen.get(r);
                double bestCost = Double.MAX_VALUE;
                for (LabelPlacement opt : options.get(r)) {
                    Rect rect = rectFor(layout, r, opt);
                    // leve preferencia por la ubicación por defecto y por desplazamientos chicos
                    double cost = (opt.side() == null ? 0 : 0.5) + Math.abs(opt.dy()) / 18.0;
                    for (Rect o : fixed) {
                        cost += overlapArea(rect, o);
                    }
                    for (UmlRelation other : labeled) {
                        if (other != r) {
                            cost += overlapArea(rect, rectFor(layout, other, chosen.get(other)));
                        }
                    }
                    for (Map.Entry<UmlRelation, List<Point>> e : layout.routes().entrySet()) {
                        if (e.getKey() != r) {
                            cost += 3 * crossingLength(rect, e.getValue());
                        }
                    }
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = opt;
                    }
                }
                if (!java.util.Objects.equals(best, chosen.get(r))) {
                    chosen.put(r, best);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        for (UmlRelation r : labeled) {
            if (!chosen.get(r).isDefault()) {
                layout.labelPositions().put(r, chosen.get(r));
            }
        }
    }

    private static Rect rectFor(DiagramLayout layout, UmlRelation r, LabelPlacement opt) {
        List<Point> pts = layout.routes().get(r);
        int w = (int) Math.ceil(r.middleText().length() * CHAR_WIDTH);
        return labelRect(midpoint(pts), isHorizontalAt(pts), opt.side(), w).translate(0, opt.dy());
    }

    /** Longitud de la poligonal que pasa por dentro del rectángulo. */
    private static double crossingLength(Rect r, List<Point> pts) {
        double total = 0;
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1);
            Point b = pts.get(i);
            double t0 = 0, t1 = 1;
            double dx = b.x() - a.x(), dy = b.y() - a.y();
            double[] p = {-dx, dx, -dy, dy};
            double[] q = {a.x() - r.x(), r.right() - a.x(), a.y() - r.y(), r.bottom() - a.y()};
            boolean outside = false;
            for (int k = 0; k < 4 && !outside; k++) {
                if (p[k] == 0) {
                    outside = q[k] < 0;
                } else {
                    double t = q[k] / p[k];
                    if (p[k] < 0) {
                        t0 = Math.max(t0, t);
                    } else {
                        t1 = Math.min(t1, t);
                    }
                }
            }
            if (!outside && t0 < t1) {
                total += (t1 - t0) * Math.hypot(dx, dy);
            }
        }
        return total;
    }

    private static Rect labelRect(Point c, boolean horizontal, String opt, int w) {
        if (horizontal) {
            int x = c.x() - w / 2;
            return "d".equals(opt) ? new Rect(x, c.y() + DIST, w, TEXT_HEIGHT) : new Rect(x, c.y() - DIST - TEXT_HEIGHT, w, TEXT_HEIGHT);
        }
        int y = c.y() - TEXT_HEIGHT / 2;
        return "l".equals(opt) ? new Rect(c.x() - DIST - w, y, w, TEXT_HEIGHT) : new Rect(c.x() + DIST, y, w, TEXT_HEIGHT);
    }

    /** Zona aproximada donde UMLetino escribe la multiplicidad m1 (junto al primer punto). */
    private static Rect multiplicityArea(List<Point> pts, String text) {
        Point p = pts.get(0);
        int w = (int) Math.ceil(text.length() * CHAR_WIDTH) + 4;
        return new Rect(p.x() - 2, p.y() - 30, w + 6, 60);
    }

    static Point midpoint(List<Point> pts) {
        double total = 0;
        for (int i = 1; i < pts.size(); i++) {
            total += dist(pts.get(i - 1), pts.get(i));
        }
        double half = total / 2;
        for (int i = 1; i < pts.size(); i++) {
            double len = dist(pts.get(i - 1), pts.get(i));
            if (half <= len && len > 0) {
                double t = half / len;
                Point a = pts.get(i - 1);
                Point b = pts.get(i);
                return new Point((int) Math.round(a.x() + t * (b.x() - a.x())), (int) Math.round(a.y() + t * (b.y() - a.y())));
            }
            half -= len;
        }
        return pts.get(pts.size() - 1);
    }

    private static boolean isHorizontalAt(List<Point> pts) {
        double total = 0;
        double half = 0;
        for (int i = 1; i < pts.size(); i++) {
            total += dist(pts.get(i - 1), pts.get(i));
        }
        half = total / 2;
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1);
            Point b = pts.get(i);
            double len = dist(a, b);
            if (half <= len) {
                return Math.abs(b.x() - a.x()) > Math.abs(b.y() - a.y());
            }
            half -= len;
        }
        return false;
    }

    private static double dist(Point a, Point b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }

    private static long overlapArea(Rect a, Rect b) {
        long w = Math.min(a.right(), b.right()) - Math.max(a.x(), b.x());
        long h = Math.min(a.bottom(), b.bottom()) - Math.max(a.y(), b.y());
        return w > 0 && h > 0 ? w * h : 0;
    }
}
