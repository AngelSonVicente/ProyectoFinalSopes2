package gt.cunoc.redxela.planificacion;

import gt.cunoc.redxela.estadisticas.Estadisticas;
import gt.cunoc.redxela.modelo.EstadoPedido;
import gt.cunoc.redxela.modelo.Pedido;
import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.simulacion.RegistroEventos;
import gt.cunoc.redxela.simulacion.RelojSimulado;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Hilo planificador. Revisa la lista de espera en el orden de la politica vigente y arranca cada pedido
 * que pueda obtener todos sus recursos en ese momento; los demas se quedan esperando con el motivo anotado.
 *
 * <p>Si el primer pedido no puede iniciar, se sigue revisando a los siguientes (un pedido pequeno puede usar
 * recursos que el grande no alcanza); asi no se desperdician estaciones libres.</p>
 */
public class Despachador implements Runnable {

    /** Tiempo maximo dormido entre revisiones; permite que avance el envejecimiento aunque no haya eventos. */
    private static final long REVISION_MAX_MS = 500;
    private static final int MAX_COMPLETADOS_RECIENTES = 30;

    private final ColaEspera cola;
    private final GestorRecursos gestor;
    private final RelojSimulado reloj;
    private final Estadisticas estadisticas;
    private final RegistroEventos eventos;
    private final long msPorUnidad;
    /** Pool de hilos trabajadores; como cada pedido necesita al menos un encargado, nunca hay mas de 10 a la vez. */
    private final ExecutorService trabajadores;

    private final Map<Long, Pedido> enProceso = new ConcurrentHashMap<>();
    private final Deque<Pedido> completadosRecientes = new ArrayDeque<>();

    private volatile PoliticaPlanificacion politica = PoliticaPlanificacion.PRIORIDAD_ESTRICTA;
    private volatile boolean activo = true;
    private Thread hilo;

    public Despachador(ColaEspera cola, GestorRecursos gestor, RelojSimulado reloj, Estadisticas estadisticas,
                       RegistroEventos eventos, long msPorUnidad) {
        this.cola = cola;
        this.gestor = gestor;
        this.reloj = reloj;
        this.estadisticas = estadisticas;
        this.eventos = eventos;
        this.msPorUnidad = msPorUnidad;
        this.trabajadores = Executors.newFixedThreadPool(TipoRecurso.ENCARGADO.getCapacidad(), hilosConNombre("Trabajador"));
    }

    public synchronized void iniciar() {
        if (hilo == null) {
            hilo = new Thread(this, "Despachador");
            hilo.start();
        }
    }

    public synchronized void detener() {
        activo = false;
        if (hilo != null) {
            hilo.interrupt();
        }
        trabajadores.shutdownNow();
        try {
            trabajadores.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        eventos.registrar("Despachador iniciado con politica: " + politica.getNombre());
        while (activo) {
            try {
                revisar();
                cola.esperarCambio(REVISION_MAX_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                // Un error con un pedido no debe tumbar al planificador.
                eventos.registrar("Error en el despachador: " + e.getMessage());
            }
        }
    }

    /** Una pasada por la lista de espera en el orden de la politica vigente. */
    private void revisar() {
        PoliticaPlanificacion actual = politica;
        long ahora = reloj.ahora();
        for (Pedido pedido : cola.ordenados(actual, ahora)) {
            if (gestor.intentarReservar(pedido.etiqueta(), pedido.getRequerimientos())) {
                cola.remover(pedido);
                arrancar(pedido, actual);
            } else {
                List<TipoRecurso> faltan = gestor.faltantes(pedido.getRequerimientos());
                pedido.setEstado(EstadoPedido.ESPERANDO_RECURSOS);
                pedido.setMotivoEspera(faltan.isEmpty() ? "Recursos ocupados"
                        : "Sin " + faltan.stream().map(TipoRecurso::getNombre).collect(Collectors.joining(", ")));
            }
        }
    }

    private void arrancar(Pedido pedido, PoliticaPlanificacion actual) {
        pedido.setInicio(reloj.ahora());
        pedido.setEstado(EstadoPedido.EN_PROCESO);
        pedido.setMotivoEspera(null);
        pedido.setPoliticaAplicada(actual.name());
        enProceso.put(pedido.getId(), pedido);
        eventos.registrar(pedido.etiqueta() + " inicia (" + pedido.getNivel().getNombre() + ", espero "
                + String.format("%.1f", pedido.minutosEspera(reloj.ahora())) + " min)");
        trabajadores.execute(new TrabajadorPedido(pedido, gestor, reloj, estadisticas, eventos, msPorUnidad, this::alTerminar));
    }

    /** Lo llama el trabajador al terminar (bien o mal). */
    private void alTerminar(Pedido pedido) {
        enProceso.remove(pedido.getId());
        synchronized (completadosRecientes) {
            completadosRecientes.addFirst(pedido);
            if (completadosRecientes.size() > MAX_COMPLETADOS_RECIENTES) {
                completadosRecientes.removeLast();
            }
        }
    }

    /** Cambia la politica en caliente y fuerza una nueva revision con el nuevo orden. */
    public void setPolitica(PoliticaPlanificacion nueva) {
        this.politica = nueva;
        eventos.registrar("Politica cambiada a: " + nueva.getNombre());
        cola.notificarCambio();
    }

    public PoliticaPlanificacion getPolitica() {
        return politica;
    }

    public Collection<Pedido> enProceso() {
        return List.copyOf(enProceso.values());
    }

    public List<Pedido> completadosRecientes() {
        synchronized (completadosRecientes) {
            return new ArrayList<>(completadosRecientes);
        }
    }

    /** Fabrica de hilos con nombre legible (Trabajador-1, Trabajador-2, ...) para la bitacora. */
    static ThreadFactory hilosConNombre(String prefijo) {
        AtomicInteger contador = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefijo + "-" + contador.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }
}
