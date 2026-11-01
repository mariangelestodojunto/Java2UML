package com.ejemplo.biblioteca;

import java.util.List;

public class GeneradorReportes {
    public static void imprimir(List<Material> materiales) {
        for (Material m : materiales) {
            System.out.println(m.getTitulo());
        }
    }
}
