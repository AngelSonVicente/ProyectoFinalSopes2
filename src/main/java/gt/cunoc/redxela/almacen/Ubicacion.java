package gt.cunoc.redxela.almacen;

/**
 * Una ubicacion fisica del almacen (pasillo, nivel, posicion). Es la unidad de asignacion, como un marco de pagina:
 * todas tienen la misma capacidad y una ubicacion solo guarda unidades de un mismo lote.
 *
 * <p>No es segura para hilos por si sola: solo se modifica desde {@link Almacen}, que a su vez solo se usa
 * bajo el candado de {@code Inventario}.</p>
 */
public class Ubicacion {

    private final int indice;
    private final int pasillo;
    private final int nivel;
    private final int posicion;

    private long loteId;
    private String codigoProducto;
    private int unidades;

    Ubicacion(int indice, int pasillo, int nivel, int posicion) {
        this.indice = indice;
        this.pasillo = pasillo;
        this.nivel = nivel;
        this.posicion = posicion;
    }

    void ocupar(long loteId, String codigoProducto, int unidades) {
        this.loteId = loteId;
        this.codigoProducto = codigoProducto;
        this.unidades = unidades;
    }

    void vaciar() {
        this.loteId = 0;
        this.codigoProducto = null;
        this.unidades = 0;
    }

    /** Saca hasta {@code cantidad} unidades y devuelve cuantas saco. */
    int sacar(int cantidad) {
        int sacadas = Math.min(cantidad, unidades);
        unidades -= sacadas;
        return sacadas;
    }

    public boolean libre() {
        return loteId == 0;
    }

    /** Codigo legible, ej. "P2-N3-07" = pasillo 2, nivel 3, posicion 7. */
    public String codigo() {
        return String.format("P%d-N%d-%02d", pasillo + 1, nivel + 1, posicion + 1);
    }

    public int getIndice() {
        return indice;
    }

    public int getPasillo() {
        return pasillo;
    }

    public int getNivel() {
        return nivel;
    }

    public int getPosicion() {
        return posicion;
    }

    public long getLoteId() {
        return loteId;
    }

    public String getCodigoProducto() {
        return codigoProducto;
    }

    public int getUnidades() {
        return unidades;
    }
}
