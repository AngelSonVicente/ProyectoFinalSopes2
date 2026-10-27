package gt.cunoc.redxela;

import gt.cunoc.redxela.almacen.Almacen;
import gt.cunoc.redxela.modelo.Catalogo;
import gt.cunoc.redxela.modelo.EstadoPedido;
import gt.cunoc.redxela.modelo.Pedido;
import gt.cunoc.redxela.modelo.Producto;
import gt.cunoc.redxela.simulacion.Simulacion;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de inventario, almacen y comunicacion Recepcion -> Inventario. */
class InventarioTest {

    @Test
    void loteReutilizaHuecosNoContiguos() {
        Catalogo catalogo = new Catalogo();
        Producto libros = catalogo.buscar("P-003");   // volumen 2 -> 5 unidades por ubicacion
        Producto cemento = catalogo.buscar("P-007");  // volumen 2 -> 5 unidades por ubicacion
        Almacen almacen = new Almacen();

        almacen.almacenar(libros, 10, 0);   // ubicaciones 0 y 1
        almacen.almacenar(cemento, 10, 0);  // ubicaciones 2 y 3
        almacen.almacenar(libros, 5, 0);    // ubicacion 4
        almacen.retirar("P-003", 10);       // libera 0 y 1 (lote mas antiguo)

        // Un lote de 15 unidades usa 0, 1 (huecos liberados) y 5: no contiguo.
        assertEquals(15, almacen.almacenar(cemento, 15, 1));
        var ultimo = almacen.lotes().get(almacen.lotes().size() - 1);
        assertEquals(java.util.List.of(0, 1, 5), ultimo.getTablaUbicaciones());
    }

    @Test
    void rafagaNoPierdeAvisosYPedidoEsperaStock() throws InterruptedException {
        // Buzon de 2 e Inventario lento: Recepcion se adelanta y tiene que esperar.
        Simulacion sim = new Simulacion(60, 10, 1000, false, 5, 40, 2, 1000, 300);
        sim.iniciar();
        try {
            // P-013 arranca sin existencias: el pedido debe quedar esperando stock.
            Pedido pedido = sim.crearPedidoManual(3, null, Map.of("P-013", 4));
            esperar(() -> pedido.getEstado() == EstadoPedido.ESPERANDO_STOCK, 3000);
            assertEquals(EstadoPedido.ESPERANDO_STOCK, pedido.getEstado());

            int camiones = 12;
            for (int i = 0; i < camiones; i++) {
                sim.registrarLlegada("P-013", 1);
            }
            esperar(() -> sim.getControlInventario().getAvisosProcesados() == camiones, 10_000);

            assertEquals(camiones, sim.getRecepcion().getAvisosEnviados());
            assertEquals(camiones, sim.getControlInventario().getAvisosProcesados());
            assertTrue(sim.getRecepcion().getEsperasBuzonLleno() > 0, "El buzon nunca se lleno");

            esperar(() -> pedido.getEstado() == EstadoPedido.COMPLETADO, 5000);
            assertEquals(EstadoPedido.COMPLETADO, pedido.getEstado());
            assertEquals(camiones - 4, sim.getInventario().existencias("P-013"));
            assertNotEquals(0, sim.getInventario().lotes().size());
        } finally {
            sim.detener();
        }
    }

    private static void esperar(BooleanSupplier condicion, long maxMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + maxMs;
        while (!condicion.getAsBoolean() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
    }
}
