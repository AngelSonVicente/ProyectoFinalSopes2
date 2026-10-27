# REDXela — Simulador de centro de distribución

Proyecto final de Sistemas Operativos 2, segundo semestre 2026, CUNOC.

| | |
|---|---|
| **Estudiante** | Angel Abelino Son Vicente |
| **Carnet** | 202030885 |

## Requisitos

- JDK 21 o superior (`java -version`)
- Navegador web moderno
- Puerto 8080 libre

No hace falta instalar Maven: el proyecto incluye el wrapper `./mvnw`.

## Levantar el proyecto

Desde la carpeta raíz del proyecto:

```bash
./mvnw spring-boot:run
```

Cuando en la consola aparezca `Tomcat started on port 8080`, abre **http://localhost:8080** en el navegador.

### Con el jar compilado

```bash
./mvnw package -DskipTests
java -jar target/redxela-1.0.0.jar
```

### Usar otro puerto

```bash
java -jar target/redxela-1.0.0.jar --server.port=9090
```

### Detener

Presiona `Ctrl + C` en la consola. Toda la simulación vive en memoria, así que al volver a iniciar el servidor empieza de cero.

> Al iniciar, el generador de pedidos y los camiones automáticos están detenidos. Se encienden con los botones de la barra superior de la interfaz.
