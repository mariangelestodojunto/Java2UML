package com.umlgen.layout;

import com.umlgen.uxf.PanelTextFormatter;

import java.util.List;

/**
 * Estima el tamaño de una caja a partir de su texto, con margen para que nada quede cortado
 * con la fuente por defecto de UMLetino (zoom 10). Los tamaños se redondean a la grilla de 10 px.
 */
public final class BoxSizer {
    static final double CHAR_WIDTH = 6.5;
    static final int LINE_HEIGHT = 16;
    static final int SEPARATOR_HEIGHT = 8;
    static final int HORIZONTAL_PADDING = 22;
    static final int VERTICAL_PADDING = 16;
    static final int MIN_WIDTH = 120;
    static final int MIN_HEIGHT = 50;

    public int[] size(List<String> lines) {
        int maxChars = 0;
        int textLines = 0;
        int separators = 0;
        for (String l : lines) {
            if (l.equals(PanelTextFormatter.SEPARATOR)) {
                separators++;
            } else {
                textLines++;
                maxChars = Math.max(maxChars, visibleLength(l));
            }
        }
        int w = (int) Math.ceil(maxChars * CHAR_WIDTH) + HORIZONTAL_PADDING;
        int h = textLines * LINE_HEIGHT + separators * SEPARATOR_HEIGHT + VERTICAL_PADDING;
        return new int[]{roundUp(Math.max(w, MIN_WIDTH)), roundUp(Math.max(h, MIN_HEIGHT))};
    }

    /** Longitud sin los marcadores de formato de UMLetino (/itálica/, _subrayado_). */
    private static int visibleLength(String line) {
        String l = line;
        if (l.length() >= 2 && ((l.startsWith("/") && l.endsWith("/")) || (l.startsWith("_") && l.endsWith("_")))) {
            l = l.substring(1, l.length() - 1);
        }
        return l.length();
    }

    static int roundUp(int v) {
        return ((v + 9) / 10) * 10;
    }
}
