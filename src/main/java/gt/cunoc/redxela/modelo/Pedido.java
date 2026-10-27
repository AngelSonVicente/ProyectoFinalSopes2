package gt.cunoc.redxela.modelo;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pedido de un cliente.
 *
 * <p>Los datos de identidad (id, cliente, lineas, requerimientos) son inmutables. Los datos de avance
 * (estado, etapa, progreso, tiempos) los escribe el despachador o el hilo trabajador y los lee la interfaz
 * desde otro hilo, por eso son {@code volatile}: garantizan visibilidad entre hilos sin bloquear.</p>
 */
public class Pedido {

    private static final AtomicLong SECUENCIA = new AtomicLong(1);

    private final long id;
    private final Cliente cliente;
    private final NivelServicio nivel;
    private final List<LineaPedido> lineas;
    private final int totalArticulos;
    /** Union de los recursos de todas sus lineas (por recurso se toma la mayor cantidad pedida). */
    private final Map<TipoRecurso, Integer> requerimientos;
    private final boolean manual;
    /** Instante simulado (ms) en que llego al sistema. */
    private final long llegada;

    private volatile EstadoPedido estado = EstadoPedido.EN_COLA;
    private volatile Etapa etapa;
    private volatile double progreso;
    private volatile long restanteMs;
    private volatile long inicio = -1;
    private volatile long fin = -1;
    private volatile String motivoEspera = "Recien llegado";
    private volatile String politicaAplicada;

    public Pedido(Cliente cliente, NivelServicio nivel, List<LineaPedido> lineas, boolean manual, long llegada) {
        if (cliente == null || nivel == null || lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("Un pedido necesita cliente, nivel de servicio y al menos un producto");
        }
        this.id = SECUENCIA.getAndIncrement();
        this.cliente = cliente;
        this.nivel = nivel;
        this.lineas = List.copyOf(lineas);
        this.totalArticulos = lineas.stream().mapToInt(LineaPedido::cantidad).sum();
        this.manual = manual;
        this.llegada = llegada;

        Map<TipoRecurso, Integer> req = new EnumMap<>(TipoRecurso.class);
        for (LineaPedido linea : lineas) {
            linea.tipo().getRecursos().forEach((recurso, cant) -> req.merge(recurso, cant, Math::max));
        }
        this.requerimientos = Collections.unmodifiableMap(req);
    }

    /** Nombre con el que el pedido aparece como duenio de un recurso. */
    public String etiqueta() {
        return "Pedido #" + id;
    }

    /** Minutos simulados de espera hasta {@code ahora} (o hasta que inicio, si ya inicio). */
    public double minutosEspera(long ahora) {
        long hasta = inicio >= 0 ? inicio : ahora;
        return (hasta - llegada) / 60_000.0;
    }

    /** Instante simulado limite (ms) para iniciar sin violar el SLA de su nivel. */
    public long limiteSla() {
        return llegada + nivel.getMaxEsperaMinutos() * 60_000L;
    }

    public long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public NivelServicio getNivel() {
        return nivel;
    }

    public List<LineaPedido> getLineas() {
        return lineas;
    }

    public int getTotalArticulos() {
        return totalArticulos;
    }

    public Map<TipoRecurso, Integer> getRequerimientos() {
        return requerimientos;
    }

    public boolean isManual() {
        return manual;
    }

    public long getLlegada() {
        return llegada;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public Etapa getEtapa() {
        return etapa;
    }

    public void setEtapa(Etapa etapa) {
        this.etapa = etapa;
    }

    public double getProgreso() {
        return progreso;
    }

    public void setProgreso(double progreso) {
        this.progreso = progreso;
    }

    public long getRestanteMs() {
        return restanteMs;
    }

    public void setRestanteMs(long restanteMs) {
        this.restanteMs = restanteMs;
    }

    public long getInicio() {
        return inicio;
    }

    public void setInicio(long inicio) {
        this.inicio = inicio;
    }

    public long getFin() {
        return fin;
    }

    public void setFin(long fin) {
        this.fin = fin;
    }

    public String getMotivoEspera() {
        return motivoEspera;
    }

    public void setMotivoEspera(String motivoEspera) {
        this.motivoEspera = motivoEspera;
    }

    public String getPoliticaAplicada() {
        return politicaAplicada;
    }

    public void setPoliticaAplicada(String politicaAplicada) {
        this.politicaAplicada = politicaAplicada;
    }

    @Override
    public String toString() {
        return etiqueta() + " [" + nivel.getNombre() + ", " + totalArticulos + " art., " + estado + "]";
    }
}
