package gt.cunoc.redxela.simulacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bitacora de los ultimos eventos de la simulacion, para mostrarla en la interfaz.
 * Escriben muchos hilos (despachador, trabajadores, generador), por eso los metodos son {@code synchronized}.
 */
public class RegistroEventos {

    private static final Logger log = LoggerFactory.getLogger(RegistroEventos.class);
    private static final int MAXIMO = 200;

    /** Un evento con su instante simulado (ms) y el hilo que lo genero. */
    public record Evento(long instante, String hilo, String mensaje) {
    }

    private final Deque<Evento> eventos = new ArrayDeque<>();
    private final RelojSimulado reloj;

    public RegistroEventos(RelojSimulado reloj) {
        this.reloj = reloj;
    }

    public synchronized void registrar(String mensaje) {
        eventos.addFirst(new Evento(reloj.ahora(), Thread.currentThread().getName(), mensaje));
        if (eventos.size() > MAXIMO) {
            eventos.removeLast();
        }
        log.info(mensaje);
    }

    /** Eventos mas recientes primero. */
    public synchronized List<Evento> recientes() {
        return new ArrayList<>(eventos);
    }
}
