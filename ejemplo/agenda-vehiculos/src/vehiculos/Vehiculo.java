package vehiculos;

public class Vehiculo {
    private final int VELOCIDAD_POR_DEFECTO = 100;
    private String color;
    private String marca;
    private String modelo;
    private boolean encedido;
    private int velocidadActual;
    private int velocidadMaxima;
    private Fecha fechaFabricacion;
    private Fecha fechaUltimaVTV;

    public Vehiculo() {
        this.fechaFabricacion = null;
        this.fechaUltimaVTV = null;
    }

    public Vehiculo(String color, String marca, String modelo, int velocidadMaxima, Fecha fechaFabricacion) {
        this.color = color;
        this.marca = marca;
        this.modelo = modelo;
        this.velocidadMaxima = velocidadMaxima;
        this.fechaFabricacion = fechaFabricacion;
        this.fechaUltimaVTV = null;
    }

    public Vehiculo(String marca, String modelo) {
        this.marca = marca;
        this.modelo = modelo;
        this.velocidadMaxima = VELOCIDAD_POR_DEFECTO;
    }

    public void setFechaUltimaVTV(Fecha fecha) {
        this.fechaUltimaVTV = fecha;
    }

    public boolean encender() { encedido = true; return true; }
    public boolean apagar() { encedido = false; return true; }
    public boolean acelerar(int cuanto) { velocidadActual += cuanto; return true; }
    public boolean frenar(int cuanto) { velocidadActual -= cuanto; return true; }
    public void tocarBocina() { System.out.println("Bocina"); }
    public boolean estaDetenido() { return velocidadActual == 0; }
    public boolean estaEncendido() { return encedido; }
    public void setVelocidadMaxima(int v) { velocidadMaxima = v; }
    public void mostrar() { System.out.println(this); }
    public boolean equals(Vehiculo otro) { return otro != null && marca.equals(otro.marca); }
    @Override
    public String toString() { return marca + " " + modelo; }
}
