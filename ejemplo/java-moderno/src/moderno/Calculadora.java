package moderno;

public class Calculadora {
    private static final String AYUDA = """
            Calcula áreas de figuras.
            Usa pattern matching (Java 21).
            """;

    /** switch con patrones (Java 21) y record patterns. */
    public static String describir(Object o) {
        return switch (o) {
            case Circulo(double r) when r > 10 -> "círculo grande";
            case Circulo c -> "círculo de radio " + c.radio();
            case Figura f -> "figura de área " + f.area();
            default -> "desconocido";
        };
    }

    public double sumar(Object a, Object b) {
        var total = 0.0;
        if (a instanceof Figura fa) total += fa.area();
        if (b instanceof Figura fb) total += fb.area();
        return total;
    }
}
