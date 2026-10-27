package gt.cunoc.redxela.api;

import gt.cunoc.redxela.deadlock.OperacionConflicto;
import gt.cunoc.redxela.excepciones.DatosInvalidosException;
import gt.cunoc.redxela.modelo.Cliente;
import gt.cunoc.redxela.modelo.NivelServicio;
import gt.cunoc.redxela.modelo.Pedido;
import gt.cunoc.redxela.modelo.Producto;
import gt.cunoc.redxela.modelo.TipoMercancia;
import gt.cunoc.redxela.planificacion.PoliticaPlanificacion;
import gt.cunoc.redxela.simulacion.Simulacion;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * API REST que usa la interfaz web. No tiene logica de simulacion: solo traduce peticiones HTTP a llamadas sobre
 * {@link Simulacion}. Los hilos de Tomcat que atienden estas peticiones son productores mas del sistema
 * (agregan pedidos, camiones, cambian la politica), por eso todo lo que tocan es seguro para hilos.
 */
@RestController
@RequestMapping("/api")
public class ApiControlador {

    private final Simulacion sim;
    private final ConstructorEstado constructor;

    public ApiControlador(Simulacion sim, ConstructorEstado constructor) {
        this.sim = sim;
        this.constructor = constructor;
    }

    // ---- Consultas ----

    @GetMapping("/estado")
    public Vistas.Estado estado() {
        return constructor.construir();
    }

    public record TipoMercanciaVista(String id, String nombre, String descripcion, Map<String, Integer> recursos) {
    }

    public record NivelVista(int nivel, String nombre, int maxEsperaMinutos) {
    }

    public record Catalogo(List<Producto> productos, List<TipoMercanciaVista> tiposMercancia,
                           List<NivelVista> niveles, List<Vistas.Politica> politicas) {
    }

    @GetMapping("/catalogo")
    public Catalogo catalogo() {
        List<TipoMercanciaVista> tipos = Arrays.stream(TipoMercancia.values())
                .map(t -> new TipoMercanciaVista(t.name(), t.getNombre(), t.getDescripcion(),
                        t.getRecursos().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                                e -> e.getKey().getNombre(), Map.Entry::getValue,
                                (a, b) -> a, java.util.LinkedHashMap::new))))
                .toList();
        List<NivelVista> niveles = Arrays.stream(NivelServicio.values())
                .map(n -> new NivelVista(n.getNivel(), n.getNombre(), n.getMaxEsperaMinutos())).toList();
        List<Vistas.Politica> politicas = Arrays.stream(PoliticaPlanificacion.values())
                .map(p -> new Vistas.Politica(p.name(), p.getNombre(), p.getDescripcion())).toList();
        return new Catalogo(sim.getCatalogo().todos(), tipos, niveles, politicas);
    }

    @GetMapping("/clientes")
    public List<Cliente> clientes() {
        return sim.getClientes().todos();
    }

    // ---- Clientes y pedidos ----

    public record NuevoCliente(String nombre, String correo, Integer nivel) {
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    public Cliente crearCliente(@RequestBody NuevoCliente datos) {
        if (datos.nivel() == null) {
            throw new DatosInvalidosException("Debe elegir un nivel de servicio");
        }
        Cliente c = sim.getClientes().crear(datos.nombre(), datos.correo(), NivelServicio.deNumero(datos.nivel()));
        sim.getEventos().registrar("Nuevo cliente: " + c.nombre() + " (" + c.nivelContratado().getNombre() + ")");
        return c;
    }

    public record NuevoPedido(Long clienteId, Integer nivel, Map<String, Integer> productos) {
    }

    public record PedidoCreado(long id, String mensaje) {
    }

    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoCreado crearPedido(@RequestBody NuevoPedido datos) {
        if (datos.clienteId() == null) {
            throw new DatosInvalidosException("Debe elegir un cliente");
        }
        Pedido p = sim.crearPedidoManual(datos.clienteId(), datos.nivel(), datos.productos());
        return new PedidoCreado(p.getId(), p.etiqueta() + " ingresado (" + p.getNivel().getNombre() + ")");
    }

    // ---- Recepcion de mercaderia ----

    public record Llegada(String codigo, Integer cantidad) {
    }

    @PostMapping("/recepcion")
    public Map<String, String> registrarLlegada(@RequestBody Llegada datos) {
        if (datos.codigo() == null || datos.cantidad() == null) {
            throw new DatosInvalidosException("Debe indicar producto y cantidad");
        }
        sim.registrarLlegada(datos.codigo(), datos.cantidad());
        return Map.of("mensaje", "Camion registrado en el patio");
    }

    /** Llegan {@code camiones} camiones de golpe, para ver como se llena el buzon hacia Inventario. */
    @PostMapping("/recepcion/rafaga")
    public Map<String, String> rafaga(@RequestParam(defaultValue = "12") int camiones) {
        if (camiones < 1 || camiones > 100) {
            throw new DatosInvalidosException("La rafaga debe ser de 1 a 100 camiones");
        }
        List<Producto> productos = sim.getCatalogo().todos();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < camiones; i++) {
            Producto p = productos.get(rnd.nextInt(productos.size()));
            sim.registrarLlegada(p.codigo(), 5 + rnd.nextInt(26));
        }
        return Map.of("mensaje", camiones + " camiones llegaron al patio");
    }

    // ---- Control de la simulacion ----

    @PutMapping("/politica/{id}")
    public Vistas.Politica cambiarPolitica(@PathVariable String id) {
        PoliticaPlanificacion p;
        try {
            p = PoliticaPlanificacion.valueOf(id);
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException("Politica desconocida: " + id);
        }
        sim.cambiarPolitica(p);
        return new Vistas.Politica(p.name(), p.getNombre(), p.getDescripcion());
    }

    public record Generador(Boolean activo, Long intervaloMs) {
    }

    @PutMapping("/generador/pedidos")
    public Generador generadorPedidos(@RequestBody Generador datos) {
        if (datos.intervaloMs() != null) {
            sim.getGenerador().setIntervaloMedioMs(datos.intervaloMs());
        }
        if (Boolean.TRUE.equals(datos.activo())) {
            sim.getGenerador().iniciar();
        } else if (Boolean.FALSE.equals(datos.activo())) {
            sim.getGenerador().detener();
        }
        return new Generador(sim.getGenerador().isActivo(), sim.getGenerador().getIntervaloMedioMs());
    }

    @PutMapping("/generador/camiones")
    public Generador generadorCamiones(@RequestBody Generador datos) {
        if (datos.intervaloMs() != null) {
            sim.getGeneradorCamiones().setIntervaloMedioMs(datos.intervaloMs());
        }
        if (Boolean.TRUE.equals(datos.activo())) {
            sim.getGeneradorCamiones().iniciar();
        } else if (Boolean.FALSE.equals(datos.activo())) {
            sim.getGeneradorCamiones().detener();
        }
        return new Generador(sim.getGeneradorCamiones().isActivo(), sim.getGeneradorCamiones().getIntervaloMedioMs());
    }

    // ---- Conflicto de recursos ----

    @PostMapping("/conflicto/provocar")
    public Map<String, Object> provocar(@RequestParam(defaultValue = "false") boolean prevencion) {
        List<String> ops = sim.getConflictos().provocar(prevencion).stream().map(OperacionConflicto::getNombre).toList();
        return Map.of("operaciones", ops);
    }

    public record Resolucion(String ganadora) {
    }

    @PostMapping("/conflicto/{id}/resolver")
    public Map<String, String> resolver(@PathVariable long id, @RequestBody Resolucion datos) {
        if (datos.ganadora() == null || datos.ganadora().isBlank()) {
            throw new DatosInvalidosException("Debe elegir la operacion que continua");
        }
        sim.getConflictos().resolver(id, datos.ganadora());
        return Map.of("mensaje", "Continua " + datos.ganadora());
    }

    @PostMapping("/conflicto/limpiar")
    public Map<String, String> limpiarOperaciones() {
        sim.getConflictos().limpiarTerminadas();
        return Map.of("mensaje", "Operaciones terminadas eliminadas");
    }
}
