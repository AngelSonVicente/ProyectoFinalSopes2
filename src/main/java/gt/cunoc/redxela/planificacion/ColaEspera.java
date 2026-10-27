package gt.cunoc.redxela.planificacion;

import gt.cunoc.redxela.modelo.Pedido;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lista de espera de pedidos compartida entre productores (generador, API) y el despachador.
 *
 * <p>No se usa {@code PriorityBlockingQueue} porque con la politica de envejecimiento la prioridad cambia con el
 * tiempo, y un monticulo no se reordena solo cuando cambian las claves. En su lugar se guarda una lista protegida
 * por un {@link ReentrantLock} y el despachador la ordena con la politica vigente cada vez que la revisa.</p>
 *
 * <p>La {@link Condition} {@code hayCambio} evita la espera activa: el despachador duerme hasta que llega un pedido,
 * se libera un recurso o se cambia la politica (o como maximo un tiempo limite, para que el envejecimiento avance).</p>
 */
public class ColaEspera {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hayCambio = lock.newCondition();
    private final List<Pedido> pedidos = new ArrayList<>();
    /** Indica que hubo un cambio desde la ultima revision; evita perder avisos que llegan antes del await. */
    private boolean cambio;

    public void agregar(Pedido pedido) {
        lock.lock();
        try {
            pedidos.add(pedido);
            cambio = true;
            hayCambio.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public boolean remover(Pedido pedido) {
        lock.lock();
        try {
            return pedidos.remove(pedido);
        } finally {
            lock.unlock();
        }
    }

    /** Copia de la lista ordenada segun {@code politica} en el instante {@code ahora}. */
    public List<Pedido> ordenados(PoliticaPlanificacion politica, long ahora) {
        List<Pedido> copia;
        lock.lock();
        try {
            copia = new ArrayList<>(pedidos);
        } finally {
            lock.unlock();
        }
        // Se ordena fuera del candado para no bloquear a los productores mientras tanto.
        copia.sort(politica.comparador(ahora));
        return copia;
    }

    /** Despierta al despachador para que vuelva a revisar la lista. */
    public void notificarCambio() {
        lock.lock();
        try {
            cambio = true;
            hayCambio.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Bloquea hasta que haya un cambio o pasen {@code maxMs} milisegundos. */
    public void esperarCambio(long maxMs) throws InterruptedException {
        lock.lock();
        try {
            if (!cambio) {
                hayCambio.await(maxMs, TimeUnit.MILLISECONDS);
            }
            cambio = false;
        } finally {
            lock.unlock();
        }
    }

    public int tamanio() {
        lock.lock();
        try {
            return pedidos.size();
        } finally {
            lock.unlock();
        }
    }

    public void limpiar() {
        lock.lock();
        try {
            pedidos.clear();
        } finally {
            lock.unlock();
        }
    }
}
