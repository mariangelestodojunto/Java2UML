package especial;

import java.util.ArrayList;
import java.util.List;

public class RepoClientes implements Repositorio<Cliente> {
    private final ArrayList<Cliente> clientes = new ArrayList<>();

    @Override
    public void guardar(Cliente elemento) {
        clientes.add(elemento);
    }

    @Override
    public List<Cliente> listar() {
        return clientes;
    }
}
