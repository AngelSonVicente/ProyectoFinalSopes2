package gt.cunoc.redxela.modelo;

/** Un producto dentro de un pedido y cuantas unidades se solicitan. */
public record LineaPedido(Producto producto, int cantidad) {

    public LineaPedido {
        if (producto == null) {
            throw new IllegalArgumentException("La linea debe tener un producto");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
    }

    public TipoMercancia tipo() {
        return producto.tipo();
    }
}
