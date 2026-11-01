package com.ejemplo.biblioteca;

public class Libro extends Material {
    private Autor autor;
    private int paginas;
    private Genero genero;

    public Libro(String titulo, int anio, Autor autor, Genero genero) {
        super(titulo, anio);
        this.autor = autor;
        this.genero = genero;
    }

    @Override
    public double calcularMulta(int diasDeAtraso) {
        return diasDeAtraso * 50.0;
    }

    public Autor getAutor() {
        return autor;
    }
}
