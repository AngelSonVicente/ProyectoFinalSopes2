package gt.cunoc.redxela.almacen;

import gt.cunoc.redxela.modelo.Producto;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Almacen fisico: {@value #PASILLOS} pasillos x {@value #NIVELES} niveles x {@value #POSICIONES} posiciones,
 * cada ubicacion con capacidad de {@value #CAPACIDAD_UBICACION} unidades de volumen.
 *
 * <p><b>Asignacion no contigua</b>, analoga a la paginacion de memoria:</p>
 * <ul>
 *     <li>El espacio se divide en ubicaciones de tamano fijo (marcos).</li>
 *     <li>Un {@link BitSet} es el mapa de bits de ubicaciones ocupadas (como la tabla de marcos libres).</li>
 *     <li>Al llegar un lote se le asignan las primeras ubicaciones libres que se encuentren (primer ajuste),
 *     esten juntas o no, y se anotan en la tabla de ubicaciones del lote.</li>
 *     <li>No hay fragmentacion externa (cualquier ubicacion libre sirve), pero si interna: la ultima ubicacion de un
 *     lote puede quedar a medio llenar.</li>
 * </ul>
 *
 * <p>No es segura para hilos: la protege el candado de {@code Inventario}, que es su unico duenio.</p>
 */
public class Almacen {

    public static final int PASILLOS = 6;
    public static final int NIVELES = 4;
    public static final int POSICIONES = 10;
    public static final int CAPACIDAD_UBICACION = 10;
    public static final int TOTAL_UBICACIONES = PASILLOS * NIVELES * POSICIONES;

    private final Ubicacion[] ubicaciones = new Ubicacion[TOTAL_UBICACIONES];
    private final BitSet ocupadas = new BitSet(TOTAL_UBICACIONES);
    /** Lotes vigentes en orden de ingreso. */
    private final Map<Long, Lote> lotes = new LinkedHashMap<>();
    private long secuenciaLote = 1;

    public Almacen() {
        int i = 0;
        for (int p = 0; p < PASILLOS; p++) {
            for (int n = 0; n < NIVELES; n++) {
                for (int pos = 0; pos < POSICIONES; pos++) {
                    ubicaciones[i] = new Ubicacion(i, p, n, pos);
                    i++;
                }
            }
        }
    }

    /** Cuantas unidades de un producto caben en una ubicacion (al menos una, aunque sea muy voluminoso). */
    public static int unidadesPorUbicacion(Producto producto) {
        return Math.max(1, CAPACIDAD_UBICACION / producto.volumenUnidad());
    }

    /**
     * Guarda un lote nuevo en las ubicaciones libres que haya.
     *
     * @return unidades que se lograron ubicar (puede ser menos que {@code cantidad} si el almacen se llena)
     */
    public int almacenar(Producto producto, int cantidad, long instante) {
        int porUbicacion = unidadesPorUbicacion(producto);
        int restantes = cantidad;
        Lote lote = null;
        int indice = ocupadas.nextClearBit(0);
        while (restantes > 0 && indice < TOTAL_UBICACIONES) {
            if (lote == null) {
                lote = new Lote(secuenciaLote++, producto, instante);
                lotes.put(lote.getId(), lote);
            }
            int aqui = Math.min(porUbicacion, restantes);
            ubicaciones[indice].ocupar(lote.getId(), producto.codigo(), aqui);
            ocupadas.set(indice);
            lote.tabla().add(indice);
            restantes -= aqui;
            indice = ocupadas.nextClearBit(indice + 1);
        }
        return cantidad - restantes;
    }

    /**
     * Saca unidades de un producto empezando por el lote mas antiguo. Las ubicaciones que quedan vacias se liberan
     * en el mapa de bits y se quitan de la tabla del lote; un lote sin ubicaciones desaparece.
     *
     * @return unidades retiradas
     */
    public int retirar(String codigoProducto, int cantidad) {
        int pendientes = cantidad;
        Iterator<Lote> itLotes = lotes.values().iterator();
        while (pendientes > 0 && itLotes.hasNext()) {
            Lote lote = itLotes.next();
            if (!lote.getProducto().codigo().equals(codigoProducto)) {
                continue;
            }
            Iterator<Integer> itUbic = lote.tabla().iterator();
            while (pendientes > 0 && itUbic.hasNext()) {
                Ubicacion u = ubicaciones[itUbic.next()];
                pendientes -= u.sacar(pendientes);
                if (u.getUnidades() == 0) {
                    u.vaciar();
                    ocupadas.clear(u.getIndice());
                    itUbic.remove();
                }
            }
            if (lote.tabla().isEmpty()) {
                itLotes.remove();
            }
        }
        return cantidad - pendientes;
    }

    public int libres() {
        return TOTAL_UBICACIONES - ocupadas.cardinality();
    }

    /** Unidades de un producto que caben en el espacio libre actual. */
    public int capacidadLibrePara(Producto producto) {
        return libres() * unidadesPorUbicacion(producto);
    }

    public List<Ubicacion> ubicaciones() {
        return List.of(ubicaciones);
    }

    public List<Lote> lotes() {
        return new ArrayList<>(lotes.values());
    }
}
