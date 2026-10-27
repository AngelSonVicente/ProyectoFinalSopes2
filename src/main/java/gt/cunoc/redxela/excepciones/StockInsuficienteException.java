package gt.cunoc.redxela.excepciones;

/** Se intento retirar mas unidades de las que hay en el almacen. */
public class StockInsuficienteException extends RedXelaException {

    public StockInsuficienteException(String codigo, int pedidas, int disponibles) {
        super("Stock insuficiente de " + codigo + ": se pidieron " + pedidas + " y hay " + disponibles);
    }
}
