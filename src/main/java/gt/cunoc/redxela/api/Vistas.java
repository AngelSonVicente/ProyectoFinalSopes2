package gt.cunoc.redxela.api;

import java.util.List;
import java.util.Map;

/**
 * Objetos que se envian a la interfaz (JSON). Son copias inmutables tomadas en un instante: la interfaz nunca
 * recibe referencias a las estructuras vivas que modifican los hilos de la simulacion.
 */
public final class Vistas {

    private Vistas() {
    }

    public record Estado(long instanteMs, double minutos, double factorReloj,
                         Politica politica, Generadores generadores,
                         List<Recurso> recursos, List<PedidoProceso> enProceso, List<PedidoEspera> espera,
                         List<PedidoTerminado> terminados, Estadisticas estadisticas,
                         Inventario inventario, Recepcion recepcion, Conflictos conflictos,
                         List<Evento> eventos) {
    }

    public record Politica(String id, String nombre, String descripcion) {
    }

    public record Generadores(boolean pedidosActivo, long pedidosIntervaloMs,
                              boolean camionesActivo, long camionesIntervaloMs) {
    }

    public record Recurso(String tipo, String nombre, int capacidad, int ocupados, List<String> unidades,
                          Map<String, Integer> esperando, double ocupacionPromedio) {
    }

    public record PedidoProceso(long id, String cliente, int nivel, String nivelNombre, int articulos,
                                List<String> tipos, List<String> recursos, String etapa, double progreso,
                                double restanteSeg, double esperaMin, boolean manual) {
    }

    public record PedidoEspera(int posicion, long id, String cliente, int nivel, String nivelNombre, int articulos,
                               List<String> tipos, double esperaMin, double limiteMin, double clave,
                               String estado, String motivo, boolean slaVencido, boolean manual) {
    }

    public record PedidoTerminado(long id, String cliente, String nivelNombre, int articulos, String estado,
                                  double esperaMin, double procesoMin, boolean cumplioSla, String politica) {
    }

    public record Estadisticas(long ingresados, long completados, long enEspera, long enProceso,
                               Map<String, Double> ocupacionActual,
                               List<gt.cunoc.redxela.estadisticas.Estadisticas.ResumenPolitica> porPolitica) {
    }

    public record Producto(String codigo, String nombre, String tipo, int existencias, int pendientes) {
    }

    /** Ubicacion compacta: [lote, unidades] y codigo de producto (o null si libre). */
    public record Celda(long lote, String producto, int unidades) {
    }

    public record Inventario(List<Producto> productos, int ubicacionesLibres, int totalUbicaciones,
                             int pasillos, int niveles, int posiciones, List<Celda> mapa,
                             List<gt.cunoc.redxela.inventario.Inventario.VistaLote> lotes) {
    }

    public record Recepcion(String actividadRecepcion, String actividadInventario, List<String> patio,
                            int buzon, int capacidadBuzon, long avisosEnviados, long avisosProcesados,
                            long esperasBuzonLleno) {
    }

    public record Operacion(String nombre, String descripcion, String estado, String detalle, int intentos,
                            boolean conPrevencion, List<String> pasos) {
    }

    public record Conflicto(long id, double detectadoMin, List<gt.cunoc.redxela.deadlock.Conflicto.Participante> participantes,
                            List<String> recursos, String explicacion, boolean resuelto, String ganador) {
    }

    public record Arista(String espera, String retiene, String recurso) {
    }

    public record Conflictos(List<Operacion> operaciones, List<Conflicto> conflictos, List<Arista> grafo) {
    }

    public record Evento(double minuto, String hilo, String mensaje) {
    }
}
