package gt.cunoc.redxela.modelo;

/**
 * Recursos compartidos del centro de distribucion y su capacidad.
 *
 * <p>El orden de declaracion de este enum es el ORDEN GLOBAL de adquisicion: cualquier hilo que necesite
 * varios recursos los pide siempre de arriba hacia abajo. Asi nunca se forma una espera circular
 * (se rompe una de las cuatro condiciones de Coffman) y no puede haber deadlock.</p>
 */
public enum TipoRecurso {

    ENCARGADO("Encargados de bodega", 10),
    MONTACARGAS("Montacargas", 3),
    ESCANER("Sistema de escaneo", 1),
    AREA_CARGA("Areas de carga", 2),
    CONTROL_CALIDAD("Estaciones de control de calidad", 4),
    EMPAQUE("Estaciones de empaque", 6);

    private final String nombre;
    private final int capacidad;

    TipoRecurso(String nombre, int capacidad) {
        this.nombre = nombre;
        this.capacidad = capacidad;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCapacidad() {
        return capacidad;
    }
}
