package gt.cunoc.redxela.modelo;

/** Ciclo de vida de un pedido dentro del centro de distribucion. */
public enum EstadoPedido {
    /** Recien ingresado a la lista de espera, aun no evaluado por el despachador. */
    EN_COLA,
    /** En la lista de espera porque no hay existencias suficientes de algun producto. */
    ESPERANDO_STOCK,
    /** En la lista de espera porque algun recurso que necesita esta ocupado. */
    ESPERANDO_RECURSOS,
    /** Tiene todos sus recursos y un hilo trabajador lo esta procesando. */
    EN_PROCESO,
    /** Terminado y despachado. */
    COMPLETADO,
    /** Interrumpido (por ejemplo al detener la simulacion). */
    CANCELADO
}
