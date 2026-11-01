package com.ejemplo.biblioteca.util;

public class Validador {
    public static boolean dniValido(String dni) {
        return dni != null && dni.matches("\\d{7,8}");
    }
}
