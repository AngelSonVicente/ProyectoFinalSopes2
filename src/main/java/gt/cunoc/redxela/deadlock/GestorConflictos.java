package gt.cunoc.redxela.deadlock;

import gt.cunoc.redxela.excepciones.DatosInvalidosException;
import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.recursos.PoolRecurso;
import gt.cunoc.redxela.simulacion.RegistroEventos;
import gt.cunoc.redxela.simulacion.RelojSimulado;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Provoca, detecta y resuelve el conflicto de recursos.
 *
 * <p><b>Escenario</b>: Recepcion de un camion pesado toma el ESCANER (escanea la guia) y luego pide un
 * MONTACARGAS para bajar la carga. Al mismo tiempo un Despacho extra pesado toma los 3 MONTACARGAS y luego pide el
 * ESCANER para verificar la guia de salida. Sin prevencion, cada una retiene lo que la otra necesita: deadlock.
 * Con prevencion, ambas piden en el orden global (montacargas antes que escaner) y no puede formarse el ciclo.</p>
 *
 * <p><b>Deteccion</b>: un hilo revisa cada {@value #REVISION_MS} ms el grafo de espera y, si el mismo ciclo aparece
 * en dos revisiones seguidas, registra el conflicto. <b>Resolucion</b>: el usuario elige que operacion continua;
 * las demas se interrumpen, liberan sus recursos y regresan a la lista de espera.</p>
 */
public class GestorConflictos implements Runnable {

    private static final long REVISION_MS = 500;

    private final GestorRecursos gestor;
    private final RegistroEventos eventos;
    private final RelojSimulado reloj;
    private final long msPorPaso;

    private final Map<String, OperacionConflicto> operaciones = new ConcurrentHashMap<>();
    private final List<Conflicto> conflictos = new ArrayList<>(); // protegida por synchronized(this)
    private final AtomicLong secuenciaOperaciones = new AtomicLong(1);
    private final AtomicLong secuenciaConflictos = new AtomicLong(1);

    /** Ciclo visto en la revision anterior (para confirmar que es estable). */
    private volatile Set<String> cicloAnterior = Set.of();
    private volatile boolean activo = true;
    private Thread hilo;

    public GestorConflictos(GestorRecursos gestor, RegistroEventos eventos, RelojSimulado reloj, long msPorPaso) {
        this.gestor = gestor;
        this.eventos = eventos;
        this.reloj = reloj;
        this.msPorPaso = msPorPaso;
    }

    public synchronized void iniciar() {
        if (hilo == null) {
            hilo = new Thread(this, "Detector-deadlock");
            hilo.setDaemon(true);
            hilo.start();
        }
    }

    public synchronized void detener() {
        activo = false;
        if (hilo != null) {
            hilo.interrupt();
        }
        operaciones.values().forEach(OperacionConflicto::detener);
    }

    /**
     * Lanza las dos operaciones del escenario.
     *
     * @param conPrevencion {@code false} para provocar el deadlock; {@code true} para ver que el orden global lo evita
     */
    public List<OperacionConflicto> provocar(boolean conPrevencion) {
        long n = secuenciaOperaciones.getAndAdd(2);
        OperacionConflicto recepcion = new OperacionConflicto("OP-" + n + " Recepcion camion pesado",
                "Escanea la guia del camion y luego baja la carga con un montacargas.",
                List.of(new OperacionConflicto.Paso(TipoRecurso.ESCANER, 1, "escanear la guia del camion"),
                        new OperacionConflicto.Paso(TipoRecurso.MONTACARGAS, 1, "bajar la carga del camion")),
                conPrevencion, gestor, eventos, msPorPaso);
        OperacionConflicto despacho = new OperacionConflicto("OP-" + (n + 1) + " Despacho extra pesado",
                "Levanta una carga extra pesada con los 3 montacargas y luego verifica la guia con el escaner.",
                List.of(new OperacionConflicto.Paso(TipoRecurso.MONTACARGAS, 3, "levantar la carga extra pesada"),
                        new OperacionConflicto.Paso(TipoRecurso.ESCANER, 1, "verificar la guia de salida")),
                conPrevencion, gestor, eventos, msPorPaso);
        operaciones.put(recepcion.getNombre(), recepcion);
        operaciones.put(despacho.getNombre(), despacho);
        eventos.registrar("Se lanzan " + recepcion.getNombre() + " y " + despacho.getNombre()
                + (conPrevencion ? " CON prevencion (orden global)" : " SIN prevencion"));
        recepcion.iniciar();
        despacho.iniciar();
        return List.of(recepcion, despacho);
    }

    @Override
    public void run() {
        while (activo) {
            try {
                Thread.sleep(REVISION_MS);
                revisar();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                eventos.registrar("Error en el detector de deadlock: " + e.getMessage());
            }
        }
    }

    /** Una revision del grafo de espera. */
    private void revisar() {
        GrafoEspera grafo = GrafoEspera.construir(gestor);
        List<GrafoEspera.Arista> ciclo = grafo.buscarCiclo();
        Set<String> nodos = ciclo.stream().map(GrafoEspera.Arista::espera).collect(Collectors.toCollection(TreeSet::new));

        if (!ciclo.isEmpty() && nodos.equals(cicloAnterior) && !hayConflictoActivoCon(nodos)) {
            registrarConflicto(ciclo);
        }
        cicloAnterior = nodos;
    }

    private synchronized boolean hayConflictoActivoCon(Set<String> nodos) {
        return conflictos.stream().anyMatch(c -> !c.isResuelto() && c.getParticipantes().stream()
                .map(Conflicto.Participante::operacion).collect(Collectors.toSet()).equals(nodos));
    }

    private void registrarConflicto(List<GrafoEspera.Arista> ciclo) {
        List<Conflicto.Participante> participantes = new ArrayList<>();
        Set<String> recursos = new LinkedHashSet<>();
        StringBuilder porque = new StringBuilder("Espera circular: ");
        for (GrafoEspera.Arista a : ciclo) {
            List<String> retiene = recursosDe(a.espera());
            participantes.add(new Conflicto.Participante(a.espera(), retiene, a.recurso().getNombre(), a.retiene()));
            recursos.add(a.recurso().getNombre());
            porque.append(a.espera()).append(" retiene ").append(String.join(", ", retiene))
                    .append(" y espera ").append(a.recurso().getNombre())
                    .append(", que tiene ").append(a.retiene()).append(". ");
        }
        porque.append("Ninguna puede avanzar ni soltar lo que tiene: se cumplen las 4 condiciones de Coffman ")
                .append("(exclusion mutua, retener y esperar, no expropiacion y espera circular).");

        Conflicto conflicto = new Conflicto(secuenciaConflictos.getAndIncrement(), reloj.ahora(), participantes,
                new ArrayList<>(recursos), porque.toString());
        // Primero se marcan las operaciones y despues se publica el conflicto, para que quien lo lea
        // (la interfaz) nunca vea un conflicto cuyas operaciones aun no aparecen como bloqueadas.
        for (Conflicto.Participante p : participantes) {
            OperacionConflicto op = operaciones.get(p.operacion());
            if (op != null) {
                op.marcarBloqueada("BLOQUEADA: espera " + p.espera() + " retenido por " + p.esperaDe());
            }
        }
        synchronized (this) {
            conflictos.add(0, conflicto);
        }
        eventos.registrar("DEADLOCK detectado (conflicto #" + conflicto.getId() + ") entre "
                + participantes.stream().map(Conflicto.Participante::operacion).collect(Collectors.joining(" y ")));
    }

    /** Descripcion de lo que retiene un duenio, ej. ["3 x Montacargas"]. */
    private List<String> recursosDe(String duenio) {
        List<String> lista = new ArrayList<>();
        for (PoolRecurso pool : gestor.pools()) {
            int n = pool.unidadesDe(duenio);
            if (n > 0) {
                lista.add(n + " x " + pool.getTipo().getNombre());
            }
        }
        return lista;
    }

    /**
     * Resuelve un conflicto: {@code ganadora} se queda con los recursos disputados y las demas ceden.
     */
    public void resolver(long conflictoId, String ganadora) {
        Conflicto conflicto;
        synchronized (this) {
            conflicto = conflictos.stream().filter(c -> c.getId() == conflictoId).findFirst()
                    .orElseThrow(() -> new DatosInvalidosException("No existe el conflicto #" + conflictoId));
            if (conflicto.isResuelto()) {
                throw new DatosInvalidosException("El conflicto #" + conflictoId + " ya fue resuelto");
            }
            boolean participa = conflicto.getParticipantes().stream().anyMatch(p -> p.operacion().equals(ganadora));
            if (!participa) {
                throw new DatosInvalidosException(ganadora + " no participa en el conflicto #" + conflictoId);
            }
            conflicto.resolver(ganadora, reloj.ahora());
        }
        OperacionConflicto opGanadora = operaciones.get(ganadora);
        eventos.registrar("Conflicto #" + conflictoId + " resuelto: continua " + ganadora);
        for (Conflicto.Participante p : conflicto.getParticipantes()) {
            if (!p.operacion().equals(ganadora)) {
                OperacionConflicto victima = operaciones.get(p.operacion());
                if (victima != null) {
                    victima.ceder(opGanadora);
                }
            }
        }
        cicloAnterior = Set.of();
    }

    public List<OperacionConflicto> operaciones() {
        return operaciones.values().stream()
                .sorted((a, b) -> a.getNombre().compareTo(b.getNombre())).toList();
    }

    public synchronized List<Conflicto> conflictos() {
        return List.copyOf(conflictos);
    }

    /** Aristas actuales del grafo de espera, para dibujarlo en la interfaz. */
    public List<GrafoEspera.Arista> grafoActual() {
        return GrafoEspera.construir(gestor).getAristas();
    }

    /** Quita de la lista las operaciones ya terminadas. */
    public void limpiarTerminadas() {
        operaciones.values().removeIf(OperacionConflicto::terminada);
    }
}
