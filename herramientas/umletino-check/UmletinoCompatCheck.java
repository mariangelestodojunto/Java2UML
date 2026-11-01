import com.baselet.control.basics.geom.DimensionDouble;
import com.baselet.control.basics.geom.PointDouble;
import com.baselet.control.basics.geom.Rectangle;
import com.baselet.control.constants.SharedConstants;
import com.baselet.control.enums.AlignHorizontal;
import com.baselet.control.enums.ElementId;
import com.baselet.control.enums.FormatLabels;
import com.baselet.control.enums.LineType;
import com.baselet.control.StringStyle;
import com.baselet.diagram.draw.DrawFunction;
import com.baselet.diagram.draw.DrawHandler;
import com.baselet.diagram.draw.helper.ColorOwn;
import com.baselet.diagram.draw.helper.Style;
import com.baselet.element.NewGridElement;
import com.baselet.element.elementnew.ElementFactory;
import com.baselet.element.interfaces.Component;
import com.baselet.element.interfaces.DrawHandlerInterface;
import com.baselet.element.relation.Relation;
import com.baselet.element.sticking.StickableMap;
import com.baselet.element.sticking.Stickables;
import com.baselet.element.sticking.StickingPolygon;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Prueba de compatibilidad con UMLetino (herramienta de desarrollo, no forma parte de la aplicación).
 *
 * Carga un .uxf replicando paso a paso DiagramXmlParser de umlet-gwt (el código que ejecuta UMLetino en el
 * navegador) y crea cada elemento con ElementFactory/NewGridElement de umlet-elements 15.1 (código compartido
 * por UMLet y UMLetino). Luego:
 *   - informa cualquier elemento que UMLetino rechazaría ("Diagram has invalid element"),
 *   - verifica que los extremos de cada relación queden "pegados" (sticking) a una clase, como exige
 *     UMLetino para que las líneas sigan a las cajas al moverlas,
 *   - dibuja el diagrama en PNG con los comandos de dibujo reales generados por UMLet.
 *
 * Uso: java -cp umlet-elements:slf4j:. UmletinoCompatCheck entrada.uxf salida.png
 */
public class UmletinoCompatCheck {

    static final class Java2dDrawHandler extends DrawHandler {
        Graphics2D g;
        final FontRenderContext frc = new FontRenderContext(null, true, true);

        @Override
        protected DimensionDouble textDimensionHelper(StringStyle s) {
            Font f = font(style.getFontSize(), s);
            return new DimensionDouble(f.getStringBounds(s.getStringWithoutMarkup(), frc).getWidth(), style.getFontSize());
        }

        @Override
        protected double getDefaultFontSize() {
            return 12; // igual que DrawHandlerGwt (UMLetino)
        }

        static Font font(double size, StringStyle s) {
            int st = Font.PLAIN;
            if (s.getFormat().contains(FormatLabels.BOLD)) st |= Font.BOLD;
            if (s.getFormat().contains(FormatLabels.ITALIC)) st |= Font.ITALIC;
            return new Font(Font.SANS_SERIF, st, 1).deriveFont((float) size);
        }

        private void apply(Style s) {
            ColorOwn c = s.getForegroundColor();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()));
            float w = (float) Math.max(1, s.getLineWidth());
            if (s.getLineType() == LineType.DASHED) {
                g.setStroke(new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8, 5}, 0));
            } else if (s.getLineType() == LineType.DOTTED) {
                g.setStroke(new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{2, 3}, 0));
            } else {
                g.setStroke(new BasicStroke(w));
            }
        }

        private void fillThenStroke(java.awt.Shape shape, Style s) {
            ColorOwn bg = s.getBackgroundColor();
            if (bg != null && bg.getAlpha() > 0) {
                g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), bg.getAlpha()));
                g.fill(shape);
            }
            apply(s);
            if (s.getLineWidth() > 0) g.draw(shape);
        }

        private void add(Runnable r) {
            addDrawable(new DrawFunction() {
                @Override
                public void run() {
                    r.run();
                }
            });
        }

        @Override
        public void drawArc(double x, double y, double w, double h, double start, double extent, boolean open) {
            Style s = style.cloneFromMe();
            add(() -> fillThenStroke(new java.awt.geom.Arc2D.Double(x, y, w, h, start, extent,
                    open ? java.awt.geom.Arc2D.OPEN : java.awt.geom.Arc2D.PIE), s));
        }

        @Override
        public void drawCircle(double x, double y, double r) {
            Style s = style.cloneFromMe();
            add(() -> fillThenStroke(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r), s));
        }

        @Override
        public void drawEllipse(double x, double y, double w, double h) {
            Style s = style.cloneFromMe();
            add(() -> fillThenStroke(new java.awt.geom.Ellipse2D.Double(x, y, w, h), s));
        }

        @Override
        public void drawLines(PointDouble... pts) {
            if (pts.length < 2) return;
            Style s = style.cloneFromMe();
            PointDouble[] copy = pts.clone();
            add(() -> {
                Path2D.Double p = new Path2D.Double();
                p.moveTo(copy[0].getX(), copy[0].getY());
                for (int i = 1; i < copy.length; i++) p.lineTo(copy[i].getX(), copy[i].getY());
                boolean closed = copy[0].equals(copy[copy.length - 1]) && copy.length > 2;
                if (closed) {
                    p.closePath();
                    fillThenStroke(p, s);
                } else {
                    apply(s);
                    g.draw(p);
                }
            });
        }

        @Override
        public void drawRectangle(double x, double y, double w, double h) {
            Style s = style.cloneFromMe();
            add(() -> fillThenStroke(new java.awt.geom.Rectangle2D.Double(x, y, w, h), s));
        }

        @Override
        public void drawRectangleRound(double x, double y, double w, double h, double r) {
            Style s = style.cloneFromMe();
            add(() -> fillThenStroke(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, r, r), s));
        }

        @Override
        public void printHelper(StringStyle[] lines, PointDouble point, AlignHorizontal align) {
            Style s = style.cloneFromMe();
            double lineHeight = textHeightMax();
            add(() -> {
                double y = point.getY();
                for (StringStyle line : lines) {
                    String t = line.getStringWithoutMarkup();
                    Font f = font(s.getFontSize(), line);
                    g.setFont(f);
                    ColorOwn c = s.getForegroundColor();
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
                    double w = f.getStringBounds(t, frc).getWidth();
                    double x = align == AlignHorizontal.CENTER ? point.getX() - w / 2
                            : align == AlignHorizontal.RIGHT ? point.getX() - w : point.getX();
                    g.drawString(t, (float) x, (float) y);
                    if (line.getFormat().contains(FormatLabels.UNDERLINE)) {
                        g.setStroke(new BasicStroke(1));
                        g.drawLine((int) x, (int) y + 1, (int) (x + w), (int) y + 1);
                    }
                    y += lineHeight;
                }
            });
        }
    }

    static final class TestComponent implements Component {
        Rectangle rect;
        final Java2dDrawHandler drawer = new Java2dDrawHandler();
        final Java2dDrawHandler meta = new Java2dDrawHandler();

        public void setBoundsRect(Rectangle r) { rect = r; }
        public Rectangle getBoundsRect() { return rect.copy(); }
        public void repaintComponent() { }
        public DrawHandler getDrawHandler() { return drawer; }
        public DrawHandler getMetaDrawHandler() { return meta; }
        public void afterModelUpdate() { }
        public void translateForExport() { }
    }

    static final class Factory extends ElementFactory {
        static NewGridElement create(ElementId id, Rectangle rect, String panel, String additional) {
            NewGridElement element = createAssociatedGridElement(id);
            DrawHandlerInterface handler = new DrawHandlerInterface() {
                public void updatePropertyPanel() { }
                public int getGridSize() { return SharedConstants.DEFAULT_GRID_SIZE; }
                public boolean isInitialized() { return true; }
                public StickableMap getStickableMap() { return new StickableMap(); }
            };
            TestComponent c = new TestComponent();
            element.init(rect, panel, additional, c, handler);
            element.setPanelAttributes(panel);
            return element;
        }
    }

    public static void main(String[] args) throws Exception {
        String xml = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        if (xml.startsWith(SharedConstants.UTF8_BOM)) xml = xml.substring(1);
        Document dom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        int zoom = Integer.parseInt(dom.getElementsByTagName("zoom_level").item(0).getFirstChild().getNodeValue());
        List<NewGridElement> elements = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int invalid = 0;
        NodeList nodes = dom.getElementsByTagName("element");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            try { // ---- réplica exacta de DiagramXmlParser.xmlToDiagram (umlet-gwt 15.1)
                ElementId id = ElementId.valueOf(element.getElementsByTagName("id").item(0).getFirstChild().getNodeValue());
                Element coord = (Element) element.getElementsByTagName("coordinates").item(0);
                Rectangle rect = new Rectangle(getInt(coord, "x"), getInt(coord, "y"), getInt(coord, "w"), getInt(coord, "h"));
                String panel = "";
                Node panelNode = element.getElementsByTagName("panel_attributes").item(0).getFirstChild();
                if (panelNode != null) panel = panelNode.getNodeValue();
                String additional = "";
                Node addNode = element.getElementsByTagName("additional_attributes").item(0);
                if (addNode != null && addNode.getFirstChild() != null) additional = addNode.getFirstChild().getNodeValue();
                NewGridElement ge = Factory.create(id, rect, panel, additional);
                elements.add(ge);
                names.add(id == ElementId.UMLClass ? className(panel) : panel.replace('\n', ' '));
            } catch (Exception e) {
                invalid++;
                System.out.println("ELEMENTO INVÁLIDO (UMLetino mostraría 'Diagram has invalid element'): " + e);
            }
        }

        // ---- sticking: ¿los extremos de cada relación quedan unidos a una clase?
        int classes = 0, relations = 0, unattached = 0;
        List<StickingPolygon> polygons = new ArrayList<>();
        List<String> polygonNames = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).getId() == ElementId.UMLClass) {
                classes++;
                polygons.add(elements.get(i).generateStickingBorder());
                polygonNames.add(names.get(i));
            }
        }
        for (int i = 0; i < elements.size(); i++) {
            if (!(elements.get(i) instanceof Relation rel)) continue;
            relations++;
            List<String> attached = new ArrayList<>();
            var stickPts = new ArrayList<>(rel.getStickablePoints());
            int points = stickPts.size();
            int firstIdx = stickPts.get(0).getIndex();
            int lastIdx = stickPts.get(points - 1).getIndex();
            boolean firstOk = false, lastOk = false;
            for (int p = 0; p < polygons.size(); p++) {
                StickableMap m = Stickables.getStickingPointsWhichAreConnectedToStickingPolygon(polygons.get(p), List.of(rel));
                if (!m.isEmpty()) {
                    for (var idx : m.getStickablePoints(rel)) {
                        if (idx.getIndex() == firstIdx) firstOk = true;
                        if (idx.getIndex() == lastIdx) lastOk = true;
                        attached.add((idx.getIndex() == firstIdx ? "destino=" : "origen=") + polygonNames.get(p));
                    }
                }
            }
            if (!firstOk || !lastOk) unattached++;
            System.out.printf("Relación %-40s puntos=%d  %s%s%n", "[" + names.get(i) + "]", points, attached,
                    firstOk && lastOk ? "" : "   <-- EXTREMO SUELTO");
        }

        // ---- render
        int maxX = 0, maxY = 0;
        for (NewGridElement e : elements) {
            maxX = Math.max(maxX, e.getRectangle().getX2());
            maxY = Math.max(maxY, e.getRectangle().getY2());
        }
        BufferedImage img = new BufferedImage(maxX + 40, maxY + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (NewGridElement e : elements) {
            TestComponent c = (TestComponent) e.getComponent();
            Graphics2D eg = (Graphics2D) g.create();
            eg.translate(e.getRectangle().getX(), e.getRectangle().getY());
            c.drawer.g = eg;
            c.drawer.drawAll();
            eg.dispose();
        }
        g.dispose();
        ImageIO.write(img, "png", new File(args[1]));
        System.out.println();
        System.out.println("zoom_level=" + zoom + "  clases=" + classes + "  relaciones=" + relations
                + "  elementos inválidos=" + invalid + "  relaciones con extremos sueltos=" + unattached);
        System.out.println(invalid == 0 && unattached == 0 ? "RESULTADO: COMPATIBLE" : "RESULTADO: CON PROBLEMAS");
    }

    private static String className(String panel) {
        for (String l : panel.split("\n")) {
            if (!l.startsWith("<<")) return l;
        }
        return panel;
    }

    private static Integer getInt(Element coordinates, String tag) {
        return Integer.valueOf(coordinates.getElementsByTagName(tag).item(0).getFirstChild().getNodeValue());
    }
}
