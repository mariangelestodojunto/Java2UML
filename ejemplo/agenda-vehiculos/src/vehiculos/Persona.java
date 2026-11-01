package vehiculos;

public class Persona {
    private String dni;
    private String nombre;
    private String apellido;
    private Fecha fechaNacimiento;
    private Vehiculo vehiculo;

    public Persona(String dni, String nombre, String apellido, Fecha fechaNacimiento) {
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.fechaNacimiento = fechaNacimiento;
        this.vehiculo = null;
    }

    @Override
    public String toString() { return nombre + " " + apellido; }
    public boolean mismoDni(String dni) { return this.dni.equals(dni); }
    public boolean setFechaNacimiento(Fecha f) {
        if (f == null) {
            return false;
        }
        fechaNacimiento = f;
        return true;
    }
    public void agregarVehiculo(Vehiculo vehiculo) { this.vehiculo = vehiculo; }
    public boolean tieneVehiculo() { return vehiculo != null; }
}
