package especial;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Cliente {
    private String nombre;
    private Map<String, Pedido> pedidosPorCodigo = new HashMap<>();
    private Optional<Direccion> direccion = Optional.empty();
    private Categoria categoria;

    /** Enum anidado. */
    public enum Categoria { COMUN, PREMIUM }
}
