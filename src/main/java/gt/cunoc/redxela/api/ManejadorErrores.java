package gt.cunoc.redxela.api;

import gt.cunoc.redxela.excepciones.ClienteNoEncontradoException;
import gt.cunoc.redxela.excepciones.ProductoNoEncontradoException;
import gt.cunoc.redxela.excepciones.RedXelaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * Convierte las excepciones en respuestas JSON {@code {"error": "..."}} con el codigo HTTP adecuado,
 * para que la interfaz muestre un mensaje claro en lugar de una pagina de error.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler({ClienteNoEncontradoException.class, ProductoNoEncontradoException.class})
    public ResponseEntity<Map<String, String>> noEncontrado(RedXelaException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({RedXelaException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> datosInvalidos(RuntimeException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> formatoInvalido(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Los datos enviados no tienen el formato correcto");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> recursoInexistente(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "No existe: " + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> inesperado(Exception e) {
        log.error("Error inesperado en la API", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno: " + e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje == null ? "Error" : mensaje));
    }
}
