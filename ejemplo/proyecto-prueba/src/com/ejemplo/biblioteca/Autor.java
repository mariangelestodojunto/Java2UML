package com.ejemplo.biblioteca;

public class Autor {
    private String nombre;
    private String nacionalidad;
    private Autor mentor;

    public Autor(String nombre, String nacionalidad) {
        this.nombre = nombre;
        this.nacionalidad = nacionalidad;
    }

    public void setMentor(Autor mentor) {
        this.mentor = mentor;
    }
}
