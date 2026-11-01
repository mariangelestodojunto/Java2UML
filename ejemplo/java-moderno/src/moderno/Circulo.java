package moderno;

/** Record que implementa una interfaz sellada (Java 16/17). */
public record Circulo(double radio) implements Figura {
    public Circulo {
        if (radio < 0) throw new IllegalArgumentException("radio negativo");
    }

    @Override
    public double area() {
        return Math.PI * radio * radio;
    }
}
