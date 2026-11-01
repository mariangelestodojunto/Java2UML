package com.umlgen.layout;

/** Rectángulo en coordenadas absolutas del diagrama (píxeles con zoom 10 de UMLetino). */
public record Rect(int x, int y, int w, int h) {
    public int right() { return x + w; }
    public int bottom() { return y + h; }

    public boolean intersects(Rect o) {
        return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
    }

    public Rect translate(int dx, int dy) {
        return new Rect(x + dx, y + dy, w, h);
    }
}
