package gt.cunoc.redxela.recursos;

import gt.cunoc.redxela.modelo.TipoRecurso;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Conjunto de unidades identicas de un recurso (ej. las 6 estaciones de empaque).
 *
 * <p>El acceso se controla con un {@link Semaphore} contador iniciado con la capacidad del recurso:
 * cada permiso es una unidad libre. Para el escaner (capacidad 1) el semaforo es binario y funciona
 * como mutex. Se usa semaforo y no {@code ReentrantLock} porque la unidad la reserva el hilo despachador
 * y la libera el hilo trabajador; un {@code ReentrantLock} solo puede liberarlo el mismo hilo que lo tomo.</p>
 *
 * <p>Ademas del semaforo se lleva un arreglo con el duenio de cada unidad para mostrar en la interfaz
 * quien usa cada estacion. Invariante: el numero de casillas vacias siempre es mayor o igual a los permisos
 * disponibles, porque al adquirir primero se toma el permiso y luego la casilla, y al liberar primero se
 * vacia la casilla y luego se devuelve el permiso.</p>
 */
public class PoolRecurso {

    private final TipoRecurso tipo;
    /** Semaforo justo (fair): en las esperas bloqueantes atiende a los hilos en orden de llegada. */
    private final Semaphore semaforo;
    /** Duenio de cada unidad; {@code null} = libre. Protegido por el monitor de este objeto. */
    private final String[] duenios;
    /** Quien esta bloqueado esperando unidades de este recurso y cuantas pide (para el grafo de espera). */
    private final Map<String, Integer> esperando = new ConcurrentHashMap<>();

    public PoolRecurso(TipoRecurso tipo) {
        this.tipo = tipo;
        this.semaforo = new Semaphore(tipo.getCapacidad(), true);
        this.duenios = new String[tipo.getCapacidad()];
    }

    /**
     * Intenta tomar {@code cantidad} unidades sin bloquear. Es atomico: o toma las n unidades o ninguna.
     *
     * <p>Se usa {@code tryAcquire(n, 0, ms)} y no {@code tryAcquire(n)} porque este ultimo se "cuela" delante de
     * los hilos que ya estan esperando aunque el semaforo sea justo; con tiempo 0 se respeta la fila y el
     * despachador no puede dejar sin recursos a un hilo bloqueado.</p>
     *
     * @return {@code true} si las obtuvo
     */
    public boolean intentarAdquirir(String duenio, int cantidad) {
        try {
            if (!semaforo.tryAcquire(cantidad, 0, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        asignar(duenio, cantidad);
        return true;
    }

    /**
     * Espera sin limite de tiempo hasta obtener {@code cantidad} unidades. Mientras espera queda registrado en
     * {@link #esperando()}, que es lo que usa el detector para construir el grafo de espera. La espera es
     * interrumpible: asi se resuelve un deadlock (se interrumpe a la operacion que debe ceder).
     */
    public void adquirirBloqueante(String duenio, int cantidad) throws InterruptedException {
        esperando.merge(duenio, cantidad, Integer::sum);
        try {
            semaforo.acquire(cantidad);
        } finally {
            esperando.remove(duenio);
        }
        asignar(duenio, cantidad);
    }

    /**
     * Espera como maximo {@code timeoutMs} a que haya {@code cantidad} unidades libres.
     * Con el semaforo justo, los hilos que esperan son atendidos en orden de llegada.
     *
     * @return {@code true} si las obtuvo antes del tiempo limite
     */
    public boolean adquirir(String duenio, int cantidad, long timeoutMs) throws InterruptedException {
        esperando.merge(duenio, cantidad, Integer::sum);
        try {
            if (!semaforo.tryAcquire(cantidad, timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } finally {
            esperando.remove(duenio);
        }
        asignar(duenio, cantidad);
        return true;
    }

    /**
     * Libera todas las unidades que tenga {@code duenio}.
     *
     * @return cuantas unidades se liberaron
     */
    public int liberar(String duenio) {
        int liberadas = 0;
        synchronized (this) {
            for (int i = 0; i < duenios.length; i++) {
                if (duenio.equals(duenios[i])) {
                    duenios[i] = null;
                    liberadas++;
                }
            }
        }
        if (liberadas > 0) {
            semaforo.release(liberadas);
        }
        return liberadas;
    }

    private synchronized void asignar(String duenio, int cantidad) {
        int pendientes = cantidad;
        for (int i = 0; i < duenios.length && pendientes > 0; i++) {
            if (duenios[i] == null) {
                duenios[i] = duenio;
                pendientes--;
            }
        }
        if (pendientes > 0) {
            // No deberia ocurrir por el invariante; si ocurre se devuelven los permisos para no perderlos.
            semaforo.release(pendientes);
            throw new IllegalStateException("Inconsistencia en " + tipo + ": faltaron casillas libres");
        }
    }

    /** Copia del duenio de cada unidad, para la interfaz. */
    public synchronized List<String> duenios() {
        return Arrays.asList(Arrays.copyOf(duenios, duenios.length));
    }

    /** Copia de quien espera este recurso -> unidades que pide. */
    public Map<String, Integer> esperando() {
        return Map.copyOf(esperando);
    }

    /** Unidades que tiene {@code duenio} de este recurso. */
    public synchronized int unidadesDe(String duenio) {
        int n = 0;
        for (String d : duenios) {
            if (duenio.equals(d)) {
                n++;
            }
        }
        return n;
    }

    public int ocupados() {
        return tipo.getCapacidad() - semaforo.availablePermits();
    }

    public int disponibles() {
        return semaforo.availablePermits();
    }

    public TipoRecurso getTipo() {
        return tipo;
    }
}
