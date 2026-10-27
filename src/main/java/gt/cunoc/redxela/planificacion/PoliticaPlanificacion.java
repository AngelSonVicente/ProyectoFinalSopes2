package gt.cunoc.redxela.planificacion;

import gt.cunoc.redxela.modelo.Pedido;

import java.util.Comparator;

/**
 * Politicas para decidir que pedido de la lista de espera se atiende primero (patron Strategy).
 *
 * <p>Cada politica calcula una <b>clave</b> numerica por pedido: menor clave = se atiende antes.
 * La clave se muestra en la interfaz para que se entienda el criterio de orden. Los empates se resuelven
 * por nivel de servicio y luego por orden de llegada (FIFO).</p>
 */
public enum PoliticaPlanificacion {

    PRIORIDAD_ESTRICTA("Prioridad estricta",
            "Siempre primero el nivel de servicio mas alto (1 = Expres). Puede causar inanicion de los niveles bajos.") {
        @Override
        public double clave(Pedido p, long ahora) {
            return p.getNivel().getNivel();
        }
    },

    ENVEJECIMIENTO("Prioridad con envejecimiento",
            "Prioridad = nivel - minutos esperando / " + PoliticaPlanificacion.MINUTOS_POR_NIVEL
                    + ". Cada " + PoliticaPlanificacion.MINUTOS_POR_NIVEL
                    + " min de espera el pedido sube un nivel; evita la inanicion.") {
        @Override
        public double clave(Pedido p, long ahora) {
            return p.getNivel().getNivel() - p.minutosEspera(ahora) / MINUTOS_POR_NIVEL;
        }
    },

    MENOS_ARTICULOS("Menos articulos primero (SJF)",
            "Primero el pedido con menos unidades, porque libera las estaciones mas rapido. "
                    + "Minimiza la espera promedio pero puede retrasar pedidos grandes.") {
        @Override
        public double clave(Pedido p, long ahora) {
            return p.getTotalArticulos();
        }
    },

    PLAZO_MAS_CERCANO("Plazo mas cercano (EDF)",
            "Primero el pedido cuyo limite de SLA vence antes (llegada + espera maxima de su nivel).") {
        @Override
        public double clave(Pedido p, long ahora) {
            return (p.limiteSla() - ahora) / 60_000.0;
        }
    },

    FIFO("Orden de llegada (FIFO)",
            "Se atiende en el orden en que llegaron, sin importar el nivel. Sirve como linea base.") {
        @Override
        public double clave(Pedido p, long ahora) {
            return p.getLlegada() / 60_000.0;
        }
    };

    /** Minutos simulados de espera que hacen subir un nivel en la politica de envejecimiento. */
    static final double MINUTOS_POR_NIVEL = 5.0;

    private final String nombre;
    private final String descripcion;

    PoliticaPlanificacion(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    /** Clave de orden del pedido en el instante simulado {@code ahora}. Menor = mas prioritario. */
    public abstract double clave(Pedido p, long ahora);

    /** Comparador completo con desempates, evaluado en el instante {@code ahora}. */
    public Comparator<Pedido> comparador(long ahora) {
        return Comparator.<Pedido>comparingDouble(p -> clave(p, ahora))
                .thenComparingInt(p -> p.getNivel().getNivel())
                .thenComparingLong(Pedido::getLlegada)
                .thenComparingLong(Pedido::getId);
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
