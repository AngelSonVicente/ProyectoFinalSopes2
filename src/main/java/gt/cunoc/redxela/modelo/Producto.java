package gt.cunoc.redxela.modelo;

/**
 * Producto del catalogo (definido de antemano, no se crea desde la interfaz).
 *
 * @param codigo         identificador unico, ej. "P-001"
 * @param nombre         nombre comercial
 * @param tipo           tipo de mercancia, define los recursos que necesita
 * @param volumenUnidad  espacio que ocupa una unidad en el almacen (en unidades de ubicacion)
 */
public record Producto(String codigo, String nombre, TipoMercancia tipo, int volumenUnidad) {
}
