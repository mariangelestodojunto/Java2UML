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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Motor de distribución automática basado en el método de capas de Sugiyama:
 * <ol>
 *   <li>Se separan los componentes conexos; las clases sin relaciones van en una grilla al final.</li>
 *   <li>Se asignan capas: superclases/interfaces arriba de sus subclases; quien tiene el atributo arriba de la
 *       clase referenciada. Los ciclos se rompen sin invertir nunca una herencia.</li>
 *   <li>Las relaciones que cruzan varias capas pasan por nodos ficticios que reservan un "canal" vertical
 *       libre, de modo que las líneas no atraviesan cajas.</li>
 *   <li>El orden dentro de cada capa se optimiza (baricentro) para reducir cruces de líneas.</li>
 *   <li>Las coordenadas X acercan cada clase a sus relacionadas sin superposiciones.</li>
 * </ol>
 */
public final class LayoutEngine {
    static final int H_GAP = 90;
    static final int DUMMY_GAP = 40;
    static final int V_GAP = 130;
    static final int COMPONENT_GAP = 150;
    static final int ISOLATED_GAP = 70;
    static final int ISOLATED_TOP_GAP = 100;
    static final int MAX_ROW_WIDTH = 2400;
    static final int MIN_ISOLATED_ROW_WIDTH = 1400;
    static final int MARGIN = 20;
    static final int SELF_LOOP_REACH = 40;
    static final int SELF_LOOP_STEP = 20;

    // --------------------------------------------------------------------------------- estructuras internas

    private static final class Node {
        final UmlClassifier classifier; // null = nodo ficticio
        final int w;
        final int h;
        int extraRight;
        int layer;
        int order;
        double x;
        int y;
        final List<Seg> ups = new ArrayList<>();
        final List<Seg> downs = new ArrayList<>();
        /** Relaciones con otra caja de la misma capa: salen y llegan por el borde inferior. */
        final List<Seg> sameLayer = new ArrayList<>();

        Node(UmlClassifier c, int w, int h) {
            this.classifier = c;
            this.w = w;
            this.h = h;
        }

        boolean dummy() { return classifier == null; }
        double center() { return x + w / 2.0; }
        int span() { return w + extraRight; }
    }

    private static final class Seg {
        final Node upper;
        final Node lower;
        int upperPort;
        int lowerPort;

        Seg(Node upper, Node lower) {
            this.upper = upper;
            this.lower = lower;
        }
    }

    private static final class Chain {
        final UmlRelation relation;
        final List<Node> nodes = new ArrayList<>();
        final List<Seg> segs = new ArrayList<>();
        boolean targetIsUpper;
        /** Origen y destino quedaron en la misma capa (sólo dependencias). */
        boolean sameLayer;
        Node source;
        Node target;

        Chain(UmlRelation r) { this.relation = r; }
    }

    // --------------------------------------------------------------------------------- API

    public DiagramLayout layout(Collection<UmlClassifier> classifiers, List<UmlRelation> relations,
                                Map<UmlClassifier, int[]> sizes) {
        DiagramLayout result = new DiagramLayout();
        Map<UmlClassifier, Node> nodes = new LinkedHashMap<>();
        for (UmlClassifier c : classifiers) {
            int[] s = sizes.get(c);
            nodes.put(c, new Node(c, s[0], s[1]));
        }
        List<UmlRelation> drawable = new ArrayList<>();
        Map<Node, Integer> selfLoops = new HashMap<>();
        for (UmlRelation r : relations) {
            if (!nodes.containsKey(r.source()) || !nodes.containsKey(r.target())) {
                continue;
            }
            if (r.isSelfRelation()) {
                selfLoops.merge(nodes.get(r.source()), 1, Integer::sum);
            } else {
                drawable.add(r);
            }
        }
        selfLoops.forEach((n, count) -> n.extraRight = SELF_LOOP_REACH + SELF_LOOP_STEP * (count - 1) + 20);

        List<List<Node>> components = components(nodes, drawable);
        List<Node> isolated = new ArrayList<>();
        List<List<Node>> connected = new ArrayList<>();
        for (List<Node> comp : components) {
            if (comp.size() == 1) {
                isolated.add(comp.get(0));
            } else {
                connected.add(comp);
            }
        }
        connected.sort(Comparator.comparingInt((List<Node> l) -> -l.size()));

        Map<UmlRelation, List<Point>> routes = new LinkedHashMap<>();
        int cursorX = 0;
        int cursorY = 0;
        int rowHeight = 0;
        int widest = 0;
        for (List<Node> comp : connected) {
            Set<Node> members = new HashSet<>(comp);
            List<UmlRelation> compRelations = new ArrayList<>();
            for (UmlRelation r : drawable) {
                if (members.contains(nodes.get(r.source()))) {
                    compRelations.add(r);
                }
            }
            Map<UmlRelation, List<Point>> localRoutes = layoutComponent(comp, compRelations, nodes);
            int[] bounds = bounds(comp, localRoutes.values());
            int w = bounds[2] - bounds[0];
            int h = bounds[3] - bounds[1];
            if (cursorX > 0 && cursorX + w > MAX_ROW_WIDTH) {
                cursorX = 0;
                cursorY += rowHeight + COMPONENT_GAP;
                rowHeight = 0;
            }
            int dx = snap(cursorX - bounds[0]);
            int dy = snap(cursorY - bounds[1]);
            for (Node n : comp) {
                n.x += dx;
                n.y += dy;
            }
            for (Map.Entry<UmlRelation, List<Point>> e : localRoutes.entrySet()) {
                routes.put(e.getKey(), translate(e.getValue(), dx, dy));
            }
            cursorX += w + COMPONENT_GAP;
            widest = Math.max(widest, cursorX - COMPONENT_GAP);
            rowHeight = Math.max(rowHeight, h);
        }

        // clases sin relaciones (típicamente enums o interfaces sueltas): grilla al pie del diagrama
        isolated.sort(Comparator.comparingInt((Node n) -> kindRank(n.classifier.kind()))
                .thenComparing(n -> n.classifier.displayName()));
        int y = connected.isEmpty() ? 0 : cursorY + rowHeight + ISOLATED_TOP_GAP;
        int x = 0;
        int isoRowHeight = 0;
        int limit = Math.max(MIN_ISOLATED_ROW_WIDTH, widest);
        for (Node n : isolated) {
            if (x > 0 && x + n.span() > limit) {
                x = 0;
                y += isoRowHeight + ISOLATED_GAP;
                isoRowHeight = 0;
            }
            n.x = x;
            n.y = y;
            x += n.span() + ISOLATED_GAP;
            isoRowHeight = Math.max(isoRowHeight, n.h);
        }

        for (Node n : nodes.values()) {
            result.boxes().put(n.classifier, new Rect((int) Math.round(n.x) + MARGIN, n.y + MARGIN, n.w, n.h));
        }
        // rutas en el orden original de las relaciones
        Map<UmlClassifier, Integer> selfIndex = new HashMap<>();
        for (UmlRelation r : relations) {
            if (!nodes.containsKey(r.source()) || !nodes.containsKey(r.target())) {
                continue;
            }
            if (r.isSelfRelation()) {
                int i = selfIndex.merge(r.source(), 1, Integer::sum) - 1;
                result.routes().put(r, selfLoop(result.boxes().get(r.source()), i));
            } else if (routes.containsKey(r)) {
                result.routes().put(r, translate(routes.get(r), MARGIN, MARGIN));
            }
        }
        return result;
    }

    private static int kindRank(ClassifierKind k) {
        return switch (k) {
            case CLASS, ABSTRACT_CLASS -> 0;
            case INTERFACE -> 1;
            case ENUM -> 2;
        };
    }

    // --------------------------------------------------------------------------------- componentes conexos

    private static List<List<Node>> components(Map<UmlClassifier, Node> nodes, List<UmlRelation> rels) {
        Map<Node, Node> parent = new IdentityHashMap<>();
        for (Node n : nodes.values()) {
            parent.put(n, n);
        }
        for (UmlRelation r : rels) {
            Node a = find(parent, nodes.get(r.source()));
            Node b = find(parent, nodes.get(r.target()));
            if (a != b) {
                parent.put(a, b);
            }
        }
        Map<Node, List<Node>> groups = new LinkedHashMap<>();
        for (Node n : nodes.values()) {
            groups.computeIfAbsent(find(parent, n), k -> new ArrayList<>()).add(n);
        }
        return new ArrayList<>(groups.values());
    }

    private static Node find(Map<Node, Node> parent, Node n) {
        Node root = n;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }
        Node cur = n;
        while (parent.get(cur) != root) {
            Node next = parent.get(cur);
            parent.put(cur, root);
            cur = next;
        }
        return root;
    }

    // --------------------------------------------------------------------------------- un componente

    private Map<UmlRelation, List<Point>> layoutComponent(List<Node> comp, List<UmlRelation> rels,
                                                          Map<UmlClassifier, Node> nodeOf) {
        // 1. orientación de las relaciones (grafo acíclico)
        Map<Node, Set<Node>> succ = new IdentityHashMap<>();
        Map<Node, Set<Node>> pred = new IdentityHashMap<>();
        for (Node n : comp) {
            succ.put(n, new HashSet<>());
            pred.put(n, new HashSet<>());
        }
        List<UmlRelation> sorted = new ArrayList<>(rels);
        sorted.sort(Comparator.comparingInt(r -> r.kind().ordinal()));
        Set<Node> structural = new HashSet<>();
        for (UmlRelation r : sorted) {
            if (r.kind() != RelationKind.DEPENDENCY) {
                structural.add(nodeOf.get(r.source()));
                structural.add(nodeOf.get(r.target()));
            }
        }
        List<Chain> chains = new ArrayList<>();
        List<Chain> looseDependencies = new ArrayList<>();
        for (UmlRelation r : sorted) {
            Chain ch = new Chain(r);
            ch.source = nodeOf.get(r.source());
            ch.target = nodeOf.get(r.target());
            chains.add(ch);
            boolean constrains = r.kind() != RelationKind.DEPENDENCY
                    || !structural.contains(ch.source) || !structural.contains(ch.target);
            if (!constrains) {
                looseDependencies.add(ch); // se orienta después, según las capas obtenidas
                continue;
            }
            boolean hierarchy = r.kind() == RelationKind.GENERALIZATION || r.kind() == RelationKind.REALIZATION;
            Node upper = hierarchy ? ch.target : ch.source;
            Node lower = hierarchy ? ch.source : ch.target;
            if (reaches(succ, lower, upper)) {
                Node t = upper;
                upper = lower;
                lower = t;
            }
            succ.get(upper).add(lower);
            pred.get(lower).add(upper);
            ch.nodes.add(upper);
            ch.nodes.add(lower);
        }

        // 2. capas: camino más largo desde las raíces, luego se bajan las raíces junto a sus hijos
        List<Node> topo = topologicalOrder(comp, succ, pred);
        for (Node n : topo) {
            n.layer = 0;
            for (Node p : pred.get(n)) {
                n.layer = Math.max(n.layer, p.layer + 1);
            }
        }
        for (int i = topo.size() - 1; i >= 0; i--) {
            Node n = topo.get(i);
            if (pred.get(n).isEmpty() && !succ.get(n).isEmpty()) {
                int min = Integer.MAX_VALUE;
                for (Node s : succ.get(n)) {
                    min = Math.min(min, s.layer);
                }
                n.layer = Math.max(n.layer, min - 1);
            }
        }
        TreeSet<Integer> used = new TreeSet<>();
        comp.forEach(n -> used.add(n.layer));
        Map<Integer, Integer> compact = new HashMap<>();
        for (int l : used) {
            compact.put(l, compact.size());
        }
        comp.forEach(n -> n.layer = compact.get(n.layer));
        int layerCount = used.size();

        // 3. nodos ficticios para relaciones largas
        List<List<Node>> layers = new ArrayList<>();
        for (int i = 0; i < layerCount; i++) {
            layers.add(new ArrayList<>());
        }
        for (Node n : topo) {
            layers.get(n.layer).add(n);
        }
        for (Chain ch : looseDependencies) {
            if (ch.source.layer == ch.target.layer) {
                ch.sameLayer = true;
            } else {
                boolean sourceUp = ch.source.layer < ch.target.layer;
                ch.nodes.add(sourceUp ? ch.source : ch.target);
                ch.nodes.add(sourceUp ? ch.target : ch.source);
            }
        }
        for (Chain ch : chains) {
            if (ch.sameLayer) {
                Seg s = new Seg(ch.source, ch.target);
                ch.source.sameLayer.add(s);
                ch.target.sameLayer.add(s);
                ch.segs.add(s);
                continue;
            }
            Node upper = ch.nodes.get(0);
            Node lower = ch.nodes.get(1);
            ch.targetIsUpper = upper == ch.target;
            ch.nodes.clear();
            ch.nodes.add(upper);
            for (int l = upper.layer + 1; l < lower.layer; l++) {
                Node d = new Node(null, 0, 0);
                d.layer = l;
                layers.get(l).add(d);
                ch.nodes.add(d);
            }
            ch.nodes.add(lower);
            for (int i = 0; i + 1 < ch.nodes.size(); i++) {
                Seg s = new Seg(ch.nodes.get(i), ch.nodes.get(i + 1));
                s.upper.downs.add(s);
                s.lower.ups.add(s);
                ch.segs.add(s);
            }
        }

        // 4. orden dentro de cada capa (baricentro) minimizando cruces
        renumber(layers);
        List<List<Node>> best = snapshot(layers);
        int bestCrossings = crossings(layers);
        for (int iter = 0; iter < 16 && bestCrossings > 0; iter++) {
            if (iter % 2 == 0) {
                for (int l = 1; l < layerCount; l++) {
                    sortByBarycenter(layers.get(l), true);
                }
            } else {
                for (int l = layerCount - 2; l >= 0; l--) {
                    sortByBarycenter(layers.get(l), false);
                }
            }
            int c = crossings(layers);
            if (c < bestCrossings) {
                bestCrossings = c;
                best = snapshot(layers);
            }
        }
        for (int l = 0; l < layerCount; l++) {
            layers.get(l).clear();
            layers.get(l).addAll(best.get(l));
        }
        renumber(layers);

        // 5. coordenadas X
        assignX(layers);

        // 6. coordenadas Y (todas las cajas de una capa alineadas arriba)
        int[] layerTop = new int[layerCount];
        int[] layerBottom = new int[layerCount];
        int y = 0;
        for (int l = 0; l < layerCount; l++) {
            int height = 0;
            for (Node n : layers.get(l)) {
                height = Math.max(height, n.h);
            }
            layerTop[l] = y;
            layerBottom[l] = y + height;
            for (Node n : layers.get(l)) {
                n.y = y;
            }
            y += height + V_GAP;
        }

        // 7. puertos y recorridos
        assignPorts(layers);
        Map<UmlRelation, List<Point>> routes = new LinkedHashMap<>();
        int[] channelsUsed = new int[layerCount];
        for (Chain ch : chains) {
            if (ch.sameLayer) {
                int l = ch.source.layer;
                int channelY = layerBottom[l] + 20 + 12 * (channelsUsed[l]++ % 4);
                routes.put(ch.relation, sameLayerRoute(ch, channelY));
            } else {
                routes.put(ch.relation, route(ch, layerTop, layerBottom));
            }
        }
        return routes;
    }

    private static boolean reaches(Map<Node, Set<Node>> succ, Node from, Node to) {
        if (from == to) {
            return true;
        }
        Deque<Node> stack = new ArrayDeque<>();
        Set<Node> seen = new HashSet<>();
        stack.push(from);
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            if (n == to) {
                return true;
            }
            if (seen.add(n)) {
                stack.addAll(succ.get(n));
            }
        }
        return false;
    }

    private static List<Node> topologicalOrder(List<Node> comp, Map<Node, Set<Node>> succ, Map<Node, Set<Node>> pred) {
        Map<Node, Integer> indegree = new IdentityHashMap<>();
        Deque<Node> ready = new ArrayDeque<>();
        for (Node n : comp) {
            indegree.put(n, pred.get(n).size());
            if (pred.get(n).isEmpty()) {
                ready.add(n);
            }
        }
        List<Node> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            Node n = ready.poll();
            order.add(n);
            List<Node> next = new ArrayList<>(succ.get(n));
            next.sort(Comparator.comparing(m -> m.classifier.displayName()));
            for (Node s : next) {
                if (indegree.merge(s, -1, Integer::sum) == 0) {
                    ready.add(s);
                }
            }
        }
        return order;
    }

    private static void renumber(List<List<Node>> layers) {
        for (List<Node> layer : layers) {
            for (int i = 0; i < layer.size(); i++) {
                layer.get(i).order = i;
            }
        }
    }

    private static List<List<Node>> snapshot(List<List<Node>> layers) {
        List<List<Node>> copy = new ArrayList<>();
        for (List<Node> l : layers) {
            copy.add(new ArrayList<>(l));
        }
        return copy;
    }

    private static void sortByBarycenter(List<Node> layer, boolean useUpper) {
        Map<Node, Double> bary = new IdentityHashMap<>();
        for (Node n : layer) {
            List<Seg> segs = useUpper ? n.ups : n.downs;
            if (segs.isEmpty()) {
                bary.put(n, (double) n.order);
            } else {
                double sum = 0;
                for (Seg s : segs) {
                    sum += useUpper ? s.upper.order : s.lower.order;
                }
                bary.put(n, sum / segs.size());
            }
        }
        layer.sort(Comparator.comparingDouble(bary::get));
        for (int i = 0; i < layer.size(); i++) {
            layer.get(i).order = i;
        }
    }

    private static int crossings(List<List<Node>> layers) {
        int total = 0;
        for (int l = 0; l + 1 < layers.size(); l++) {
            List<int[]> segs = new ArrayList<>();
            for (Node n : layers.get(l)) {
                for (Seg s : n.downs) {
                    segs.add(new int[]{s.upper.order, s.lower.order});
                }
            }
            for (int i = 0; i < segs.size(); i++) {
                for (int j = i + 1; j < segs.size(); j++) {
                    int[] a = segs.get(i);
                    int[] b = segs.get(j);
                    if ((a[0] - b[0]) * (a[1] - b[1]) < 0) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    // --------------------------------------------------------------------------------- coordenadas X

    private static int gapBetween(Node a, Node b) {
        if (a.dummy() && b.dummy()) {
            return 20;
        }
        return a.dummy() || b.dummy() ? DUMMY_GAP : H_GAP;
    }

    private static void assignX(List<List<Node>> layers) {
        for (List<Node> layer : layers) {
            double x = 0;
            for (Node n : layer) {
                n.x = x;
                x += n.span() + H_GAP;
            }
        }
        // centrar las capas respecto de la más ancha
        double maxWidth = 0;
        for (List<Node> layer : layers) {
            maxWidth = Math.max(maxWidth, layerWidth(layer));
        }
        for (List<Node> layer : layers) {
            double offset = (maxWidth - layerWidth(layer)) / 2;
            layer.forEach(n -> n.x += offset);
        }
        for (int iter = 0; iter < 40; iter++) {
            boolean down = iter % 2 == 0;
            boolean both = iter >= 20;
            if (down) {
                for (int l = 0; l < layers.size(); l++) {
                    place(layers.get(l), true, both);
                }
            } else {
                for (int l = layers.size() - 1; l >= 0; l--) {
                    place(layers.get(l), false, both);
                }
            }
        }
        double minX = Double.MAX_VALUE;
        for (List<Node> layer : layers) {
            for (Node n : layer) {
                minX = Math.min(minX, n.x);
            }
        }
        for (List<Node> layer : layers) {
            int prevEnd = Integer.MIN_VALUE;
            Node prev = null;
            for (Node n : layer) {
                int x = snap((int) Math.round(n.x - minX));
                if (prev != null) {
                    x = Math.max(x, prevEnd + gapBetween(prev, n));
                }
                n.x = x;
                prevEnd = x + n.span();
                prev = n;
            }
        }
    }

    private static double layerWidth(List<Node> layer) {
        if (layer.isEmpty()) {
            return 0;
        }
        Node last = layer.get(layer.size() - 1);
        return last.x + last.span() - layer.get(0).x;
    }

    /** Acerca cada nodo al promedio de sus vecinos manteniendo el orden y la separación mínima. */
    private static void place(List<Node> layer, boolean fromUpper, boolean both) {
        int n = layer.size();
        double[] desired = new double[n];
        for (int i = 0; i < n; i++) {
            Node node = layer.get(i);
            double sum = 0;
            int count = 0;
            boolean useUp = fromUpper || both || node.dummy();
            boolean useDown = !fromUpper || both || node.dummy();
            if (useUp) {
                for (Seg s : node.ups) {
                    sum += s.upper.center();
                    count++;
                }
            }
            if (useDown) {
                for (Seg s : node.downs) {
                    sum += s.lower.center();
                    count++;
                }
            }
            desired[i] = count == 0 ? node.x : sum / count - node.w / 2.0;
        }
        double[] fwd = new double[n];
        double[] bwd = new double[n];
        for (int i = 0; i < n; i++) {
            fwd[i] = i == 0 ? desired[i]
                    : Math.max(desired[i], fwd[i - 1] + layer.get(i - 1).span() + gapBetween(layer.get(i - 1), layer.get(i)));
        }
        for (int i = n - 1; i >= 0; i--) {
            bwd[i] = i == n - 1 ? desired[i]
                    : Math.min(desired[i], bwd[i + 1] - gapBetween(layer.get(i), layer.get(i + 1)) - layer.get(i).span());
        }
        for (int i = 0; i < n; i++) {
            double x = (fwd[i] + bwd[i]) / 2;
            if (i > 0) {
                Node prev = layer.get(i - 1);
                x = Math.max(x, prev.x + prev.span() + gapBetween(prev, layer.get(i)));
            }
            layer.get(i).x = x;
        }
    }

    // --------------------------------------------------------------------------------- puertos y rutas

    private static void assignPorts(List<List<Node>> layers) {
        for (List<Node> layer : layers) {
            for (Node n : layer) {
                if (n.dummy()) {
                    int x = (int) Math.round(n.x);
                    n.ups.forEach(s -> s.lowerPort = x);
                    n.downs.forEach(s -> s.upperPort = x);
                } else {
                    List<Seg> bottom = new ArrayList<>(n.downs);
                    bottom.addAll(n.sameLayer);
                    distribute(n, bottom, false);
                    distribute(n, n.ups, true);
                }
            }
        }
    }

    /**
     * Reparte los puntos de conexión sobre un borde (superior o inferior) ordenados según la posición del
     * otro extremo, para que las líneas no se crucen al salir de la caja.
     */
    private static void distribute(Node n, List<Seg> segs, boolean top) {
        if (segs.isEmpty()) {
            return;
        }
        List<Seg> sorted = new ArrayList<>(segs);
        sorted.sort(Comparator.comparingDouble(s -> other(s, n).center()));
        int left = (int) Math.round(n.x);
        int k = sorted.size();
        int margin = Math.min(20, n.w / 6);
        for (int i = 0; i < k; i++) {
            Seg s = sorted.get(i);
            int port;
            if (k == 1) {
                double other = other(s, n).center();
                port = (int) Math.round(Math.max(left + margin, Math.min(left + n.w - margin, other)));
            } else {
                port = left + margin + (int) Math.round((n.w - 2.0 * margin) * i / (k - 1));
            }
            boolean isUpperEnd = s.upper == n && (!top || s.lower != n);
            if (top) {
                if (s.lower == n) {
                    s.lowerPort = port;
                } else {
                    s.upperPort = port;
                }
            } else if (isUpperEnd) {
                s.upperPort = port;
            } else {
                s.lowerPort = port;
            }
        }
    }

    private static Node other(Seg s, Node n) {
        return s.upper == n ? s.lower : s.upper;
    }

    private static List<Point> route(Chain ch, int[] layerTop, int[] layerBottom) {
        List<Point> pts = new ArrayList<>();
        Node first = ch.nodes.get(0);
        Seg s0 = ch.segs.get(0);
        pts.add(new Point(s0.upperPort, first.y + first.h));
        if (first.y + first.h < layerBottom[first.layer]) {
            pts.add(new Point(s0.upperPort, layerBottom[first.layer]));
        }
        for (int i = 1; i < ch.nodes.size(); i++) {
            Node n = ch.nodes.get(i);
            Seg in = ch.segs.get(i - 1);
            if (n.dummy()) {
                pts.add(new Point(in.lowerPort, layerTop[n.layer]));
                pts.add(new Point(in.lowerPort, layerBottom[n.layer]));
            } else {
                pts.add(new Point(in.lowerPort, n.y));
            }
        }
        List<Point> clean = simplify(pts);
        if (!ch.targetIsUpper) {
            java.util.Collections.reverse(clean);
        }
        return clean;
    }

    /** Relación entre cajas de la misma capa: baja, corre por el espacio libre debajo de la capa y sube. */
    private static List<Point> sameLayerRoute(Chain ch, int channelY) {
        Seg s = ch.segs.get(0);
        List<Point> pts = new ArrayList<>();
        pts.add(new Point(s.lowerPort, ch.target.y + ch.target.h));
        pts.add(new Point(s.lowerPort, channelY));
        pts.add(new Point(s.upperPort, channelY));
        pts.add(new Point(s.upperPort, ch.source.y + ch.source.h));
        return simplify(pts);
    }

    /** Quita puntos repetidos y puntos intermedios alineados. */
    static List<Point> simplify(List<Point> pts) {
        List<Point> out = new ArrayList<>();
        for (Point p : pts) {
            if (out.isEmpty() || !out.get(out.size() - 1).equals(p)) {
                out.add(p);
            }
        }
        int i = 1;
        while (i + 1 < out.size()) {
            Point a = out.get(i - 1);
            Point b = out.get(i);
            Point c = out.get(i + 1);
            long cross = (long) (b.x() - a.x()) * (c.y() - a.y()) - (long) (b.y() - a.y()) * (c.x() - a.x());
            if (cross == 0) {
                out.remove(i);
            } else {
                i++;
            }
        }
        if (out.size() == 1) {
            out.add(out.get(0).translate(0, 1));
        }
        return out;
    }

    /** Autoasociación: lazo sobre el lado derecho de la caja. */
    private static List<Point> selfLoop(Rect box, int index) {
        int reach = SELF_LOOP_REACH + SELF_LOOP_STEP * index;
        int y1 = box.y() + Math.min(15 + 12 * index, Math.max(5, box.h() / 2 - 10));
        int y2 = Math.min(box.bottom() - 5, y1 + 30);
        if (y2 <= y1) {
            y2 = y1 + 10;
        }
        List<Point> pts = new ArrayList<>();
        pts.add(new Point(box.right(), y1));
        pts.add(new Point(box.right() + reach, y1));
        pts.add(new Point(box.right() + reach, y2));
        pts.add(new Point(box.right(), y2));
        return pts;
    }

    // --------------------------------------------------------------------------------- utilidades

    private static int[] bounds(List<Node> comp, Collection<List<Point>> routes) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Node n : comp) {
            minX = Math.min(minX, (int) n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, (int) n.x + n.span());
            maxY = Math.max(maxY, n.y + n.h);
        }
        for (List<Point> r : routes) {
            for (Point p : r) {
                minX = Math.min(minX, p.x());
                maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y());
                maxY = Math.max(maxY, p.y());
            }
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    private static List<Point> translate(List<Point> pts, int dx, int dy) {
        List<Point> out = new ArrayList<>(pts.size());
        for (Point p : pts) {
            out.add(p.translate(dx, dy));
        }
        return out;
    }

    private static int snap(int v) {
        return Math.round(v / 10f) * 10;
    }
}
