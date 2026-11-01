package vehiculos;

import java.util.ArrayList;

public class Agenda {
    private ArrayList<Persona> personas;

    public Agenda() { personas = new ArrayList<>(); }
    public void listarPersonas() { personas.forEach(System.out::println); }
    private Persona buscarPersona(String dni) {
        for (Persona p : personas) if (p.mismoDni(dni)) return p;
        return null;
    }
    public boolean agregarPersona(String dni, String nombre, String apellido, Fecha fecha) {
        return personas.add(new Persona(dni, nombre, apellido, fecha));
    }
    public Persona removerPersona(String dni) {
        Persona p = buscarPersona(dni);
        personas.remove(p);
        return p;
    }
    public boolean modificarFechaNacimiento(String dni, Fecha f) {
        Persona p = buscarPersona(dni);
        return p != null && p.setFechaNacimiento(f);
    }
    public Persona devolverUltimo() { return personas.isEmpty() ? null : personas.get(personas.size() - 1); }
    public void eliminarTodosElementosAMano() { personas.clear(); }
}
