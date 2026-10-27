package gt.cunoc.redxela.modelo;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tipo de mercancia. Determina QUE recursos necesita un pedido (el nivel de servicio determina cuando).
 *
 * <p>Cada tipo usa una combinacion distinta de recursos. Las combinaciones se eligieron para que haya
 * contencion real sobre dos recursos compartidos al mismo tiempo:</p>
 * <ul>
 *     <li>PESADO y VOLUMINOSO compiten por Encargado + Montacargas.</li>
 *     <li>PESADO y PERECEDERO compiten por Encargado + Area de carga.</li>
 *     <li>ELECTRONICO compite con Recepcion por el escaner (unico).</li>
 * </ul>
 */
public enum TipoMercancia {

    GENERAL("General", "Caja estandar sin cuidados especiales.",
            req(TipoRecurso.ENCARGADO, 1, TipoRecurso.EMPAQUE, 1)),
    FRAGIL("Fragil", "Vidrio, ceramica o similares; requiere revision de calidad antes de salir.",
            req(TipoRecurso.ENCARGADO, 1, TipoRecurso.EMPAQUE, 1, TipoRecurso.CONTROL_CALIDAD, 1)),
    PESADO("Pesado", "Mas de 50 kg; se mueve con montacargas y se sube directo al camion.",
            req(TipoRecurso.ENCARGADO, 1, TipoRecurso.MONTACARGAS, 1, TipoRecurso.AREA_CARGA, 1)),
    ELECTRONICO("Electronico", "Alto valor; se escanea el numero de serie y pasa por calidad.",
            req(TipoRecurso.ENCARGADO, 1, TipoRecurso.ESCANER, 1, TipoRecurso.CONTROL_CALIDAD, 1, TipoRecurso.EMPAQUE, 1)),
    VOLUMINOSO("Voluminoso", "Muebles y articulos grandes; dos encargados y montacargas.",
            req(TipoRecurso.ENCARGADO, 2, TipoRecurso.MONTACARGAS, 1, TipoRecurso.EMPAQUE, 1)),
    PERECEDERO("Perecedero", "Alimentos o medicinas; revision de calidad y despacho inmediato en area de carga.",
            req(TipoRecurso.ENCARGADO, 1, TipoRecurso.CONTROL_CALIDAD, 1, TipoRecurso.AREA_CARGA, 1));

    private final String nombre;
    private final String descripcion;
    private final Map<TipoRecurso, Integer> recursos;

    TipoMercancia(String nombre, String descripcion, Map<TipoRecurso, Integer> recursos) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.recursos = Collections.unmodifiableMap(recursos);
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Recursos que requiere este tipo y cuantas unidades de cada uno. */
    public Map<TipoRecurso, Integer> getRecursos() {
        return recursos;
    }

    /** Construye el mapa de requerimientos a partir de pares (recurso, cantidad). */
    private static Map<TipoRecurso, Integer> req(Object... pares) {
        Map<TipoRecurso, Integer> mapa = new EnumMap<>(TipoRecurso.class);
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((TipoRecurso) pares[i], (Integer) pares[i + 1]);
        }
        return mapa;
    }
}
