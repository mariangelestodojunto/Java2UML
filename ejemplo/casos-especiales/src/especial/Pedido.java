package especial;

import java.util.List;
import java.util.function.Supplier;

public class Pedido implements Auditable {
    private Cliente.Categoria categoriaAplicada;
    private List<List<Item>> lotes;
    private Supplier<Item> proveedorDeItems;
    private Cliente cliente;

    @Override
    public double total() {
        double t = 0;
        for (List<Item> lote : lotes) {
            for (Item i : lote) {
                t += i.subtotal(); 
            }
        }
        return t;
    }

    @Override
    public String auditar() {
        return "ok";
    }

    public <X extends Item> void agregar(X item, int cantidad, String... notas) {
    }
}
