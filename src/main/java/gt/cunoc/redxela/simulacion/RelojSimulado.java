package gt.cunoc.redxela.simulacion;

/**
 * Reloj de la simulacion. Acelera el tiempo real por un factor para que los SLA (10, 30, 60, 120 minutos)
 * se puedan observar en una demostracion. Con factor 60, un segundo real equivale a un minuto simulado.
 *
 * <p>Usa {@link System#nanoTime()} porque es monotono (no cambia si se ajusta la hora del sistema).
 * Solo tiene campos finales, asi que es seguro usarlo desde cualquier hilo.</p>
 */
public class RelojSimulado {

    private final long inicioNano = System.nanoTime();
    private final double factor;

    public RelojSimulado(double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("El factor del reloj debe ser positivo");
        }
        this.factor = factor;
    }

    /** Milisegundos simulados transcurridos desde que inicio la simulacion. */
    public long ahora() {
        double realMs = (System.nanoTime() - inicioNano) / 1_000_000.0;
        return (long) (realMs * factor);
    }

    /** Convierte una duracion real (ms) a ms simulados. */
    public long aSimulado(long realMs) {
        return (long) (realMs * factor);
    }

    public double getFactor() {
        return factor;
    }
}
