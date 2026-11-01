package especial;

import especial.catalogo.*;

public class Item {
    private Producto producto;
    private int cantidad;
    private static int contador = 0;

    public double subtotal() {
        return cantidad * producto.getPrecio();
    }
}
