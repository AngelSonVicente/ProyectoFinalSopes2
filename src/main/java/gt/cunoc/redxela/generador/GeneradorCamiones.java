package gt.cunoc.redxela.generador;

import gt.cunoc.redxela.modelo.Catalogo;
import gt.cunoc.redxela.modelo.Producto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Hilo que simula la llegada periodica de camiones con mercaderia, para que el inventario se reabastezca
 * mientras el generador de pedidos lo consume. Igual que los pedidos, las llegadas son exponenciales.
 */
public class GeneradorCamiones implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GeneradorCamiones.class);

    private final Catalogo catalogo;
    /** Recibe (producto, cantidad) y lo registra en Recepcion. */
    private final BiConsumer<Producto, Integer> destino;

    private volatile long intervaloMedioMs;
    private volatile boolean activo;
    private Thread hilo;

    public GeneradorCamiones(Catalogo catalogo, BiConsumer<Producto, Integer> destino, long intervaloMedioMs) {
        this.catalogo = catalogo;
        this.destino = destino;
        this.intervaloMedioMs = intervaloMedioMs;
    }

    public synchronized void iniciar() {
        if (activo) {
            return;
        }
        activo = true;
        hilo = new Thread(this, "Camiones");
        hilo.setDaemon(true);
        hilo.start();
    }

    public synchronized void detener() {
        activo = false;
        if (hilo != null) {
            hilo.interrupt();
            hilo = null;
        }
    }

    @Override
    public void run() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Producto> productos = catalogo.todos();
        while (activo) {
            try {
                long espera = (long) (-intervaloMedioMs * Math.log(1.0 - rnd.nextDouble()));
                Thread.sleep(Math.max(50, espera));
                destino.accept(productos.get(rnd.nextInt(productos.size())), 10 + rnd.nextInt(31));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.warn("No se pudo generar camion", e);
            }
        }
    }

    public boolean isActivo() {
        return activo;
    }

    public long getIntervaloMedioMs() {
        return intervaloMedioMs;
    }

    public void setIntervaloMedioMs(long intervaloMedioMs) {
        if (intervaloMedioMs < 100) {
            throw new IllegalArgumentException("El intervalo medio de camiones debe ser de al menos 100 ms");
        }
        this.intervaloMedioMs = intervaloMedioMs;
    }
}
