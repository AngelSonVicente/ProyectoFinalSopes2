package gt.cunoc.redxela.modelo;

/**
 * Cuenta de cliente. Al crearla se elige el nivel de servicio contratado,
 * que se usa por defecto en sus pedidos.
 */
public record Cliente(long id, String nombre, String correo, NivelServicio nivelContratado) {
}
