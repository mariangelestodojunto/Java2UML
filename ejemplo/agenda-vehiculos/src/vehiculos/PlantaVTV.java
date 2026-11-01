package vehiculos;

public class PlantaVTV {
    private String nombre;
    private String domicilio;

    public boolean realizarRevision(Vehiculo vehiculo, Fecha fecha) {
        boolean aprobada = !vehiculo.estaEncendido() && vehiculo.estaDetenido();
        if (aprobada) {
            vehiculo.setFechaUltimaVTV(fecha);
        }
        return aprobada;
    }
}
