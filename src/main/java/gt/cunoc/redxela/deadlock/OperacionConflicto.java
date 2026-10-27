package gt.cunoc.redxela.deadlock;

import gt.cunoc.redxela.modelo.TipoRecurso;
import gt.cunoc.redxela.recursos.GestorRecursos;
import gt.cunoc.redxela.simulacion.RegistroEventos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Operacion del centro que toma recursos UNO POR UNO con espera bloqueante (a diferencia de los pedidos, que
 * reservan todo de golpe). Es el tipo de operacion que puede caer en deadlock.
 *
 * <p>Sin prevencion pide los recursos en el orden natural de su trabajo; con prevencion los pide en el orden
 * global de {@link TipoRecurso}. Si en un deadlock la eligen para ceder, se la interrumpe: suelta todo lo que
 * tiene, pasa a la lista de espera hasta que la ganadora termine y despues reintenta pidiendo en el orden global
 * (para no volver a caer en el mismo ciclo).</p>
 */
public class OperacionConflicto implements Runnable {

    public enum Estado {
        CREADA, EJECUTANDO, ESPERANDO_RECURSO, BLOQUEADA, EN_LISTA_ESPERA, COMPLETADA, CANCELADA
    }

    /** Un paso de la operacion: que recurso pide, cuantas unidades y para que. */
    public record Paso(TipoRecurso recurso, int cantidad, String accion) {
    }

    private final String nombre;
    private final String descripcion;
    private final List<Paso> pasos;
    private final boolean conPrevencion;
    private final GestorRecursos gestor;
    private final RegistroEventos eventos;
    private final long msPorPaso;
    private final CountDownLatch terminada = new CountDownLatch(1);

    private volatile Estado estado = Estado.CREADA;
    private volatile String detalle = "Por iniciar";
    private volatile int intentos;
    /** Operacion a la que se le cedieron los recursos; distinta de null solo mientras se cede. */
    private volatile OperacionConflicto cederA;
    private volatile Thread hilo;

    public OperacionConflicto(String nombre, String descripcion, List<Paso> pasos, boolean conPrevencion,
                              GestorRecursos gestor, RegistroEventos eventos, long msPorPaso) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.pasos = List.copyOf(pasos);
        this.conPrevencion = conPrevencion;
        this.gestor = gestor;
        this.eventos = eventos;
        this.msPorPaso = msPorPaso;
    }

    public void iniciar() {
        hilo = new Thread(this, nombre);
        hilo.setDaemon(true);
        hilo.start();
    }

    @Override
    public void run() {
        boolean ordenGlobal = conPrevencion;
        try {
            while (true) {
                try {
                    ejecutar(ordenGlobal);
                    estado = Estado.COMPLETADA;
                    detalle = "Terminada";
                    eventos.registrar(nombre + " completada");
                    return;
                } catch (InterruptedException e) {
                    OperacionConflicto ganadora = cederA;
                    if (ganadora == null) {
                        throw e; // interrupcion por apagado, no por resolucion de conflicto
                    }
                    // Fue elegida para ceder: suelta todo y vuelve a la lista de espera.
                    gestor.liberarTodo(nombre);
                    cederA = null;
                    estado = Estado.EN_LISTA_ESPERA;
                    detalle = "Cedio sus recursos; espera a que termine " + ganadora.getNombre();
                    eventos.registrar(nombre + " libera sus recursos y regresa a la lista de espera");
                    ganadora.esperarFin();
                    ordenGlobal = true;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            estado = Estado.CANCELADA;
            detalle = "Cancelada";
        } finally {
            gestor.liberarTodo(nombre);
            terminada.countDown();
        }
    }

    /** Ejecuta todos los pasos; cada uno pide su recurso (bloqueando) y trabaja un rato reteniendolo. */
    private void ejecutar(boolean ordenGlobal) throws InterruptedException {
        intentos++;
        List<Paso> orden = new ArrayList<>(pasos);
        if (ordenGlobal) {
            orden.sort(Comparator.comparing(Paso::recurso));
        }
        for (Paso paso : orden) {
            estado = Estado.ESPERANDO_RECURSO;
            detalle = "Pide " + paso.cantidad() + " x " + paso.recurso().getNombre() + " para " + paso.accion();
            gestor.pool(paso.recurso()).adquirirBloqueante(nombre, paso.cantidad());
            estado = Estado.EJECUTANDO;
            detalle = paso.accion();
            eventos.registrar(nombre + " obtiene " + paso.cantidad() + " x " + paso.recurso().getNombre());
            Thread.sleep(msPorPaso);
        }
        gestor.liberarTodo(nombre);
    }

    /** La interrumpe para que suelte sus recursos a favor de {@code ganadora}. */
    void ceder(OperacionConflicto ganadora) {
        cederA = ganadora;
        Thread h = hilo;
        if (h != null) {
            h.interrupt();
        }
    }

    void marcarBloqueada(String motivo) {
        if (estado == Estado.ESPERANDO_RECURSO) {
            estado = Estado.BLOQUEADA;
            detalle = motivo;
        }
    }

    void esperarFin() throws InterruptedException {
        terminada.await();
    }

    void detener() {
        Thread h = hilo;
        if (h != null) {
            h.interrupt();
        }
    }

    public boolean terminada() {
        return terminada.getCount() == 0;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public List<Paso> getPasos() {
        return pasos;
    }

    public boolean isConPrevencion() {
        return conPrevencion;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getDetalle() {
        return detalle;
    }

    public int getIntentos() {
        return intentos;
    }
}
