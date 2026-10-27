package gt.cunoc.redxela.deadlock;

import java.util.List;

/**
 * Deadlock detectado: que operaciones estan bloqueadas entre si, que recursos tienen, cuales esperan y por que.
 * El estado (activo/resuelto) cambia desde la API y lo lee el detector, por eso es {@code volatile}.
 */
public class Conflicto {

    /** Una operacion atrapada en el ciclo. */
    public record Participante(String operacion, List<String> retiene, String espera, String esperaDe) {
    }

    private final long id;
    private final long detectado;
    private final List<Participante> participantes;
    private final List<String> recursos;
    private final String explicacion;

    private volatile boolean resuelto;
    private volatile String ganador;
    private volatile long resueltoEn = -1;

    public Conflicto(long id, long detectado, List<Participante> participantes, List<String> recursos,
                     String explicacion) {
        this.id = id;
        this.detectado = detectado;
        this.participantes = List.copyOf(participantes);
        this.recursos = List.copyOf(recursos);
        this.explicacion = explicacion;
    }

    void resolver(String ganador, long instante) {
        this.ganador = ganador;
        this.resueltoEn = instante;
        this.resuelto = true;
    }

    public long getId() {
        return id;
    }

    public long getDetectado() {
        return detectado;
    }

    public List<Participante> getParticipantes() {
        return participantes;
    }

    public List<String> getRecursos() {
        return recursos;
    }

    public String getExplicacion() {
        return explicacion;
    }

    public boolean isResuelto() {
        return resuelto;
    }

    public String getGanador() {
        return ganador;
    }

    public long getResueltoEn() {
        return resueltoEn;
    }
}
