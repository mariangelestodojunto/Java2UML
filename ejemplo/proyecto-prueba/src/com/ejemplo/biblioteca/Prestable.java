package com.ejemplo.biblioteca;

/** Algo que puede prestarse a un socio. */
public interface Prestable {
    int DIAS_MAXIMOS = 14;

    boolean prestar(Socio socio);

    void devolver();

    default boolean estaDisponible() {
        return true;
    }
}
