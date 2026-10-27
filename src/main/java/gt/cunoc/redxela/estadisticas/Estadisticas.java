package gt.cunoc.redxela.estadisticas;

import gt.cunoc.redxela.modelo.NivelServicio;
import gt.cunoc.redxela.modelo.Pedido;
import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.planificacion.PoliticaPlanificacion;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.recursos.PoolRecurso;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estadisticas del sistema, separadas por politica de planificacion para poder compararlas.
 *
 * <p>Cada pedido completado se acumula en la politica con la que fue despachado. La ocupacion de recursos se
 * obtiene por muestreo periodico (fraccion de unidades ocupadas) y se acumula en la politica vigente.
 * Todos los metodos son {@code synchronized}: escriben los trabajadores y el muestreador, y lee la API.</p>
 */
public class Estadisticas {

    /** Acumuladores de una politica. */
    private static final class Acumulador {
        long completados;
        double sumaEsperaMin;
        double sumaProcesoMin;
        final Map<NivelServicio, Long> completadosNivel = new EnumMap<>(NivelServicio.class);
        final Map<NivelServicio, Long> cumplidosSlaNivel = new EnumMap<>(NivelServicio.class);
        final Map<NivelServicio, Double> esperaNivel = new EnumMap<>(NivelServicio.class);
        final Map<TipoRecurso, Double> sumaOcupacion = new EnumMap<>(TipoRecurso.class);
        long muestras;
    }

    /** Resumen de un nivel de servicio dentro de una politica. */
    public record ResumenNivel(long completados, double esperaPromedioMin, double cumplimientoSla) {
    }

    /** Resumen de una politica listo para la interfaz. */
    public record ResumenPolitica(String politica, String nombre, long completados, double esperaPromedioMin,
                                  double procesoPromedioMin, double cumplimientoSla,
                                  Map<NivelServicio, ResumenNivel> porNivel,
                                  Map<TipoRecurso, Double> ocupacionPromedio) {
    }

    private final Map<PoliticaPlanificacion, Acumulador> porPolitica = new EnumMap<>(PoliticaPlanificacion.class);
    private final Map<TipoRecurso, Double> ocupacionActual = new EnumMap<>(TipoRecurso.class);
    private long totalIngresados;

    public Estadisticas() {
        for (PoliticaPlanificacion p : PoliticaPlanificacion.values()) {
            porPolitica.put(p, new Acumulador());
        }
    }

    public synchronized void registrarIngreso() {
        totalIngresados++;
    }

    /** Acumula un pedido terminado en la politica con la que se despacho. */
    public synchronized void registrarCompletado(Pedido p) {
        Acumulador a = porPolitica.get(PoliticaPlanificacion.valueOf(p.getPoliticaAplicada()));
        double espera = (p.getInicio() - p.getLlegada()) / 60_000.0;
        double proceso = (p.getFin() - p.getInicio()) / 60_000.0;
        a.completados++;
        a.sumaEsperaMin += espera;
        a.sumaProcesoMin += proceso;
        a.completadosNivel.merge(p.getNivel(), 1L, Long::sum);
        a.esperaNivel.merge(p.getNivel(), espera, Double::sum);
        if (espera <= p.getNivel().getMaxEsperaMinutos()) {
            a.cumplidosSlaNivel.merge(p.getNivel(), 1L, Long::sum);
        }
    }

    /** Toma una muestra de la ocupacion de cada recurso y la acumula en la politica vigente. */
    public synchronized void muestrearOcupacion(GestorRecursos gestor, PoliticaPlanificacion vigente) {
        Acumulador a = porPolitica.get(vigente);
        for (PoolRecurso pool : gestor.pools()) {
            double fraccion = (double) pool.ocupados() / pool.getTipo().getCapacidad();
            ocupacionActual.put(pool.getTipo(), fraccion);
            a.sumaOcupacion.merge(pool.getTipo(), fraccion, Double::sum);
        }
        a.muestras++;
    }

    public synchronized long getTotalIngresados() {
        return totalIngresados;
    }

    public synchronized Map<TipoRecurso, Double> ocupacionActual() {
        return new EnumMap<>(ocupacionActual);
    }

    /** Resumen de una politica. */
    public synchronized ResumenPolitica resumen(PoliticaPlanificacion politica) {
        Acumulador a = porPolitica.get(politica);
        Map<NivelServicio, ResumenNivel> niveles = new LinkedHashMap<>();
        long cumplidos = 0;
        for (NivelServicio n : NivelServicio.values()) {
            long c = a.completadosNivel.getOrDefault(n, 0L);
            long ok = a.cumplidosSlaNivel.getOrDefault(n, 0L);
            cumplidos += ok;
            niveles.put(n, new ResumenNivel(c, c == 0 ? 0 : a.esperaNivel.getOrDefault(n, 0.0) / c,
                    c == 0 ? 0 : (double) ok / c));
        }
        Map<TipoRecurso, Double> ocupacion = new EnumMap<>(TipoRecurso.class);
        for (TipoRecurso t : TipoRecurso.values()) {
            ocupacion.put(t, a.muestras == 0 ? 0 : a.sumaOcupacion.getOrDefault(t, 0.0) / a.muestras);
        }
        long n = a.completados;
        return new ResumenPolitica(politica.name(), politica.getNombre(), n,
                n == 0 ? 0 : a.sumaEsperaMin / n,
                n == 0 ? 0 : a.sumaProcesoMin / n,
                n == 0 ? 0 : (double) cumplidos / n,
                niveles, ocupacion);
    }

    /** Resumen de todas las politicas, para la tabla comparativa. */
    public synchronized Map<PoliticaPlanificacion, ResumenPolitica> comparacion() {
        Map<PoliticaPlanificacion, ResumenPolitica> mapa = new EnumMap<>(PoliticaPlanificacion.class);
        for (PoliticaPlanificacion p : PoliticaPlanificacion.values()) {
            mapa.put(p, resumen(p));
        }
        return mapa;
    }
}
