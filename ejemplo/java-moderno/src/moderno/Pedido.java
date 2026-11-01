package moderno;

import java.util.List;

/** Record con componentes de clases del dominio: deben dibujarse como asociaciones. */
public record Pedido(Cliente cliente, List<Producto> productos) {
    public double total() {
        return productos.stream().mapToDouble(Producto::precio).sum();
    }
}
