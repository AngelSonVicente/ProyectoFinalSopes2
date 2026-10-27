package gt.cunoc.redxela.recursos;

import gt.cunoc.redxela.modelo.TipoRecurso;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Administra todos los recursos compartidos del centro.
 *
 * <p>Prevencion de deadlock para los pedidos:</p>
 * <ol>
 *     <li><b>Todo o nada</b>: un pedido reserva TODOS sus recursos antes de empezar; si falta uno devuelve los
 *     que ya tomo y vuelve a la lista de espera. Asi nunca retiene recursos mientras espera otros
 *     (se rompe la condicion de "retener y esperar").</li>
 *     <li><b>Orden global</b>: los recursos se piden siempre en el orden del enum {@link TipoRecurso}, por lo que
 *     tampoco puede formarse una espera circular.</li>
 * </ol>
 */
public class GestorRecursos {

    private final Map<TipoRecurso, PoolRecurso> pools = new EnumMap<>(TipoRecurso.class);
    /** Accion a ejecutar cada vez que se libera algo (despierta al despachador). */
    private volatile Runnable alLiberar = () -> { };

    public GestorRecursos() {
        for (TipoRecurso tipo : TipoRecurso.values()) {
            pools.put(tipo, new PoolRecurso(tipo));
        }
    }

    public void setAlLiberar(Runnable alLiberar) {
        this.alLiberar = alLiberar;
    }

    /**
     * Intenta reservar todos los recursos de {@code requerimientos} para {@code duenio}, sin bloquear.
     *
     * @return {@code true} si los obtuvo todos; {@code false} si no obtuvo ninguno
     */
    public boolean intentarReservar(String duenio, Map<TipoRecurso, Integer> requerimientos) {
        List<PoolRecurso> tomados = new ArrayList<>();
        // values() recorre en el orden global de adquisicion
        for (TipoRecurso tipo : TipoRecurso.values()) {
            Integer cantidad = requerimientos.get(tipo);
            if (cantidad == null) {
                continue;
            }
            PoolRecurso pool = pools.get(tipo);
            if (!pool.intentarAdquirir(duenio, cantidad)) {
                // Deshacer: se devuelve lo tomado sin avisar al despachador (fue el mismo quien lo pidio).
                tomados.forEach(p -> p.liberar(duenio));
                return false;
            }
            tomados.add(pool);
        }
        return true;
    }

    /** Recursos de {@code requerimientos} que en este momento no alcanzan. Solo informativo. */
    public List<TipoRecurso> faltantes(Map<TipoRecurso, Integer> requerimientos) {
        List<TipoRecurso> faltan = new ArrayList<>();
        requerimientos.forEach((tipo, cantidad) -> {
            if (pools.get(tipo).disponibles() < cantidad) {
                faltan.add(tipo);
            }
        });
        return faltan;
    }

    /** Libera las unidades de un tipo de recurso que tenga {@code duenio}. */
    public void liberar(String duenio, TipoRecurso tipo) {
        if (pools.get(tipo).liberar(duenio) > 0) {
            alLiberar.run();
        }
    }

    /** Libera todo lo que tenga {@code duenio}. Se llama siempre en un {@code finally}. */
    public void liberarTodo(String duenio) {
        int total = 0;
        for (PoolRecurso pool : pools.values()) {
            total += pool.liberar(duenio);
        }
        if (total > 0) {
            alLiberar.run();
        }
    }

    public PoolRecurso pool(TipoRecurso tipo) {
        return pools.get(tipo);
    }

    public Collection<PoolRecurso> pools() {
        return pools.values();
    }
}
