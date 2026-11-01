package vehiculos;

/** Clase de ejecución: no debe aparecer en el diagrama. */
public class Test {
    public static void main(String[] args) {
        Agenda agenda = new Agenda();
        agenda.agregarPersona("123", "Ana", "Pérez", new Fecha(1, 1, 2000));
        Persona p = agenda.devolverUltimo();
        p.agregarVehiculo(new Vehiculo("Fiat", "Uno"));
        new PlantaVTV().realizarRevision(new Vehiculo(), new Fecha(2, 2, 2024));
        agenda.listarPersonas();
    }
}
