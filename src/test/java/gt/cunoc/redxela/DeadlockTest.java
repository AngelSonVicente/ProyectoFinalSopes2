package gt.cunoc.redxela;

import gt.cunoc.redxela.deadlock.Conflicto;
import gt.cunoc.redxela.deadlock.GestorConflictos;
import gt.cunoc.redxela.deadlock.OperacionConflicto;
import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.simulacion.Simulacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del conflicto de recursos: provocarlo, detectarlo, resolverlo y evitarlo con prevencion. */
class DeadlockTest {

    private Simulacion sim;
    private GestorConflictos conflictos;

    @BeforeEach
    void iniciar() {
        sim = new Simulacion(60, 20, 1000, false, 20, 50, 5, 1000, 300);
        sim.iniciar();
        conflictos = sim.getConflictos();
    }

    @AfterEach
    void detener() {
        sim.detener();
    }

    @Test
    void sinPrevencionSeDetectaYSeResuelve() throws InterruptedException {
        List<OperacionConflicto> ops = conflictos.provocar(false);
        esperar(() -> !conflictos.conflictos().isEmpty(), 5000);

        assertEquals(1, conflictos.conflictos().size(), "No se detecto el deadlock");
        Conflicto c = conflictos.conflictos().get(0);
        assertEquals(2, c.getParticipantes().size());
        assertTrue(c.getRecursos().contains(TipoRecurso.ESCANER.getNombre()));
        assertTrue(c.getRecursos().contains(TipoRecurso.MONTACARGAS.getNombre()));
        ops.forEach(op -> assertEquals(OperacionConflicto.Estado.BLOQUEADA, op.getEstado()));

        // Continua el despacho; la recepcion cede, espera y luego reintenta.
        OperacionConflicto despacho = ops.get(1);
        OperacionConflicto recepcion = ops.get(0);
        conflictos.resolver(c.getId(), despacho.getNombre());
        esperar(() -> despacho.terminada() && recepcion.terminada(), 5000);

        assertEquals(OperacionConflicto.Estado.COMPLETADA, despacho.getEstado());
        assertEquals(OperacionConflicto.Estado.COMPLETADA, recepcion.getEstado());
        assertEquals(2, recepcion.getIntentos(), "La operacion que cedio debio reintentar");
        assertTrue(c.isResuelto());
        for (TipoRecurso t : TipoRecurso.values()) {
            assertEquals(0, sim.getGestor().pool(t).ocupados(), "Quedo ocupado " + t);
        }
    }

    @Test
    void conPrevencionNoHayDeadlock() throws InterruptedException {
        List<OperacionConflicto> ops = conflictos.provocar(true);
        esperar(() -> ops.stream().allMatch(OperacionConflicto::terminada), 5000);

        ops.forEach(op -> assertEquals(OperacionConflicto.Estado.COMPLETADA, op.getEstado()));
        assertTrue(conflictos.conflictos().isEmpty(), "Con orden global no debe haber conflicto");
    }

    private static void esperar(BooleanSupplier condicion, long maxMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + maxMs;
        while (!condicion.getAsBoolean() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
    }
}
