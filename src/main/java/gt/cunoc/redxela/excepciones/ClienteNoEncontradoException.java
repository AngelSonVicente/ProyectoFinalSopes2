package gt.cunoc.redxela.excepciones;

/** Se pidio un cliente que no existe. */
public class ClienteNoEncontradoException extends RedXelaException {

    public ClienteNoEncontradoException(long id) {
        super("No existe el cliente con id " + id);
    }
}
