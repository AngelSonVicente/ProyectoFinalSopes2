package gt.cunoc.redxela.inventario;

import gt.cunoc.redxela.excepciones.DatosInvalidosException;
import gt.cunoc.redxela.modelo.Producto;
import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.recursos.PoolRecurso;
import gt.cunoc.redxela.simulacion.RegistroEventos;
import gt.cunoc.redxela.simulacion.RelojSimulado;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Area de Recepcion de Mercaderia: el PRODUCTOR.
 *
 * <p>Los camiones llegan al patio ({@code patio}). El hilo de Recepcion toma uno, escanea la mercaderia con el
 * escaner compartido (el mismo que usan los pedidos electronicos) y deja un {@link AvisoLlegada} en el buzon hacia
 * Inventario. Recepcion nunca toca las existencias ni el almacen: solo envia mensajes.</p>
 *
 * <p>Si Recepcion produce mas rapido de lo que Inventario consume, el buzon (acotado) se llena. Entonces
 * {@code offer(aviso, timeout)} espera un tiempo limitado y vuelve a intentar con el MISMO aviso: no se descarta
 * ningun dato y el hilo nunca queda bloqueado para siempre (puede revisar si debe detenerse entre intentos).</p>
 */
public class Recepcion implements Runnable {

    private static final String DUENIO = "Recepcion";
    private static final long REINTENTO_MS = 500;

    /** Camion esperando en el patio para ser descargado. */
    private record Camion(Producto producto, int cantidad) {
    }

    private final BlockingQueue<Camion> patio = new LinkedBlockingQueue<>();
    private final BlockingQueue<AvisoLlegada> buzon;
    private final GestorRecursos gestor;
    private final RelojSimulado reloj;
    private final RegistroEventos eventos;
    private final long msEscaneo;

    private final AtomicLong secuenciaAvisos = new AtomicLong(1);
    private final AtomicLong avisosEnviados = new AtomicLong();
    private final AtomicLong esperasBuzonLleno = new AtomicLong();
    private volatile String actividad = "Libre";
    private volatile boolean activo = true;
    private Thread hilo;

    public Recepcion(BlockingQueue<AvisoLlegada> buzon, GestorRecursos gestor, RelojSimulado reloj,
                     RegistroEventos eventos, long msEscaneo) {
        this.buzon = buzon;
        this.gestor = gestor;
        this.reloj = reloj;
        this.eventos = eventos;
        this.msEscaneo = msEscaneo;
    }

    public synchronized void iniciar() {
        if (hilo == null) {
            hilo = new Thread(this, "Recepcion");
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

    /** Llega un camion con mercaderia. Lo llama la interfaz o el generador de camiones. */
    public void registrarLlegada(Producto producto, int cantidad) {
        if (cantidad <= 0) {
            throw new DatosInvalidosException("La cantidad recibida debe ser mayor a cero");
        }
        patio.add(new Camion(producto, cantidad));
        eventos.registrar("Camion con " + cantidad + " x " + producto.nombre() + " llega al patio");
    }

    @Override
    public void run() {
        while (activo) {
            try {
                actividad = "Esperando camiones";
                Camion camion = patio.take();
                escanear(camion);
                enviar(new AvisoLlegada(secuenciaAvisos.getAndIncrement(), camion.producto(), camion.cantidad(),
                        reloj.ahora()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                eventos.registrar("Error en Recepcion: " + e.getMessage());
            }
        }
    }

    /** Usa el escaner compartido (exclusion mutua) para registrar la mercaderia del camion. */
    private void escanear(Camion camion) throws InterruptedException {
        PoolRecurso escaner = gestor.pool(TipoRecurso.ESCANER);
        actividad = "Esperando escaner";
        while (!escaner.adquirir(DUENIO, 1, REINTENTO_MS)) {
            if (!activo) {
                throw new InterruptedException();
            }
        }
        try {
            actividad = "Escaneando " + camion.producto().nombre();
            Thread.sleep(msEscaneo);
        } finally {
            gestor.liberar(DUENIO, TipoRecurso.ESCANER);
        }
    }

    /** Deposita el aviso en el buzon; si esta lleno, espera con tiempo limite y reintenta sin perder el aviso. */
    private void enviar(AvisoLlegada aviso) throws InterruptedException {
        actividad = "Enviando aviso #" + aviso.id();
        if (!buzon.offer(aviso)) {
            // Primer intento sin espera fallo: el consumidor va atrasado.
            esperasBuzonLleno.incrementAndGet();
            actividad = "Buzon lleno: esperando a Inventario (aviso #" + aviso.id() + ")";
            eventos.registrar("Buzon a Inventario lleno; Recepcion espera para enviar el aviso #" + aviso.id());
            while (!buzon.offer(aviso, REINTENTO_MS, TimeUnit.MILLISECONDS)) {
                if (!activo) {
                    throw new InterruptedException();
                }
            }
        }
        avisosEnviados.incrementAndGet();
        eventos.registrar("Recepcion envia aviso #" + aviso.id() + ": " + aviso.cantidad() + " x "
                + aviso.producto().nombre());
    }

    public List<String> camionesEnPatio() {
        return patio.stream().map(c -> c.cantidad() + " x " + c.producto().nombre()).toList();
    }

    public long getAvisosEnviados() {
        return avisosEnviados.get();
    }

    public long getEsperasBuzonLleno() {
        return esperasBuzonLleno.get();
    }

    public String getActividad() {
        return actividad;
    }
}
