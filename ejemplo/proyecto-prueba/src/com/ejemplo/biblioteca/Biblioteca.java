package com.ejemplo.biblioteca;

import java.util.ArrayList;

public class Biblioteca {
    public static final String VERSION = "1.0";
    private String nombre;
    private ArrayList<Material> catalogo = new ArrayList<>();
    private Socio[] socios;
    protected int cantidadSocios;

    public Biblioteca(String nombre, int maxSocios) {
        this.nombre = nombre;
        this.socios = new Socio[maxSocios];
    }

    public Prestamo registrarPrestamo(Socio socio, Material material) {
        if (!material.prestar(socio)) {
            return null;
        }
        Prestamo p = new Prestamo(material, socio);
        socio.agregarPrestamo(p);
        return p;
    }

    public void listarCatalogo() {
        GeneradorReportes.imprimir(catalogo);
    }

    private Material buscar(String titulo) {
        for (Material m : catalogo) {
            if (m.getTitulo().equals(titulo)) {
                return m;
            }
        }
        return null;
    }
}
