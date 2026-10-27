package gt.cunoc.redxela.api;

import gt.cunoc.redxela.almacen.Almacen;
import gt.cunoc.redxela.deadlock.GestorConflictos;
import gt.cunoc.redxela.estadisticas.Estadisticas;
import gt.cunoc.redxela.inventario.Inventario;
import gt.cunoc.redxela.modelo.EstadoPedido;
import gt.cunoc.redxela.modelo.LineaPedido;
import gt.cunoc.redxela.modelo.Pedido;
import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.planificacion.PoliticaPlanificacion;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.recursos.PoolRecurso;
import gt.cunoc.redxela.simulacion.Simulacion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma la foto completa del sistema que consulta la interfaz cada pocos cientos de milisegundos.
 * Solo lee: cada componente entrega copias tomadas bajo su propio mecanismo de sincronizacion.
 */
@Component
public class ConstructorEstado {

    private static final int MAX_EVENTOS = 60;

    private final Simulacion sim;

    public ConstructorEstado(Simulacion sim) {
        this.sim = sim;
    }

    public Vistas.Estado construir() {
        long ahora = sim.getReloj().ahora();
        PoliticaPlanificacion politica = sim.getDespachador().getPolitica();
        Estadisticas.ResumenPolitica resumenActual = sim.getEstadisticas().resumen(politica);

        return new Vistas.Estado(ahora, ahora / 60_000.0, sim.getReloj().getFactor(),
                new Vistas.Politica(politica.name(), politica.getNombre(), politica.getDescripcion()),
                new Vistas.Generadores(sim.getGenerador().isActivo(), sim.getGenerador().getIntervaloMedioMs(),
                        sim.getGeneradorCamiones().isActivo(), sim.getGeneradorCamiones().getIntervaloMedioMs()),
                recursos(resumenActual),
                enProceso(ahora),
                espera(politica, ahora),
                terminados(),
                estadisticas(),
                inventario(),
                recepcion(),
                conflictos(),
                sim.getEventos().recientes().stream().limit(MAX_EVENTOS)
                        .map(e -> new Vistas.Evento(e.instante() / 60_000.0, e.hilo(), e.mensaje())).toList());
    }

    private List<Vistas.Recurso> recursos(Estadisticas.ResumenPolitica resumen) {
        List<Vistas.Recurso> lista = new ArrayList<>();
        for (PoolRecurso pool : sim.getGestor().pools()) {
            TipoRecurso t = pool.getTipo();
            lista.add(new Vistas.Recurso(t.name(), t.getNombre(), t.getCapacidad(), pool.ocupados(), pool.duenios(),
                    pool.esperando(), resumen.ocupacionPromedio().getOrDefault(t, 0.0)));
        }
        return lista;
    }

    private List<Vistas.PedidoProceso> enProceso(long ahora) {
        GestorRecursos gestor = sim.getGestor();
        return sim.getDespachador().enProceso().stream()
                .sorted(Comparator.comparingLong(Pedido::getId))
                .map(p -> {
                    List<String> tiene = new ArrayList<>();
                    for (PoolRecurso pool : gestor.pools()) {
                        int n = pool.unidadesDe(p.etiqueta());
                        if (n > 0) {
                            tiene.add(n + " x " + pool.getTipo().getNombre());
                        }
                    }
                    return new Vistas.PedidoProceso(p.getId(), p.getCliente().nombre(), p.getNivel().getNivel(),
                            p.getNivel().getNombre(), p.getTotalArticulos(), tipos(p), tiene,
                            p.getEtapa() == null ? "Iniciando" : p.getEtapa().getNombre(),
                            p.getProgreso(), p.getRestanteMs() / 1000.0, p.minutosEspera(ahora), p.isManual());
                })
                .toList();
    }

    private List<Vistas.PedidoEspera> espera(PoliticaPlanificacion politica, long ahora) {
        List<Pedido> orden = sim.getCola().ordenados(politica, ahora);
        List<Vistas.PedidoEspera> lista = new ArrayList<>(orden.size());
        int pos = 1;
        for (Pedido p : orden) {
            double espera = p.minutosEspera(ahora);
            int limite = p.getNivel().getMaxEsperaMinutos();
            lista.add(new Vistas.PedidoEspera(pos++, p.getId(), p.getCliente().nombre(), p.getNivel().getNivel(),
                    p.getNivel().getNombre(), p.getTotalArticulos(), tipos(p), espera, limite,
                    politica.clave(p, ahora), p.getEstado().name(), p.getMotivoEspera(), espera > limite,
                    p.isManual()));
        }
        return lista;
    }

    private List<Vistas.PedidoTerminado> terminados() {
        return sim.getDespachador().completadosRecientes().stream().map(p -> {
            double espera = (p.getInicio() - p.getLlegada()) / 60_000.0;
            double proceso = p.getFin() < 0 ? 0 : (p.getFin() - p.getInicio()) / 60_000.0;
            String politica = p.getPoliticaAplicada() == null ? ""
                    : PoliticaPlanificacion.valueOf(p.getPoliticaAplicada()).getNombre();
            return new Vistas.PedidoTerminado(p.getId(), p.getCliente().nombre(), p.getNivel().getNombre(),
                    p.getTotalArticulos(), p.getEstado().name(), espera, proceso,
                    p.getEstado() == EstadoPedido.COMPLETADO && espera <= p.getNivel().getMaxEsperaMinutos(),
                    politica);
        }).toList();
    }

    private Vistas.Estadisticas estadisticas() {
        Estadisticas est = sim.getEstadisticas();
        List<Estadisticas.ResumenPolitica> porPolitica = new ArrayList<>(est.comparacion().values());
        long completados = porPolitica.stream().mapToLong(Estadisticas.ResumenPolitica::completados).sum();
        Map<String, Double> ocupacion = new LinkedHashMap<>();
        est.ocupacionActual().forEach((t, v) -> ocupacion.put(t.name(), v));
        return new Vistas.Estadisticas(est.getTotalIngresados(), completados, sim.getCola().tamanio(),
                sim.getDespachador().enProceso().size(), ocupacion, porPolitica);
    }

    private Vistas.Inventario inventario() {
        Inventario inv = sim.getInventario();
        Map<String, Integer> existencias = inv.existencias();
        Map<String, Integer> pendientes = inv.pendientesUbicar();
        List<Vistas.Producto> productos = sim.getCatalogo().todos().stream()
                .map(p -> new Vistas.Producto(p.codigo(), p.nombre(), p.tipo().getNombre(),
                        existencias.getOrDefault(p.codigo(), 0), pendientes.getOrDefault(p.codigo(), 0)))
                .toList();
        List<Vistas.Celda> mapa = inv.mapaAlmacen().stream()
                .map(u -> new Vistas.Celda(u.lote(), u.producto(), u.unidades())).toList();
        return new Vistas.Inventario(productos, inv.ubicacionesLibres(), Almacen.TOTAL_UBICACIONES,
                Almacen.PASILLOS, Almacen.NIVELES, Almacen.POSICIONES, mapa, inv.lotes());
    }

    private Vistas.Recepcion recepcion() {
        return new Vistas.Recepcion(sim.getRecepcion().getActividad(), sim.getControlInventario().getActividad(),
                sim.getRecepcion().camionesEnPatio(), sim.avisosEnBuzon(), sim.capacidadBuzon(),
                sim.getRecepcion().getAvisosEnviados(), sim.getControlInventario().getAvisosProcesados(),
                sim.getRecepcion().getEsperasBuzonLleno());
    }

    private Vistas.Conflictos conflictos() {
        GestorConflictos gc = sim.getConflictos();
        List<Vistas.Operacion> ops = gc.operaciones().stream()
                .map(o -> new Vistas.Operacion(o.getNombre(), o.getDescripcion(), o.getEstado().name(),
                        o.getDetalle(), o.getIntentos(), o.isConPrevencion(),
                        o.getPasos().stream().map(p -> p.cantidad() + " x " + p.recurso().getNombre()
                                + " (" + p.accion() + ")").toList()))
                .toList();
        List<Vistas.Conflicto> conflictos = gc.conflictos().stream()
                .map(c -> new Vistas.Conflicto(c.getId(), c.getDetectado() / 60_000.0, c.getParticipantes(),
                        c.getRecursos(), c.getExplicacion(), c.isResuelto(), c.getGanador()))
                .toList();
        List<Vistas.Arista> grafo = gc.grafoActual().stream()
                .map(a -> new Vistas.Arista(a.espera(), a.retiene(), a.recurso().getNombre())).toList();
        return new Vistas.Conflictos(ops, conflictos, grafo);
    }

    private static List<String> tipos(Pedido p) {
        return p.getLineas().stream().map(LineaPedido::tipo).distinct().map(t -> t.getNombre()).toList();
    }
}
