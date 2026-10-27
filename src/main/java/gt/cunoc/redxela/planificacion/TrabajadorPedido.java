package gt.cunoc.redxela.planificacion;

import gt.cunoc.redxela.estadisticas.Estadisticas;
import gt.cunoc.redxela.modelo.EstadoPedido;
import gt.cunoc.redxela.modelo.Etapa;
import gt.cunoc.redxela.modelo.Pedido;
import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.simulacion.RegistroEventos;
import gt.cunoc.redxela.simulacion.RelojSimulado;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Tarea que procesa un pedido en un hilo del pool de trabajadores. El pedido ya tiene todos sus recursos reservados.
 *
 * <p>La cantidad de trabajo es proporcional a las unidades: {@code totalArticulos * msPorUnidad} ms reales,
 * repartidos entre las etapas que aplican segun su peso. Al terminar cada etapa se liberan sus recursos y, pase lo
 * que pase (excepcion o interrupcion), el {@code finally} libera todo lo que quede para no dejar recursos tomados.</p>
 */
public class TrabajadorPedido implements Runnable {

    /** Cada cuanto se actualiza el progreso visible en la interfaz. */
    private static final long TICK_MS = 100;

    private final Pedido pedido;
    private final GestorRecursos gestor;
    private final RelojSimulado reloj;
    private final Estadisticas estadisticas;
    private final RegistroEventos eventos;
    private final long msPorUnidad;
    private final Consumer<Pedido> alTerminar;

    public TrabajadorPedido(Pedido pedido, GestorRecursos gestor, RelojSimulado reloj, Estadisticas estadisticas,
                            RegistroEventos eventos, long msPorUnidad, Consumer<Pedido> alTerminar) {
        this.pedido = pedido;
        this.gestor = gestor;
        this.reloj = reloj;
        this.estadisticas = estadisticas;
        this.eventos = eventos;
        this.msPorUnidad = msPorUnidad;
        this.alTerminar = alTerminar;
    }

    @Override
    public void run() {
        String duenio = pedido.etiqueta();
        try {
            Etapa[] etapas = Arrays.stream(Etapa.values()).filter(e -> e.aplicaA(pedido)).toArray(Etapa[]::new);
            double sumaPesos = Arrays.stream(etapas).mapToDouble(Etapa::getPeso).sum();
            long total = Math.max(TICK_MS, pedido.getTotalArticulos() * msPorUnidad);
            long hecho = 0;

            for (Etapa etapa : etapas) {
                long duracion = Math.round(total * etapa.getPeso() / sumaPesos);
                pedido.setEtapa(etapa);
                trabajar(duracion, hecho, total);
                hecho += duracion;
                // La etapa termino: sus recursos ya no se necesitan y se devuelven para otros pedidos.
                for (TipoRecurso recurso : etapa.getRecursos()) {
                    gestor.liberar(duenio, recurso);
                }
            }

            pedido.setProgreso(1.0);
            pedido.setRestanteMs(0);
            pedido.setFin(reloj.ahora());
            pedido.setEstado(EstadoPedido.COMPLETADO);
            estadisticas.registrarCompletado(pedido);
            eventos.registrar(duenio + " completado (" + pedido.getTotalArticulos() + " art.)");
        } catch (InterruptedException e) {
            // Se respeta la interrupcion: se marca el hilo y se cancela el pedido.
            Thread.currentThread().interrupt();
            pedido.setEstado(EstadoPedido.CANCELADO);
            eventos.registrar(duenio + " interrumpido");
        } catch (RuntimeException e) {
            pedido.setEstado(EstadoPedido.CANCELADO);
            eventos.registrar(duenio + " fallo: " + e.getMessage());
        } finally {
            gestor.liberarTodo(duenio);
            alTerminar.accept(pedido);
        }
    }

    /** Simula {@code duracion} ms de trabajo actualizando progreso y tiempo restante. */
    private void trabajar(long duracion, long hechoAntes, long total) throws InterruptedException {
        long inicio = System.nanoTime();
        long transcurrido = 0;
        while (transcurrido < duracion) {
            Thread.sleep(Math.min(TICK_MS, duracion - transcurrido));
            transcurrido = (System.nanoTime() - inicio) / 1_000_000;
            long avance = Math.min(total, hechoAntes + transcurrido);
            pedido.setProgreso((double) avance / total);
            pedido.setRestanteMs(total - avance);
        }
    }
}
