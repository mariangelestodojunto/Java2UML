package com.ejemplo.biblioteca;

import java.time.LocalDate;
import java.util.Objects;

public class Prestamo {
    private Material material;
    private Socio socio;
    private LocalDate fechaInicio;
    private LocalDate fechaDevolucion;

    public Prestamo(Material material, Socio socio) {
        this.material = Objects.requireNonNull(material, "material obligatorio");
        this.socio = socio;
        this.fechaInicio = LocalDate.now();
    }

    public boolean estaVencido(LocalDate hoy) {
        return hoy.isAfter(fechaInicio.plusDays(Prestable.DIAS_MAXIMOS));
    }
}
