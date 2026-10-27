package gt.cunoc.redxela.inventario;

import gt.cunoc.redxela.almacen.Almacen;
import gt.cunoc.redxela.almacen.Lote;
import gt.cunoc.redxela.almacen.Ubicacion;
import gt.cunoc.redxela.excepciones.StockInsuficienteException;
import gt.cunoc.redxela.modelo.LineaPedido;
import gt.cunoc.redxela.modelo.Producto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Existencias y ubicaciones de la mercaderia (estructuras internas del area de Control de Inventario).
 *
 * <p>La usan dos hilos: Control de Inventario (entradas) y el Despachador (salidas al iniciar un pedido).
 * Todos los metodos publicos son {@code synchronized} sobre este objeto, que es el monitor que protege
 * las existencias, el almacen y la lista de pendientes; asi cada operacion es atomica.</p>
 */
public class Inventario {

    /** Una ubicacion vista desde la interfaz (copia, no la ubicacion real). */
    public record VistaUbicacion(String codigo, int pasillo, int nivel, int posicion,
                                 long lote, String producto, int unidades) {
    }

    /** Un lote visto desde la interfaz, con las ubicaciones (posiblemente no contiguas) que ocupa. */
    public record VistaLote(long id, String producto, String nombre, int unidades, List<String> ubicaciones) {
    }

    private final Almacen almacen = new Almacen();
    /** Unidades ubicadas en el almacen, por codigo de producto. */
    private final Map<String, Integer> existencias = new LinkedHashMap<>();
    /** Mercaderia recibida que no cupo en el almacen; se ubica cuando se libera espacio. */
    private final Map<Producto, Integer> pendientesUbicar = new LinkedHashMap<>();
    /** Aviso que se dispara cuando un producto gana existencias (para reevaluar pedidos que lo esperan). */
    private volatile Consumer<Producto> alAumentarStock = p -> { };

    public void setAlAumentarStock(Consumer<Producto> alAumentarStock) {
        this.alAumentarStock = alAumentarStock;
    }

    /**
     * Registra una entrada de mercaderia: la ubica en el almacen y actualiza existencias.
     * Lo que no quepa queda como pendiente de ubicar; no se pierde.
     *
     * @return unidades que se ubicaron en este momento
     */
    public int registrarEntrada(Producto producto, int cantidad, long instante) {
        int ubicadas;
        synchronized (this) {
            ubicadas = ubicar(producto, cantidad, instante);
        }
        if (ubicadas > 0) {
            alAumentarStock.accept(producto); // fuera del candado para no retenerlo mientras se avisa
        }
        return ubicadas;
    }

    /** {@code true} si hay existencias para todas las lineas del pedido. */
    public synchronized boolean alcanza(List<LineaPedido> lineas) {
        return faltantes(lineas).isEmpty();
    }

    /** Productos sin existencias suficientes: codigo -> {pedidas, disponibles}. */
    public synchronized Map<String, int[]> faltantes(List<LineaPedido> lineas) {
        Map<String, int[]> faltan = new LinkedHashMap<>();
        totalesPorProducto(lineas).forEach((codigo, pedidas) -> {
            int hay = existencias.getOrDefault(codigo, 0);
            if (hay < pedidas) {
                faltan.put(codigo, new int[]{pedidas, hay});
            }
        });
        return faltan;
    }

    /**
     * Saca del almacen las unidades de un pedido (todo o nada) y aprovecha el espacio liberado para ubicar pendientes.
     *
     * @throws StockInsuficienteException si algun producto no alcanza (no se retira nada)
     */
    public void retirar(List<LineaPedido> lineas, long instante) {
        List<Producto> conNuevoStock;
        synchronized (this) {
            Map<String, Integer> totales = totalesPorProducto(lineas);
            totales.forEach((codigo, pedidas) -> {
                int hay = existencias.getOrDefault(codigo, 0);
                if (hay < pedidas) {
                    throw new StockInsuficienteException(codigo, pedidas, hay);
                }
            });
            totales.forEach((codigo, pedidas) -> {
                almacen.retirar(codigo, pedidas);
                existencias.merge(codigo, -pedidas, Integer::sum);
            });
            conNuevoStock = ubicarPendientes(instante);
        }
        conNuevoStock.forEach(alAumentarStock);
    }

    /** Intenta ubicar lo pendiente con el espacio libre. Devuelve los productos que ganaron existencias. */
    private List<Producto> ubicarPendientes(long instante) {
        List<Producto> conStock = new ArrayList<>();
        var it = pendientesUbicar.entrySet().iterator();
        while (it.hasNext() && almacen.libres() > 0) {
            var e = it.next();
            int ubicadas = almacen.almacenar(e.getKey(), e.getValue(), instante);
            if (ubicadas > 0) {
                existencias.merge(e.getKey().codigo(), ubicadas, Integer::sum);
                conStock.add(e.getKey());
            }
            if (ubicadas == e.getValue()) {
                it.remove();
            } else {
                e.setValue(e.getValue() - ubicadas);
            }
        }
        return conStock;
    }

    private int ubicar(Producto producto, int cantidad, long instante) {
        int ubicadas = almacen.almacenar(producto, cantidad, instante);
        if (ubicadas > 0) {
            existencias.merge(producto.codigo(), ubicadas, Integer::sum);
        }
        if (ubicadas < cantidad) {
            pendientesUbicar.merge(producto, cantidad - ubicadas, Integer::sum);
        }
        return ubicadas;
    }

    private static Map<String, Integer> totalesPorProducto(List<LineaPedido> lineas) {
        Map<String, Integer> totales = new LinkedHashMap<>();
        for (LineaPedido l : lineas) {
            totales.merge(l.producto().codigo(), l.cantidad(), Integer::sum);
        }
        return totales;
    }

    // ---- Consultas para la interfaz (devuelven copias tomadas bajo el candado) ----

    public synchronized int existencias(String codigo) {
        return existencias.getOrDefault(codigo, 0);
    }

    public synchronized Map<String, Integer> existencias() {
        return new LinkedHashMap<>(existencias);
    }

    public synchronized Map<String, Integer> pendientesUbicar() {
        Map<String, Integer> copia = new LinkedHashMap<>();
        pendientesUbicar.forEach((p, c) -> copia.put(p.codigo(), c));
        return copia;
    }

    public synchronized int ubicacionesLibres() {
        return almacen.libres();
    }

    public synchronized List<VistaUbicacion> mapaAlmacen() {
        List<VistaUbicacion> mapa = new ArrayList<>(Almacen.TOTAL_UBICACIONES);
        for (Ubicacion u : almacen.ubicaciones()) {
            mapa.add(new VistaUbicacion(u.codigo(), u.getPasillo(), u.getNivel(), u.getPosicion(),
                    u.getLoteId(), u.getCodigoProducto(), u.getUnidades()));
        }
        return mapa;
    }

    public synchronized List<VistaLote> lotes() {
        List<Ubicacion> ubic = almacen.ubicaciones();
        List<VistaLote> vista = new ArrayList<>();
        for (Lote lote : almacen.lotes()) {
            List<String> codigos = new ArrayList<>();
            int unidades = 0;
            for (int i : lote.getTablaUbicaciones()) {
                codigos.add(ubic.get(i).codigo());
                unidades += ubic.get(i).getUnidades();
            }
            vista.add(new VistaLote(lote.getId(), lote.getProducto().codigo(), lote.getProducto().nombre(),
                    unidades, codigos));
        }
        return vista;
    }
}
