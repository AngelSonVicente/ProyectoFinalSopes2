package gt.cunoc.redxela.excepciones;

/** Se pidio un producto que no esta en el catalogo. */
public class ProductoNoEncontradoException extends RedXelaException {

    public ProductoNoEncontradoException(String codigo) {
        super("No existe el producto " + codigo + " en el catalogo");
    }
}
