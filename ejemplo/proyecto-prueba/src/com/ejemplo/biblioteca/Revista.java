package com.ejemplo.biblioteca;

public class Revista extends Material {
    private int numero;

    public Revista(String titulo, int anio, int numero) {
        super(titulo, anio);
        this.numero = numero;
    }

    @Override
    public double calcularMulta(int diasDeAtraso) {
        return diasDeAtraso * 20.0;
    }
}
