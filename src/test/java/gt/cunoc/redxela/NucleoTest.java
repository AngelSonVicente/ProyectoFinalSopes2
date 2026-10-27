package gt.cunoc.redxela;

import gt.cunoc.redxela.modelo.EstadoPedido;
import gt.cunoc.redxela.modelo.Pedido;
import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.planificacion.PoliticaPlanificacion;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.simulacion.Simulacion;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del nucleo sin levantar Spring. */
class NucleoTest {

    @Test
    void reservaEsTodoONada() {
        GestorRecursos gestor = new GestorRecursos();
        assertTrue(gestor.intentarReservar("A", Map.of(TipoRecurso.AREA_CARGA, 2)));
        // B necesita encargado + area de carga: no hay area, por lo tanto tampoco debe quedarse con el encargado.
        assertFalse(gestor.intentarReservar("B", Map.of(TipoRecurso.ENCARGADO, 1, TipoRecurso.AREA_CARGA, 1)));
        assertEquals(0, gestor.pool(TipoRecurso.ENCARGADO).ocupados());

        gestor.liberarTodo("A");
        assertTrue(gestor.intentarReservar("B", Map.of(TipoRecurso.ENCARGADO, 1, TipoRecurso.AREA_CARGA, 1)));
        assertEquals("B", gestor.pool(TipoRecurso.AREA_CARGA).duenios().get(0));
    }

    @Test
    void procesaPedidosYLiberaTodo() throws InterruptedException {
        // Reloj rapido y poco trabajo por unidad para que la prueba dure poco.
        Simulacion sim = new Simulacion(60, 20, 1000, false, 20, 50, 5, 1000);
        sim.iniciar();
        try {
            sim.cambiarPolitica(PoliticaPlanificacion.MENOS_ARTICULOS);
            Pedido p1 = sim.crearPedidoManual(1, null, Map.of("P-008", 3, "P-012", 2)); // pesado + voluminoso
            Pedido p2 = sim.crearPedidoManual(2, 1, Map.of("P-009", 4));               // electronico expres
            for (int i = 0; i < 50 && !(terminado(p1) && terminado(p2)); i++) {
                Thread.sleep(100);
            }
            assertEquals(EstadoPedido.COMPLETADO, p1.getEstado());
            assertEquals(EstadoPedido.COMPLETADO, p2.getEstado());
            for (TipoRecurso t : TipoRecurso.values()) {
                assertEquals(0, sim.getGestor().pool(t).ocupados(), "Quedo ocupado " + t);
            }
            assertEquals(2, sim.getEstadisticas().resumen(PoliticaPlanificacion.MENOS_ARTICULOS).completados());
        } finally {
            sim.detener();
        }
    }

    private static boolean terminado(Pedido p) {
        return p.getEstado() == EstadoPedido.COMPLETADO || p.getEstado() == EstadoPedido.CANCELADO;
    }
}
