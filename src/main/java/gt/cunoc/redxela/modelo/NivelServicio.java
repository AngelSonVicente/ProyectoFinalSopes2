package gt.cunoc.redxela.modelo;

/**
 * Nivel de servicio contratado por el cliente. Determina CUANDO debe atenderse un pedido.
 * El numero de nivel es la prioridad base: 1 es la mas alta.
 */
public enum NivelServicio {

    EXPRES(1, "Expres", 1),
    PRIORITARIO(2, "Prioritario", 10),
    ESTANDAR(3, "Estandar", 30),
    PROGRAMADO(4, "Programado", 60),
    ECONOMICO(5, "Economico", 120);

    private final int nivel;
    private final String nombre;
    /** Minutos simulados maximos que el pedido puede esperar antes de iniciar (SLA). Expres = casi instantaneo. */
    private final int maxEsperaMinutos;

    NivelServicio(int nivel, String nombre, int maxEsperaMinutos) {
        this.nivel = nivel;
        this.nombre = nombre;
        this.maxEsperaMinutos = maxEsperaMinutos;
    }

    public int getNivel() {
        return nivel;
    }

    public String getNombre() {
        return nombre;
    }

    public int getMaxEsperaMinutos() {
        return maxEsperaMinutos;
    }

    /** Busca el nivel por su numero (1-5). */
    public static NivelServicio deNumero(int nivel) {
        for (NivelServicio n : values()) {
            if (n.nivel == nivel) {
                return n;
            }
        }
        throw new IllegalArgumentException("Nivel de servicio invalido: " + nivel);
    }
}
