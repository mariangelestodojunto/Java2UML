package com.umlgen.layout;

import com.umlgen.model.ClassifierKind;
import com.umlgen.model.RelationKind;
import com.umlgen.model.UmlClassifier;
import com.umlgen.model.UmlRelation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Distribución en cuadrícula 2D (p. ej. Agenda | Persona | Fecha arriba y Vehiculo | PlantaVTV abajo).
 *
 * <ol>
 *   <li>Cada clase relacionada ocupa una celda de una cuadrícula. La ubicación se optimiza (recocido simulado,
 *       con semilla fija para que el resultado sea siempre el mismo) minimizando: la longitud de las relaciones,
 *       las líneas que pasarían por encima de otra caja, los cruces entre líneas y, con menor peso, que el
 *       contenido quede a la izquierda/arriba de su contenedor y que una superclase no quede arriba de su
 *       subclase.</li>
 *   <li>Las filas y columnas toman el tamaño de su caja más grande; las cajas se centran en su celda.</li>
 *   <li>Las relaciones son líneas rectas de borde a borde (las paralelas entre las mismas dos clases se
 *       separan). Si una recta atravesara otra caja se prueba un recorrido en L.</li>
 *   <li>Las clases sin relaciones van en una fila al pie del diagrama.</li>
 * </ol>
 */
public final class GridLayoutEngine {
    static final int H_GAP = 130;
    static final int V_GAP = 110;
    static final int ISOLATED_TOP_GAP = 100;
    static final int ISOLATED_GAP = 70;
    static final int MARGIN = 20;
    static final int PARALLEL_SEPARATION = 26;

    // pesos de la función de costo (en "celdas")
    static final double W_THROUGH_BOX = 12;
    static final double W_CROSSING = 3;
    static final double W_HIERARCHY = 4;
    /** Preferencia de lectura: contenedor a la izquierda y contenido a la derecha. */
    static final double W_READING_HORIZONTAL = 0.2;
    static final double W_READING_VERTICAL = 0.05;
    /** Las relaciones horizontales cuestan un poco menos: los diagramas se leen de izquierda a derecha. */
    static final double HORIZONTAL_FACTOR = 0.9;
    /** Desempate: las clases con más relaciones (el eje del diagrama) van más arriba. */
    static final double W_CONNECTED_ON_TOP = 0.012;

    private record Edge(int a, int b, double weight, boolean hierarchy) {
    }

    public DiagramLayout layout(Collection<UmlClassifier> classifiers, List<UmlRelation> relations,
                                Map<UmlClassifier, int[]> sizes) {
        List<UmlClassifier> all = new ArrayList<>(classifiers);
        Map<UmlClassifier, Integer> index = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            index.put(all.get(i), i);
        }
        // nodos conectados (por relaciones que no son autoasociaciones)
        Set<Integer> connectedSet = new HashSet<>();
        Map<String, Edge> edgeByPair = new LinkedHashMap<>();
        for (UmlRelation r : relations) {
            Integer s = index.get(r.source());
            Integer t = index.get(r.target());
            if (s == null || t == null || s.equals(t)) {
                continue;
            }
            connectedSet.add(s);
            connectedSet.add(t);
            boolean hierarchy = r.kind() == RelationKind.GENERALIZATION || r.kind() == RelationKind.REALIZATION;
            double w = r.kind() == RelationKind.DEPENDENCY ? 0.7 : 1.0;
            // para jerarquías: a = padre (arriba), b = hijo; para el resto: a = origen, b = destino
            int a = hierarchy ? t : s;
            int b = hierarchy ? s : t;
            String key = Math.min(a, b) + "-" + Math.max(a, b);
            Edge prev = edgeByPair.get(key);
            if (prev == null || (hierarchy && !prev.hierarchy())) {
                edgeByPair.put(key, new Edge(a, b, prev == null ? w : prev.weight() + w * 0.3, hierarchy));
            } else {
                edgeByPair.put(key, new Edge(prev.a(), prev.b(), prev.weight() + w * 0.3, prev.hierarchy()));
            }
        }
        List<Integer> connected = new ArrayList<>(connectedSet);
        connected.sort(Comparator.naturalOrder());
        List<Edge> edges = new ArrayList<>(edgeByPair.values());

        DiagramLayout result = new DiagramLayout();
        Map<UmlClassifier, Rect> boxes = new LinkedHashMap<>();
        int bottom = 0;
        int right = 0;
        if (!connected.isEmpty()) {
            int[][] cells = optimize(connected, edges, all.size());
            // cells[i] = {fila, columna} de connected.get(i)
            Map<Integer, int[]> cellOf = new HashMap<>();
            for (int i = 0; i < connected.size(); i++) {
                cellOf.put(connected.get(i), cells[i]);
            }
            compact(cellOf);
            int rows = 0;
            int cols = 0;
            for (int[] c : cellOf.values()) {
                rows = Math.max(rows, c[0] + 1);
                cols = Math.max(cols, c[1] + 1);
            }
            int[] colWidth = new int[cols];
            int[] rowHeight = new int[rows];
            for (Map.Entry<Integer, int[]> e : cellOf.entrySet()) {
                int[] size = sizes.get(all.get(e.getKey()));
                int extra = hasSelfLoop(all.get(e.getKey()), relations) ? 70 : 0;
                colWidth[e.getValue()[1]] = Math.max(colWidth[e.getValue()[1]], size[0] + extra);
                rowHeight[e.getValue()[0]] = Math.max(rowHeight[e.getValue()[0]], size[1]);
            }
            int[] colX = new int[cols];
            int[] rowY = new int[rows];
            for (int c = 1; c < cols; c++) {
                colX[c] = colX[c - 1] + colWidth[c - 1] + H_GAP;
            }
            for (int r = 1; r < rows; r++) {
                rowY[r] = rowY[r - 1] + rowHeight[r - 1] + V_GAP;
            }
            for (Map.Entry<Integer, int[]> e : cellOf.entrySet()) {
                UmlClassifier c = all.get(e.getKey());
                int[] size = sizes.get(c);
                int extra = hasSelfLoop(c, relations) ? 70 : 0;
                int x = colX[e.getValue()[1]] + (colWidth[e.getValue()[1]] - extra - size[0]) / 2;
                int y = rowY[e.getValue()[0]] + (rowHeight[e.getValue()[0]] - size[1]) / 2;
                Rect box = new Rect(snap(x) + MARGIN, snap(y) + MARGIN, size[0], size[1]);
                boxes.put(c, box);
                bottom = Math.max(bottom, box.bottom());
                right = Math.max(right, box.right());
            }
        }

        // clases sin relaciones: fila al pie (clases, interfaces, enums)
        List<UmlClassifier> isolated = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (!connectedSet.contains(i)) {
                isolated.add(all.get(i));
            }
        }
        isolated.sort(Comparator.comparingInt((UmlClassifier c) -> kindRank(c.kind())).thenComparing(UmlClassifier::displayName));
        int limit = Math.max(1200, right);
        int x = MARGIN;
        int y = boxes.isEmpty() ? MARGIN : snap(bottom + ISOLATED_TOP_GAP);
        int rowH = 0;
        for (UmlClassifier c : isolated) {
            int[] size = sizes.get(c);
            int extra = hasSelfLoop(c, relations) ? 70 : 0;
            if (x > MARGIN && x + size[0] + extra > limit) {
                x = MARGIN;
                y = snap(y + rowH + ISOLATED_GAP);
                rowH = 0;
            }
            boxes.put(c, new Rect(x, y, size[0], size[1]));
            x = snap(x + size[0] + extra + ISOLATED_GAP);
            rowH = Math.max(rowH, size[1]);
        }
        for (UmlClassifier c : classifiers) {
            result.boxes().put(c, boxes.get(c));
        }
        route(result, relations);
        return result;
    }

    private static int kindRank(ClassifierKind k) {
        return switch (k) {
            case CLASS, ABSTRACT_CLASS -> 0;
            case INTERFACE -> 1;
            case ENUM -> 2;
        };
    }

    private static boolean hasSelfLoop(UmlClassifier c, List<UmlRelation> relations) {
        for (UmlRelation r : relations) {
            if (r.source() == c && r.target() == c) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ optimización

    /** Devuelve {fila, columna} para cada nodo conectado. */
    private int[][] optimize(List<Integer> nodes, List<Edge> edgeList, int totalNodes) {
        int n = nodes.size();
        Map<Integer, Integer> local = new HashMap<>();
        for (int i = 0; i < n; i++) {
            local.put(nodes.get(i), i);
        }
        int[][] edges = new int[edgeList.size()][];
        double[] weights = new double[edgeList.size()];
        boolean[] hierarchy = new boolean[edgeList.size()];
        for (int i = 0; i < edgeList.size(); i++) {
            Edge e = edgeList.get(i);
            edges[i] = new int[]{local.get(e.a()), local.get(e.b())};
            weights[i] = e.weight();
            hierarchy[i] = e.hierarchy();
        }
        int cols = (int) Math.ceil(Math.sqrt(n * 1.6)) + 1;
        int rows = (int) Math.ceil(n / (double) (cols - 1)) + 1;
        int[] row = new int[n];
        int[] col = new int[n];
        int[][] grid = new int[rows][cols];
        for (int[] g : grid) {
            java.util.Arrays.fill(g, -1);
        }
        initialPlacement(n, edges, rows, cols, row, col, grid);

        Model m = new Model(n, edges, weights, hierarchy, row, col);
        double cost = m.cost();
        int[] bestRow = row.clone();
        int[] bestCol = col.clone();
        double best = cost;
        Random random = new Random(42);
        long work = (long) edges.length * edges.length + (long) edges.length * n + n + 1;
        int iterations = (int) Math.max(3000, Math.min(60000, 60_000_000L / work));
        double t0 = 3.0;
        double t1 = 0.01;
        for (int it = 0; it < iterations; it++) {
            double temp = t0 * Math.pow(t1 / t0, it / (double) iterations);
            int v = random.nextInt(n);
            int nr = random.nextInt(rows);
            int nc = random.nextInt(cols);
            if (nr == row[v] && nc == col[v]) {
                continue;
            }
            int other = grid[nr][nc];
            int or = row[v];
            int oc = col[v];
            // mover (o intercambiar con el ocupante)
            grid[or][oc] = other;
            grid[nr][nc] = v;
            row[v] = nr;
            col[v] = nc;
            if (other >= 0) {
                row[other] = or;
                col[other] = oc;
            }
            double newCost = m.cost();
            double delta = newCost - cost;
            if (delta <= 0 || random.nextDouble() < Math.exp(-delta / temp)) {
                cost = newCost;
                if (cost < best - 1e-9) {
                    best = cost;
                    bestRow = row.clone();
                    bestCol = col.clone();
                }
            } else { // deshacer
                grid[nr][nc] = other;
                grid[or][oc] = v;
                row[v] = or;
                col[v] = oc;
                if (other >= 0) {
                    row[other] = nr;
                    col[other] = nc;
                }
            }
        }
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            out[i] = new int[]{bestRow[i], bestCol[i]};
        }
        return out;
    }

    /** Ubicación inicial: recorrido en anchura desde la clase con más relaciones, en espiral desde el centro. */
    private static void initialPlacement(int n, int[][] edges, int rows, int cols, int[] row, int[] col, int[][] grid) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int[] e : edges) {
            adj.get(e[0]).add(e[1]);
            adj.get(e[1]).add(e[0]);
        }
        Integer[] byDegree = new Integer[n];
        for (int i = 0; i < n; i++) {
            byDegree[i] = i;
        }
        java.util.Arrays.sort(byDegree, (x, y) -> adj.get(y).size() - adj.get(x).size());
        List<int[]> spiral = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                spiral.add(new int[]{r, c});
            }
        }
        double cr = (rows - 1) / 2.0;
        double cc = (cols - 1) / 2.0;
        spiral.sort(Comparator.comparingDouble(p -> Math.hypot(p[0] - cr, (p[1] - cc) * 0.8)));
        boolean[] placed = new boolean[n];
        int slot = 0;
        for (Integer start : byDegree) {
            if (placed[start]) {
                continue;
            }
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            placed[start] = true;
            while (!queue.isEmpty()) {
                int v = queue.poll();
                int[] cell = spiral.get(slot++);
                row[v] = cell[0];
                col[v] = cell[1];
                grid[cell[0]][cell[1]] = v;
                for (int w : adj.get(v)) {
                    if (!placed[w]) {
                        placed[w] = true;
                        queue.add(w);
                    }
                }
            }
        }
    }

    /** Función de costo de una ubicación. */
    private static final class Model {
        final int n;
        final int[][] edges;
        final double[] weights;
        final boolean[] hierarchy;
        final int[] row;
        final int[] col;
        final int[] degree;

        Model(int n, int[][] edges, double[] weights, boolean[] hierarchy, int[] row, int[] col) {
            this.n = n;
            this.edges = edges;
            this.weights = weights;
            this.hierarchy = hierarchy;
            this.row = row;
            this.col = col;
            this.degree = new int[n];
            for (int[] e : edges) {
                degree[e[0]]++;
                degree[e[1]]++;
            }
        }

        double cost() {
            double total = 0;
            for (int v = 0; v < n; v++) {
                total += W_CONNECTED_ON_TOP * row[v] * degree[v];
            }
            for (int i = 0; i < edges.length; i++) {
                int a = edges[i][0];
                int b = edges[i][1];
                double dr = row[b] - row[a];
                double dc = col[b] - col[a];
                total += weights[i] * Math.hypot(dr, dc * HORIZONTAL_FACTOR);
                total += W_THROUGH_BOX * boxesCrossed(a, b);
                if (hierarchy[i]) {
                    if (row[a] >= row[b]) { // el padre (a) debe quedar arriba del hijo (b)
                        total += W_HIERARCHY * (row[a] - row[b] + 1);
                    }
                } else if (weights[i] >= 1.0) { // sólo asociaciones: las dependencias no tienen sentido preferido
                    if (dc < 0) {
                        total += W_READING_HORIZONTAL; // el contenido preferentemente a la derecha
                    }
                    if (dr < 0) {
                        total += W_READING_VERTICAL; // ... o abajo
                    }
                }
            }
            for (int i = 0; i < edges.length; i++) {
                for (int j = i + 1; j < edges.length; j++) {
                    if (cross(edges[i], edges[j])) {
                        total += W_CROSSING;
                    }
                }
            }
            return total;
        }

        /** Cantidad de cajas por cuyo interior pasaría la recta entre los centros de a y b. */
        int boxesCrossed(int a, int b) {
            int count = 0;
            double ar = row[a], ac = col[a], br = row[b], bc = col[b];
            for (int k = 0; k < n; k++) {
                if (k == a || k == b) {
                    continue;
                }
                double kr = row[k], kc = col[k];
                if (kr < Math.min(ar, br) - 0.5 || kr > Math.max(ar, br) + 0.5
                        || kc < Math.min(ac, bc) - 0.5 || kc > Math.max(ac, bc) + 0.5) {
                    continue;
                }
                if (segmentHitsSquare(ac, ar, bc, br, kc, kr, 0.36)) {
                    count++;
                }
            }
            return count;
        }

        boolean cross(int[] e, int[] f) {
            if (e[0] == f[0] || e[0] == f[1] || e[1] == f[0] || e[1] == f[1]) {
                return false;
            }
            return segmentsIntersect(col[e[0]], row[e[0]], col[e[1]], row[e[1]],
                    col[f[0]], row[f[0]], col[f[1]], row[f[1]]);
        }
    }

    static boolean segmentHitsSquare(double x1, double y1, double x2, double y2, double cx, double cy, double half) {
        double t0 = 0, t1 = 1;
        double dx = x2 - x1, dy = y2 - y1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 - (cx - half), (cx + half) - x1, y1 - (cy - half), (cy + half) - y1};
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

    static boolean segmentsIntersect(double x1, double y1, double x2, double y2,
                                     double x3, double y3, double x4, double y4) {
        double d1 = orient(x3, y3, x4, y4, x1, y1);
        double d2 = orient(x3, y3, x4, y4, x2, y2);
        double d3 = orient(x1, y1, x2, y2, x3, y3);
        double d4 = orient(x1, y1, x2, y2, x4, y4);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double orient(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    /** Elimina filas y columnas vacías. */
    private static void compact(Map<Integer, int[]> cellOf) {
        for (int axis = 0; axis < 2; axis++) {
            Set<Integer> used = new java.util.TreeSet<>();
            for (int[] c : cellOf.values()) {
                used.add(c[axis]);
            }
            Map<Integer, Integer> remap = new HashMap<>();
            for (int u : used) {
                remap.put(u, remap.size());
            }
            for (int[] c : cellOf.values()) {
                c[axis] = remap.get(c[axis]);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ recorridos

    private static void route(DiagramLayout layout, List<UmlRelation> relations) {
        Map<String, List<UmlRelation>> byPair = new LinkedHashMap<>();
        Map<UmlClassifier, Integer> selfCount = new HashMap<>();
        for (UmlRelation r : relations) {
            if (!layout.boxes().containsKey(r.source()) || !layout.boxes().containsKey(r.target())) {
                continue;
            }
            if (r.isSelfRelation()) {
                continue;
            }
            String a = r.source().qualifiedName();
            String b = r.target().qualifiedName();
            String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
            byPair.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        Map<UmlRelation, List<Point>> computed = new HashMap<>();
        for (List<UmlRelation> group : byPair.values()) {
            UmlClassifier first = group.get(0).source();
            for (int k = 0; k < group.size(); k++) {
                UmlRelation r = group.get(k);
                double offset = (k - (group.size() - 1) / 2.0) * PARALLEL_SEPARATION;
                // el desplazamiento se mide siempre respecto del mismo sentido para que no se superpongan
                if (r.source() != first) {
                    offset = -offset;
                }
                computed.put(r, routeOne(layout, r, offset));
            }
        }
        for (UmlRelation r : relations) {
            if (!layout.boxes().containsKey(r.source()) || !layout.boxes().containsKey(r.target())) {
                continue;
            }
            if (r.isSelfRelation()) {
                int i = selfCount.merge(r.source(), 1, Integer::sum) - 1;
                layout.routes().put(r, selfLoop(layout.boxes().get(r.source()), i));
            } else {
                layout.routes().put(r, computed.get(r));
            }
        }
    }

    /** Recta de borde a borde (destino primero); si atraviesa otra caja, recorrido en L. */
    private static List<Point> routeOne(DiagramLayout layout, UmlRelation r, double offset) {
        Rect s = layout.boxes().get(r.source());
        Rect t = layout.boxes().get(r.target());
        double sx = s.x() + s.w() / 2.0, sy = s.y() + s.h() / 2.0;
        double tx = t.x() + t.w() / 2.0, ty = t.y() + t.h() / 2.0;
        double len = Math.hypot(tx - sx, ty - sy);
        double nx = len == 0 ? 0 : -(ty - sy) / len;
        double ny = len == 0 ? 0 : (tx - sx) / len;
        double ox = nx * offset, oy = ny * offset;
        double[] sc = {clamp(sx + ox, s.x() + 8, s.right() - 8), clamp(sy + oy, s.y() + 8, s.bottom() - 8)};
        double[] tc = {clamp(tx + ox, t.x() + 8, t.right() - 8), clamp(ty + oy, t.y() + 8, t.bottom() - 8)};
        Point ps = exit(s, sc[0], sc[1], tc[0], tc[1]);
        Point pt = exit(t, tc[0], tc[1], sc[0], sc[1]);
        List<Point> straight = List.of(pt, ps);
        if (!hitsOtherBoxes(layout, straight, r)) {
            return straight;
        }
        // recorridos en L: primero vertical y después horizontal, o al revés
        for (int variant = 0; variant < 2; variant++) {
            Point corner = variant == 0 ? new Point((int) Math.round(sc[0]), (int) Math.round(tc[1]))
                    : new Point((int) Math.round(tc[0]), (int) Math.round(sc[1]));
            if (inside(s, corner) || inside(t, corner)) {
                continue;
            }
            Point a = exit(s, sc[0], sc[1], corner.x(), corner.y());
            Point b = exit(t, tc[0], tc[1], corner.x(), corner.y());
            List<Point> l = List.of(b, corner, a);
            if (!hitsOtherBoxes(layout, l, r)) {
                return l;
            }
        }
        return straight;
    }

    private static boolean inside(Rect r, Point p) {
        return p.x() > r.x() && p.x() < r.right() && p.y() > r.y() && p.y() < r.bottom();
    }

    private static double clamp(double v, double lo, double hi) {
        return lo > hi ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, v));
    }

    /** Punto donde la semirrecta desde (x0,y0) hacia (x1,y1) sale de la caja. */
    private static Point exit(Rect b, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double t = Double.MAX_VALUE;
        if (dx > 0) t = Math.min(t, (b.right() - x0) / dx);
        if (dx < 0) t = Math.min(t, (b.x() - x0) / dx);
        if (dy > 0) t = Math.min(t, (b.bottom() - y0) / dy);
        if (dy < 0) t = Math.min(t, (b.y() - y0) / dy);
        if (t == Double.MAX_VALUE) {
            t = 0;
        }
        return new Point((int) Math.round(x0 + dx * t), (int) Math.round(y0 + dy * t));
    }

    private static boolean hitsOtherBoxes(DiagramLayout layout, List<Point> pts, UmlRelation r) {
        for (Map.Entry<UmlClassifier, Rect> e : layout.boxes().entrySet()) {
            if (e.getKey() == r.source() || e.getKey() == r.target()) {
                continue;
            }
            Rect b = e.getValue();
            for (int i = 0; i + 1 < pts.size(); i++) {
                Point p = pts.get(i);
                Point q = pts.get(i + 1);
                if (rectHit(p, q, b)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean rectHit(Point p, Point q, Rect b) {
        double t0 = 0, t1 = 1;
        double dx = q.x() - p.x(), dy = q.y() - p.y();
        double[] pp = {-dx, dx, -dy, dy};
        double[] qq = {p.x() - (b.x() - 4), (b.right() + 4) - p.x(), p.y() - (b.y() - 4), (b.bottom() + 4) - p.y()};
        for (int i = 0; i < 4; i++) {
            if (pp[i] == 0) {
                if (qq[i] < 0) {
                    return false;
                }
            } else {
                double t = qq[i] / pp[i];
                if (pp[i] < 0) {
                    t0 = Math.max(t0, t);
                } else {
                    t1 = Math.min(t1, t);
                }
            }
        }
        return t0 < t1;
    }

    /** Autoasociación: lazo sobre el lado derecho de la caja. */
    static List<Point> selfLoop(Rect box, int index) {
        int reach = 40 + 20 * index;
        int y1 = box.y() + Math.min(15 + 12 * index, Math.max(5, box.h() / 2 - 10));
        int y2 = Math.min(box.bottom() - 5, y1 + 30);
        if (y2 <= y1) {
            y2 = y1 + 10;
        }
        return List.of(new Point(box.right(), y1), new Point(box.right() + reach, y1),
                new Point(box.right() + reach, y2), new Point(box.right(), y2));
    }

    private static int snap(int v) {
        return Math.round(v / 10f) * 10;
    }
}
