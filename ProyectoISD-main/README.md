# ProyectoISD

**Autores:** Juan Luis Ardila, Simón Díaz y Melissa Ruíz

## Autores

- [@Juan Luis Ardila](https://github.com/jardila20)
- [@Simon Diaz](https://github.com/SDM30)
- [@Melissa Ruiz](https://github.com/mfruiz1025)

## Descripción

Este proyecto implementa un sistema de gestión de recursos académicos utilizando el patrón *Load Balancing Broker*. Las funcionalidades principales incluyen:

1. **Facultades** envían solicitudes de recursos (aulas y laboratorios) al **Servidor Central**.
2. **Servidor Central concurrente** procesa las solicitudes, asigna recursos disponibles y responde a las facultades.
3. **Facultades** confirman o rechazan las asignaciones propuestas por el servidor.

---

## Cómo ejecutar el programa cliente (Facultad)

### Ejecutar desde IDE
1. Clonar el repositorio y abrir el proyecto en su IDE.
2. Ejecutar la clase `MainFacultad.java`.

### Ejecutar como JAR
1. Compilar el proyecto y ubicar el archivo JAR generado (ej: `Facultad-1.0-SNAPSHOT-jar-with-dependencies.jar`).
2. Ejecutar con los siguientes argumentos:
   ```bash
   java -jar Facultad.jar <nombre_facultad> <IP_servidor> <puerto> [semestre] [archivo_programas]
   ```
   **Ejemplos:**
   - Valores por defecto:
     ```bash
     java -jar Facultad.jar
     ```
   - Parámetros personalizados:
     ```bash
     java -jar Facultad.jar "Facultad de Ciencias" 192.168.1.100 5555 2 programas_custom.txt
     ```

**Argumentos:**
- `nombre_facultad`: Nombre identificador de la facultad.
- `IP_servidor`: Dirección IP del servidor central.
- `puerto`: Puerto de conexión al servidor.
- `semestre` (opcional, default=1): Semestre académico.
- `archivo_programas` (opcional): Ruta de archivo con programas académicos (formato: `Nombre,salones,laboratorios`).

---

## Cómo ejecutar el programa servidor central

### Ejecutar desde IDE
1. Ejecutar la clase `MainServidorCentral.java`.

### Ejecutar con configuración personalizada
1. Crear un archivo `configServidor.properties` con los siguientes parámetros:
   ```properties
   max.salones=380
   max.laboratorios=60
   server.ip=0.0.0.0
   server.port=5555
   inproc.address=backend
   ```
2. Ejecutar el servidor especificando la ruta del archivo:

El nombre del jar por defecto para el servidor  `ServidorCentral-1.0-SNAPSHOT-jar-with-dependencies.jar`, Para colocar una configuración
personalizada ingresa a `src/main/resources/configServidor.properties`
 
   ```bash
   java -jar ServidorCentral.jar
   ```

### Ejecutar con valores por defecto
```bash
java -jar ServidorCentral.jar
```
**Valores por defecto:**
- Máximo de salones: 380
- Máximo de laboratorios: 60
- IP: `0.0.0.0`
- Puerto: `5555`

---

## Requisitos
- **Java 17** o superior.
- **Conexión de red** entre cliente y servidor.
- **Servidor Central** debe estar en ejecución antes de iniciar los clientes.
- Archivo `configCliente.properties` (opcional para cliente) con formato:
  ```properties
  server.ip=localhost
  server.port=5555
  ```
- Archivo `programasDefecto.txt` (para carga inicial de programas académicos en cliente).

---

## Notas adicionales
- El cliente cargará automáticamente una **solicitud de emergencia** si no se especifica un archivo de programas.
- Para depuración, revise los mensajes de consola en cliente y servidor