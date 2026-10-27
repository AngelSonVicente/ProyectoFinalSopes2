package gt.cunoc.redxela.modelo;

import java.util.EnumSet;
import java.util.Set;

/**
 * Etapas por las que pasa un pedido mientras se procesa.
 *
 * <p>Cada etapa usa ciertos recursos y, al terminarla, esos recursos se liberan de inmediato para que
 * otros pedidos los aprovechen. Liberar antes de tiempo nunca provoca deadlock; solo pedir si puede.</p>
 */
public enum Etapa {

    PICKING("Recoleccion en bodega", 0.35, EnumSet.of(TipoRecurso.MONTACARGAS, TipoRecurso.ESCANER)),
    EMPAQUE("Empaque", 0.25, EnumSet.of(TipoRecurso.EMPAQUE)),
    CONTROL_CALIDAD("Control de calidad", 0.20, EnumSet.of(TipoRecurso.CONTROL_CALIDAD)),
    DESPACHO("Carga y despacho", 0.20, EnumSet.of(TipoRecurso.AREA_CARGA));

    private final String nombre;
    /** Fraccion relativa del trabajo total que consume esta etapa. */
    private final double peso;
    /** Recursos que se usan (y se liberan al terminar) en esta etapa. */
    private final Set<TipoRecurso> recursos;

    Etapa(String nombre, double peso, Set<TipoRecurso> recursos) {
        this.nombre = nombre;
        this.peso = peso;
        this.recursos = recursos;
    }

    public String getNombre() {
        return nombre;
    }

    public double getPeso() {
        return peso;
    }

    public Set<TipoRecurso> getRecursos() {
        return recursos;
    }

    /** Picking siempre aplica; las demas solo si el pedido usa alguno de sus recursos. */
    public boolean aplicaA(Pedido pedido) {
        return this == PICKING || recursos.stream().anyMatch(pedido.getRequerimientos()::containsKey);
    }
}
