package gt.cunoc.redxela.excepciones;

/** Datos de entrada incorrectos (cantidades negativas, campos vacios, etc.). */
public class DatosInvalidosException extends RedXelaException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
