package moderno;

/** Interfaz sellada (Java 17). */
public sealed interface Figura permits Circulo, Cuadrado {
    double area();
}
