package especial;

import java.util.List;

/** Interfaz genérica: T no es una clase del proyecto. */
public interface Repositorio<T> {
    void guardar(T elemento);
    List<T> listar();
}
