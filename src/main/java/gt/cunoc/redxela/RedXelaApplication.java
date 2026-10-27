package gt.cunoc.redxela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del simulador REDXela.
 * Spring Boot solo levanta el servidor web; la simulacion vive en {@link gt.cunoc.redxela.simulacion.Simulacion}.
 */
@SpringBootApplication
public class RedXelaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedXelaApplication.class, args);
    }
}
