package gt.cunoc.redxela.generador;

import gt.cunoc.redxela.modelo.Catalogo;
import gt.cunoc.redxela.modelo.Cliente;
import gt.cunoc.redxela.modelo.LineaPedido;
import gt.cunoc.redxela.modelo.NivelServicio;
import gt.cunoc.redxela.modelo.Producto;
import gt.cunoc.redxela.modelo.RegistroClientes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hilo que simula la llegada continua de pedidos de todos los niveles, sin limite de cantidad.
 *
 * <p>El tiempo entre llegadas sigue una distribucion exponencial (llegadas de Poisson), que es el modelo clasico de
 * llegadas aleatorias e independientes. Se puede iniciar, detener y cambiar la tasa en caliente.</p>
 */
public class GeneradorPedidos implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GeneradorPedidos.class);

    /** Probabilidad de cada nivel de servicio (Expres ... Economico). */
    private static final double[] PESOS_NIVEL = {0.10, 0.20, 0.35, 0.20, 0.15};

    /** Recibe los datos generados y crea el pedido en la simulacion. */
    @FunctionalInterface
    public interface Destino {
        void ingresar(Cliente cliente, NivelServicio nivel, List<LineaPedido> lineas);
    }

    private final Catalogo catalogo;
    private final RegistroClientes clientes;
    private final Destino destino;

    private volatile long intervaloMedioMs;
    private volatile boolean activo;
    private Thread hilo;

    public GeneradorPedidos(Catalogo catalogo, RegistroClientes clientes, Destino destino, long intervaloMedioMs) {
        this.catalogo = catalogo;
        this.clientes = clientes;
        this.destino = destino;
        setIntervaloMedioMs(intervaloMedioMs);
    }

    public synchronized void iniciar() {
        if (activo) {
            return;
        }
        activo = true;
        hilo = new Thread(this, "Generador");
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
        while (activo) {
            try {
                // Muestra exponencial: -media * ln(U), U en (0,1]
                long espera = (long) (-intervaloMedioMs * Math.log(1.0 - rnd.nextDouble()));
                Thread.sleep(Math.max(20, espera));
                generarUno(rnd);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // Un pedido mal generado no debe detener al generador.
                log.warn("No se pudo generar pedido", e);
            }
        }
    }

    private void generarUno(ThreadLocalRandom rnd) {
        NivelServicio nivel = nivelAleatorio(rnd);
        // Se busca un cliente que haya contratado ese nivel; si no hay, se toma cualquiera con su propio nivel.
        List<Cliente> candidatos = clientes.todos().stream().filter(c -> c.nivelContratado() == nivel).toList();
        Cliente cliente;
        if (candidatos.isEmpty()) {
            List<Cliente> todos = clientes.todos();
            cliente = todos.get(rnd.nextInt(todos.size()));
        } else {
            cliente = candidatos.get(rnd.nextInt(candidatos.size()));
        }

        List<Producto> productos = catalogo.todos();
        int numLineas = 1 + rnd.nextInt(3);
        List<LineaPedido> lineas = new ArrayList<>();
        for (int i = 0; i < numLineas; i++) {
            lineas.add(new LineaPedido(productos.get(rnd.nextInt(productos.size())), 1 + rnd.nextInt(8)));
        }
        destino.ingresar(cliente, cliente.nivelContratado(), lineas);
    }

    private static NivelServicio nivelAleatorio(ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        double acumulado = 0;
        NivelServicio[] niveles = NivelServicio.values();
        for (int i = 0; i < niveles.length; i++) {
            acumulado += PESOS_NIVEL[i];
            if (r < acumulado) {
                return niveles[i];
            }
        }
        return NivelServicio.ECONOMICO;
    }

    public boolean isActivo() {
        return activo;
    }

    public long getIntervaloMedioMs() {
        return intervaloMedioMs;
    }

    public void setIntervaloMedioMs(long intervaloMedioMs) {
        if (intervaloMedioMs < 50) {
            throw new IllegalArgumentException("El intervalo medio debe ser de al menos 50 ms");
        }
        this.intervaloMedioMs = intervaloMedioMs;
    }
}
