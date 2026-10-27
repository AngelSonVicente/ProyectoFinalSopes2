package gt.cunoc.redxela.simulacion;

import gt.cunoc.redxela.estadisticas.Estadisticas;
import gt.cunoc.redxela.excepciones.DatosInvalidosException;
import gt.cunoc.redxela.generador.GeneradorPedidos;
import gt.cunoc.redxela.modelo.Catalogo;
import gt.cunoc.redxela.modelo.Cliente;
import gt.cunoc.redxela.modelo.LineaPedido;
import gt.cunoc.redxela.modelo.NivelServicio;
import gt.cunoc.redxela.modelo.Pedido;
import gt.cunoc.redxela.modelo.RegistroClientes;
import gt.cunoc.redxela.planificacion.ColaEspera;
import gt.cunoc.redxela.planificacion.Despachador;
import gt.cunoc.redxela.planificacion.PoliticaPlanificacion;
import gt.cunoc.redxela.recursos.GestorRecursos;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fachada de la simulacion: crea y conecta todos los componentes, arranca y detiene sus hilos,
 * y es el unico punto de entrada que usa la API.
 *
 * <p>Hilos que viven aqui: Despachador (planificador), Trabajador-N (pool de procesamiento),
 * Generador (llegada automatica de pedidos) y Muestreo (ocupacion de recursos para estadisticas).</p>
 */
@Component
public class Simulacion {

    private static final long MUESTREO_MS = 250;

    private final RelojSimulado reloj;
    private final RegistroEventos eventos;
    private final Catalogo catalogo = new Catalogo();
    private final RegistroClientes clientes = new RegistroClientes();
    private final GestorRecursos gestor = new GestorRecursos();
    private final ColaEspera cola = new ColaEspera();
    private final Estadisticas estadisticas = new Estadisticas();
    private final Despachador despachador;
    private final GeneradorPedidos generador;
    private final ScheduledExecutorService muestreo = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Muestreo");
        t.setDaemon(true);
        return t;
    });
    private final boolean autoIniciarGenerador;

    public Simulacion(@Value("${redxela.reloj.factor:60}") double factorReloj,
                      @Value("${redxela.trabajo.ms-por-unidad:250}") long msPorUnidad,
                      @Value("${redxela.generador.intervalo-ms:1500}") long intervaloGenerador,
                      @Value("${redxela.generador.auto-iniciar:false}") boolean autoIniciarGenerador) {
        this.reloj = new RelojSimulado(factorReloj);
        this.eventos = new RegistroEventos(reloj);
        this.despachador = new Despachador(cola, gestor, reloj, estadisticas, eventos, msPorUnidad);
        this.generador = new GeneradorPedidos(catalogo, clientes,
                (cliente, nivel, lineas) -> ingresar(cliente, nivel, lineas, false), intervaloGenerador);
        this.autoIniciarGenerador = autoIniciarGenerador;
        // Cada liberacion de recursos despierta al despachador para que reintente con los pedidos en espera.
        gestor.setAlLiberar(cola::notificarCambio);
    }

    @PostConstruct
    public void iniciar() {
        despachador.iniciar();
        muestreo.scheduleAtFixedRate(() -> estadisticas.muestrearOcupacion(gestor, despachador.getPolitica()),
                MUESTREO_MS, MUESTREO_MS, TimeUnit.MILLISECONDS);
        if (autoIniciarGenerador) {
            generador.iniciar();
        }
    }

    @PreDestroy
    public void detener() {
        generador.detener();
        muestreo.shutdownNow();
        despachador.detener();
    }

    /** Crea un pedido y lo pone en la lista de espera. Lo usan el generador y el ingreso manual. */
    public Pedido ingresar(Cliente cliente, NivelServicio nivel, List<LineaPedido> lineas, boolean manual) {
        Pedido pedido = new Pedido(cliente, nivel, lineas, manual, reloj.ahora());
        estadisticas.registrarIngreso();
        cola.agregar(pedido);
        eventos.registrar(pedido.etiqueta() + (manual ? " ingresado manualmente" : " generado") + " para "
                + cliente.nombre() + " (" + nivel.getNombre() + ", " + pedido.getTotalArticulos() + " art.)");
        return pedido;
    }

    /**
     * Ingreso manual desde la interfaz.
     *
     * @param clienteId   cuenta del cliente
     * @param nivel       nivel de servicio (1-5); si es {@code null} se usa el contratado por el cliente
     * @param productos   codigo de producto -> cantidad de unidades
     */
    public Pedido crearPedidoManual(long clienteId, Integer nivel, Map<String, Integer> productos) {
        if (productos == null || productos.isEmpty()) {
            throw new DatosInvalidosException("El pedido debe incluir al menos un producto");
        }
        Cliente cliente = clientes.buscar(clienteId);
        NivelServicio nivelServicio;
        try {
            nivelServicio = nivel == null ? cliente.nivelContratado() : NivelServicio.deNumero(nivel);
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException(e.getMessage());
        }
        List<LineaPedido> lineas = new ArrayList<>();
        productos.forEach((codigo, cantidad) -> {
            if (cantidad == null || cantidad <= 0) {
                throw new DatosInvalidosException("Cantidad invalida para " + codigo);
            }
            lineas.add(new LineaPedido(catalogo.buscar(codigo), cantidad));
        });
        return ingresar(cliente, nivelServicio, lineas, true);
    }

    public void cambiarPolitica(PoliticaPlanificacion politica) {
        despachador.setPolitica(politica);
    }

    public RelojSimulado getReloj() {
        return reloj;
    }

    public RegistroEventos getEventos() {
        return eventos;
    }

    public Catalogo getCatalogo() {
        return catalogo;
    }

    public RegistroClientes getClientes() {
        return clientes;
    }

    public GestorRecursos getGestor() {
        return gestor;
    }

    public ColaEspera getCola() {
        return cola;
    }

    public Estadisticas getEstadisticas() {
        return estadisticas;
    }

    public Despachador getDespachador() {
        return despachador;
    }

    public GeneradorPedidos getGenerador() {
        return generador;
    }
}
