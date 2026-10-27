package gt.cunoc.redxela.excepciones;

/** Excepcion base de la simulacion. Es no verificada para no ensuciar las firmas de los hilos. */
public class RedXelaException extends RuntimeException {

    public RedXelaException(String mensaje) {
        super(mensaje);
    }

    public RedXelaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
