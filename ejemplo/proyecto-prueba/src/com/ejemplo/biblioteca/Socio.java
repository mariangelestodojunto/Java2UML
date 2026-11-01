package com.ejemplo.biblioteca;

import java.util.ArrayList;
import java.util.List;

import com.ejemplo.biblioteca.util.Validador;

public class Socio {
    private final String dni;
    private String nombre;
    private List<Prestamo> prestamos;

    public Socio(String dni, String nombre) {
        if (!Validador.dniValido(dni)) {
            throw new IllegalArgumentException("DNI inválido");
        }
        this.dni = dni;
        this.nombre = nombre;
        this.prestamos = new ArrayList<>();
    }

    public void agregarPrestamo(Prestamo p) {
        prestamos.add(p);
    }

    public int cantidadPrestamos() {
        return prestamos.size();
    }
}
