package gt.cunoc.redxela.deadlock;

import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.recursos.PoolRecurso;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grafo de espera (wait-for graph): hay una arista A -> B cuando A esta bloqueado esperando un recurso que
 * B tiene. Un ciclo en este grafo significa deadlock: cada participante espera al siguiente y nadie suelta nada.
 *
 * <p>Es una foto tomada recurso por recurso, no atomica; por eso el detector solo declara deadlock si el mismo
 * ciclo aparece en dos revisiones seguidas (un deadlock real no cambia con el tiempo).</p>
 */
public class GrafoEspera {

    /** {@code espera} esta bloqueado pidiendo {@code recurso}, que tiene {@code retiene}. */
    public record Arista(String espera, String retiene, TipoRecurso recurso) {
    }

    private final List<Arista> aristas;

    private GrafoEspera(List<Arista> aristas) {
        this.aristas = aristas;
    }

    /** Construye el grafo con quien espera y quien retiene cada recurso en este momento. */
    public static GrafoEspera construir(GestorRecursos gestor) {
        List<Arista> aristas = new ArrayList<>();
        for (PoolRecurso pool : gestor.pools()) {
            Map<String, Integer> esperando = pool.esperando();
            if (esperando.isEmpty()) {
                continue;
            }
            Set<String> duenios = new LinkedHashSet<>();
            pool.duenios().stream().filter(d -> d != null).forEach(duenios::add);
            for (String espera : esperando.keySet()) {
                for (String retiene : duenios) {
                    if (!espera.equals(retiene)) {
                        aristas.add(new Arista(espera, retiene, pool.getTipo()));
                    }
                }
            }
        }
        return new GrafoEspera(aristas);
    }

    public List<Arista> getAristas() {
        return aristas;
    }

    /**
     * Busca un ciclo con busqueda en profundidad (DFS): si al recorrer se llega a un nodo que esta en el camino
     * actual, las aristas desde ese nodo hasta el final del camino forman el ciclo.
     *
     * @return aristas del ciclo en orden, o lista vacia si no hay
     */
    public List<Arista> buscarCiclo() {
        Map<String, List<Arista>> salientes = new HashMap<>();
        for (Arista a : aristas) {
            salientes.computeIfAbsent(a.espera(), k -> new ArrayList<>()).add(a);
        }
        Set<String> visitados = new HashSet<>();
        for (String inicio : salientes.keySet()) {
            if (!visitados.contains(inicio)) {
                List<Arista> ciclo = dfs(inicio, salientes, visitados, new LinkedHashSet<>(), new ArrayDeque<>());
                if (!ciclo.isEmpty()) {
                    return ciclo;
                }
            }
        }
        return List.of();
    }

    private List<Arista> dfs(String nodo, Map<String, List<Arista>> salientes, Set<String> visitados,
                             Set<String> enCamino, Deque<Arista> camino) {
        visitados.add(nodo);
        enCamino.add(nodo);
        for (Arista a : salientes.getOrDefault(nodo, List.of())) {
            camino.addLast(a);
            if (enCamino.contains(a.retiene())) {
                // Ciclo: se recorta el camino desde la arista que sale del nodo repetido.
                List<Arista> ciclo = new ArrayList<>(camino);
                int desde = 0;
                while (!ciclo.get(desde).espera().equals(a.retiene())) {
                    desde++;
                }
                return ciclo.subList(desde, ciclo.size());
            }
            if (!visitados.contains(a.retiene())) {
                List<Arista> ciclo = dfs(a.retiene(), salientes, visitados, enCamino, camino);
                if (!ciclo.isEmpty()) {
                    return ciclo;
                }
            }
            camino.removeLast();
        }
        enCamino.remove(nodo);
        return List.of();
    }
}
