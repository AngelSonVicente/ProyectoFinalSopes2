package gt.cunoc.redxela.inventario;

import gt.cunoc.redxela.simulacion.RegistroEventos;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Area de Control de Inventario: el CONSUMIDOR.
 *
 * <p>Saca avisos del buzon a su propio ritmo (mas lento que Recepcion a proposito, para poder mostrar el buzon
 * lleno), actualiza existencias, asigna ubicaciones en el almacen y dispara la reevaluacion de los pedidos que
 * esperaban ese producto. Usa {@code poll} con tiempo limite en vez de {@code take} para poder revisar
 * periodicamente si debe detenerse.</p>
 */
public class ControlInventario implements Runnable {

    private static final long ESPERA_MS = 500;

    private final BlockingQueue<AvisoLlegada> buzon;
    private final Inventario inventario;
    private final RegistroEventos eventos;
    private final long msProcesamiento;

    private final AtomicLong avisosProcesados = new AtomicLong();
    private volatile String actividad = "Libre";
    private volatile boolean activo = true;
    private Thread hilo;

    public ControlInventario(BlockingQueue<AvisoLlegada> buzon, Inventario inventario, RegistroEventos eventos,
                             long msProcesamiento) {
        this.buzon = buzon;
        this.inventario = inventario;
        this.eventos = eventos;
        this.msProcesamiento = msProcesamiento;
    }

    public synchronized void iniciar() {
        if (hilo == null) {
            hilo = new Thread(this, "Inventario");
            hilo.setDaemon(true);
            hilo.start();
        }
    }

    public synchronized void detener() {
        activo = false;
        if (hilo != null) {
            hilo.interrupt();
        }
    }

    @Override
    public void run() {
        while (activo) {
            try {
                actividad = "Esperando avisos";
                AvisoLlegada aviso = buzon.poll(ESPERA_MS, TimeUnit.MILLISECONDS);
                if (aviso == null) {
                    continue;
                }
                actividad = "Procesando aviso #" + aviso.id() + " (" + aviso.producto().nombre() + ")";
                Thread.sleep(msProcesamiento); // tiempo de registrar y asignar ubicaciones
                int ubicadas = inventario.registrarEntrada(aviso.producto(), aviso.cantidad(), aviso.instante());
                avisosProcesados.incrementAndGet();
                String detalle = ubicadas == aviso.cantidad() ? ""
                        : " (" + (aviso.cantidad() - ubicadas) + " pendientes de ubicar: almacen lleno)";
                eventos.registrar("Inventario procesa aviso #" + aviso.id() + ": +" + ubicadas + " "
                        + aviso.producto().nombre() + detalle);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                eventos.registrar("Error en Inventario: " + e.getMessage());
            }
        }
    }

    public long getAvisosProcesados() {
        return avisosProcesados.get();
    }

    public String getActividad() {
        return actividad;
    }
}
