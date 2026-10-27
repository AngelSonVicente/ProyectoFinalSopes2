package gt.cunoc.redxela.inventario;

import gt.cunoc.redxela.modelo.Producto;

/**
 * Mensaje que Recepcion envia a Control de Inventario por el buzon: "llego esta mercaderia".
 *
 * <p>Es inmutable (record): una vez enviado ninguna de las dos areas puede cambiarlo, asi que se comparte
 * entre hilos sin sincronizacion adicional. Las areas solo se comunican con estos mensajes.</p>
 *
 * @param id       numero correlativo del aviso
 * @param producto producto recibido
 * @param cantidad unidades recibidas
 * @param instante instante simulado (ms) en que Recepcion termino de escanear
 */
public record AvisoLlegada(long id, Producto producto, int cantidad, long instante) {
}
