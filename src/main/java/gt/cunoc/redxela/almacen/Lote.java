package gt.cunoc.redxela.almacen;

import gt.cunoc.redxela.modelo.Producto;

import java.util.ArrayList;
import java.util.List;

/**
 * Un lote de un producto recibido en un mismo camion.
 *
 * <p>Guarda su <b>tabla de ubicaciones</b>: la lista de indices de las ubicaciones que ocupa. Es el equivalente a la
 * tabla de paginas de un proceso (o al bloque indice de la asignacion indexada de archivos): gracias a ella el lote
 * puede quedar repartido en ubicaciones no contiguas y aun asi se sabe exactamente donde esta cada parte.</p>
 */
public class Lote {

    private final long id;
    private final Producto producto;
    /** Instante simulado (ms) de ingreso; se despacha primero el lote mas antiguo (FIFO). */
    private final long ingreso;
    private final List<Integer> tablaUbicaciones = new ArrayList<>();

    Lote(long id, Producto producto, long ingreso) {
        this.id = id;
        this.producto = producto;
        this.ingreso = ingreso;
    }

    public long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public long getIngreso() {
        return ingreso;
    }

    /** Lista modificable, solo para {@link Almacen}. */
    List<Integer> tabla() {
        return tablaUbicaciones;
    }

    public List<Integer> getTablaUbicaciones() {
        return List.copyOf(tablaUbicaciones);
    }
}
