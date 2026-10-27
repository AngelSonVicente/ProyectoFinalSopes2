package gt.cunoc.redxela.modelo;

import gt.cunoc.redxela.excepciones.ClienteNoEncontradoException;
import gt.cunoc.redxela.excepciones.DatosInvalidosException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cuentas de clientes. Lo usan a la vez el generador automatico y la API (hilos distintos),
 * por eso se guarda en un {@link ConcurrentHashMap} y el id sale de un {@link AtomicLong}.
 */
public class RegistroClientes {

    private final Map<Long, Cliente> clientes = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong(1);

    public RegistroClientes() {
        crear("Tienda La Esquina", "laesquina@correo.com", NivelServicio.ESTANDAR);
        crear("Farmacia Xela", "farmacia@correo.com", NivelServicio.EXPRES);
        crear("Muebleria El Roble", "elroble@correo.com", NivelServicio.PROGRAMADO);
        crear("TecnoCentro", "tecnocentro@correo.com", NivelServicio.PRIORITARIO);
        crear("Ferreteria Quetzal", "ferreteria@correo.com", NivelServicio.ECONOMICO);
    }

    /** Crea una cuenta nueva con el nivel de servicio elegido por el cliente. */
    public Cliente crear(String nombre, String correo, NivelServicio nivel) {
        if (nombre == null || nombre.isBlank()) {
            throw new DatosInvalidosException("El nombre del cliente es obligatorio");
        }
        if (nivel == null) {
            throw new DatosInvalidosException("Debe elegir un nivel de servicio");
        }
        Cliente c = new Cliente(secuencia.getAndIncrement(), nombre.trim(), correo == null ? "" : correo.trim(), nivel);
        clientes.put(c.id(), c);
        return c;
    }

    public Cliente buscar(long id) {
        Cliente c = clientes.get(id);
        if (c == null) {
            throw new ClienteNoEncontradoException(id);
        }
        return c;
    }

    public List<Cliente> todos() {
        return clientes.values().stream().sorted(Comparator.comparingLong(Cliente::id)).toList();
    }
}
