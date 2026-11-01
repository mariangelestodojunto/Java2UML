package com.umlgen.layout;

/**
 * Ubicación del texto central de una relación en UMLetino: "cbase" (l = izquierda, d = abajo; null = lado por
 * defecto) y "cpos" (desplazamiento vertical en píxeles; 0 = sin desplazamiento).
 */
public record LabelPlacement(String side, int dy) {
    public static final LabelPlacement DEFAULT = new LabelPlacement(null, 0);

    public boolean isDefault() {
        return side == null && dy == 0;
    }
}
