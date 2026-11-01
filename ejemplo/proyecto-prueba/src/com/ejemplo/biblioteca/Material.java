package com.ejemplo.biblioteca;

public abstract class Material implements Prestable {
    protected String titulo;
    private int anio;
    String codigoInterno;
    private boolean prestado;

    public Material(String titulo, int anio) {
        this.titulo = titulo;
        this.anio = anio;
    }

    @Override
    public boolean prestar(Socio socio) {
        if (prestado || socio == null) {
            return false;
        }
        prestado = true;
        return true;
    }

    @Override
    public void devolver() {
        prestado = false;
    }

    public abstract double calcularMulta(int diasDeAtraso);

    public String getTitulo() {
        return titulo;
    }
}
