package gt.cunoc.redxela.modelo;

import gt.cunoc.redxela.excepciones.ProductoNoEncontradoException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogo fijo de productos. Se carga una sola vez y despues solo se lee,
 * por lo que se puede consultar desde varios hilos sin sincronizacion.
 */
public class Catalogo {

    private final Map<String, Producto> productos = new LinkedHashMap<>();

    public Catalogo() {
        agregar("P-001", "Caja de utiles escolares", TipoMercancia.GENERAL, 1);
        agregar("P-002", "Paquete de ropa", TipoMercancia.GENERAL, 1);
        agregar("P-003", "Juego de libros", TipoMercancia.GENERAL, 2);
        agregar("P-004", "Vajilla de porcelana", TipoMercancia.FRAGIL, 2);
        agregar("P-005", "Espejo decorativo", TipoMercancia.FRAGIL, 3);
        agregar("P-006", "Cristaleria fina", TipoMercancia.FRAGIL, 2);
        agregar("P-007", "Saco de cemento 50 kg", TipoMercancia.PESADO, 2);
        agregar("P-008", "Motor electrico industrial", TipoMercancia.PESADO, 4);
        agregar("P-009", "Laptop", TipoMercancia.ELECTRONICO, 1);
        agregar("P-010", "Telefono celular", TipoMercancia.ELECTRONICO, 1);
        agregar("P-011", "Televisor 55\"", TipoMercancia.ELECTRONICO, 3);
        agregar("P-012", "Sofa de tres plazas", TipoMercancia.VOLUMINOSO, 8);
        agregar("P-013", "Refrigeradora", TipoMercancia.VOLUMINOSO, 6);
        agregar("P-014", "Caja de medicamentos", TipoMercancia.PERECEDERO, 1);
        agregar("P-015", "Lacteos refrigerados", TipoMercancia.PERECEDERO, 1);
    }

    private void agregar(String codigo, String nombre, TipoMercancia tipo, int volumen) {
        productos.put(codigo, new Producto(codigo, nombre, tipo, volumen));
    }

    public List<Producto> todos() {
        return List.copyOf(productos.values());
    }

    public Producto buscar(String codigo) {
        Producto p = productos.get(codigo);
        if (p == null) {
            throw new ProductoNoEncontradoException(codigo);
        }
        return p;
    }
}
