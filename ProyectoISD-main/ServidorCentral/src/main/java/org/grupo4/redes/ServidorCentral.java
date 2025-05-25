package org.grupo4.redes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.grupo4.entidades.AdministradorInstalaciones;
import org.grupo4.entidades.Solicitud;
import org.grupo4.repositorio.Configuracion;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import java.util.*;

public class ServidorCentral {
    private String ip;
    private String port;
    private String inproc;
    private int maxSalones;
    private int maxLabs;

    private final List<Long> tiemposRespuesta = new ArrayList<>();
    private int solicitudesAtendidas = 0;
    private int solicitudesNoAtendidas = 0;


    public ServidorCentral(String rutaConfig) {
        List<String> configuraciones = Configuracion.cargarConfiguracionServidor(rutaConfig);
        this.maxSalones = Integer.parseInt(configuraciones.get(0));
        this.maxLabs = Integer.parseInt(configuraciones.get(1));
        this.ip = configuraciones.get(2);
        this.port = configuraciones.get(3);
        this.inproc = configuraciones.size() > 4 ? configuraciones.get(4) : "backend";

        // Inicializar el administrador de instalaciones con los valores de configuración
        AdministradorInstalaciones.getInstance(maxSalones, maxLabs);
    }

    public ServidorCentral(String ip, String port, String inproc, int maxSalones, int maxLabs) {
        this.ip = ip;
        this.port = port;
        this.inproc = inproc;
        this.maxSalones = maxSalones;
        this.maxLabs = maxLabs;
    }

    /**
     * Método principal del broker que gestiona el balanceo de carga entre los trabajadores
     */
    public void loadBalancingBroker() {
        try (ZContext context = new ZContext()) {
            // Inicializar sockets
            Socket frontend = inicializarSocketFrontend(context);
            Socket backend = inicializarSocketBackend(context);

            // Iniciar trabajadores
            iniciarTrabajadores(context);

            // Cola de trabajadores disponibles
            Queue<String> workerQueue = new LinkedList<>();

            // Registrar shutdown hook para manejar Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nProceso Servidor Central Finalizado");
                imprimirMetricas();
            }));

            // Bucle principal del broker
            gestionarMensajes(context, frontend, backend, workerQueue);
        }
    }

    /**
     * Inicializa el socket frontend para comunicación con clientes
     */
    private Socket inicializarSocketFrontend(ZContext context) {
        Socket frontend = context.createSocket(SocketType.ROUTER);
        String endpoint = "tcp://" + ip + ":" + port;

        frontend.bind(endpoint);
        System.out.println("[BROKER] Iniciado en " + endpoint);
        return frontend;
    }

    /**
     * Inicializa el socket backend para comunicación con trabajadores
     */
    private Socket inicializarSocketBackend(ZContext context) {
        Socket backend = context.createSocket(SocketType.ROUTER);
        String inprocEndpoint = "inproc://" + inproc;
        backend.bind(inprocEndpoint);
        return backend;
    }

    /**
     * Inicializa y lanza los hilos de los trabajadores
     */
    private void iniciarTrabajadores(ZContext context) {
        System.out.println("[BROKER] Lanzando trabajadores...");
        for (int i = 1; i <= 10; i++) {
            String workerId = String.valueOf(i);
            new Thread(new TrabajadorPeticion(context, workerId)).start();
            System.out.println("[BROKER] Trabajador " + workerId + " iniciado");
        }
    }

    /**
     * Gestiona los mensajes entre clientes y trabajadores
     */
    private void gestionarMensajes(ZContext context, Socket frontend, Socket backend, Queue<String> workerQueue) {
        while (!Thread.currentThread().isInterrupted()) {
            // Preparar poller
            Poller poller = prepararPoller(context, frontend, backend, workerQueue);

            int events = poller.poll(1000);
            if (events == 0) continue;

            // Gestionar mensajes de trabajadores
            if (poller.pollin(0)) {
                manejarMensajesTrabajadores(backend, frontend, workerQueue);
            }

            // Gestionar mensajes de clientes
            if (poller.pollin(1)) {
                manejarMensajesClientes(frontend, backend, workerQueue);
            }
        }
    }

    /**
     * Prepara el poller para detectar mensajes entrantes
     */
    private Poller prepararPoller(ZContext context, Socket frontend, Socket backend, Queue<String> workerQueue) {
        Poller poller = context.createPoller(2);
        poller.register(backend, Poller.POLLIN);

        if (!workerQueue.isEmpty()) {
            poller.register(frontend, Poller.POLLIN);
            System.out.println("[BROKER] Trabajadores disponibles: " + workerQueue.size());
            System.out.println("[BROKER] Estado recursos: " + AdministradorInstalaciones.getInstance().getEstadisticas());
        }

        return poller;
    }

    /**
     * Maneja los mensajes provenientes de los trabajadores
     */
    private void manejarMensajesTrabajadores(Socket backend, Socket frontend, Queue<String> workerQueue) {
        String workerAddr = backend.recvStr();
        backend.recv(); // Frame vacío
        String command = backend.recvStr();

        if ("READY".equals(command)) {
            workerQueue.add(workerAddr);
            System.out.println("[BROKER] Trabajador " + workerAddr + " marcado como listo");
        } else {
            // Es una respuesta para reenviar al cliente
            String clientAddr = command;
            backend.recv(); // Frame vacío
            String response = backend.recvStr();

            System.out.println("[BROKER] Reenviando respuesta a cliente " + clientAddr
                    + " desde trabajador " + workerAddr);

            // Reenviar respuesta al cliente
            frontend.sendMore(clientAddr);
            frontend.sendMore("");
            frontend.send(response);

            // Devolver trabajador a la cola
            workerQueue.add(workerAddr);
        }
    }

    /**
     * Maneja los mensajes provenientes de los clientes
     */
    private void manejarMensajesClientes(Socket frontend, Socket backend, Queue<String> workerQueue) {
        String clientAddr = frontend.recvStr();
        frontend.recv(); // Frame vacío
        String request = frontend.recvStr();

        procesarSolicitudCliente(clientAddr, request, backend, frontend, workerQueue);
    }

    /**
     * Procesa las solicitudes de los clientes y las distribuye a los trabajadores o las gestiona directamente
     */
    private void procesarSolicitudCliente(String clientAddr, String requestJson,
                                          Socket backend, Socket frontend,
                                          Queue<String> workerQueue) {
        try {
            // Validar que el JSON no sea nulo o vacío
            if (requestJson == null || requestJson.trim().isEmpty()) {
                System.err.println("[BROKER] Error: Mensaje JSON vacío o nulo");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();

            // 1. Primer intento: Deserializar como ConfirmacionAsignacion
            try {
                ConfirmacionAsignacion confirmacion = mapper.readValue(requestJson, ConfirmacionAsignacion.class);
                procesarConfirmacion(clientAddr, confirmacion, frontend);
                return;
            } catch (JsonProcessingException e) {
                System.out.println("[DEBUG] No es mensaje de confirmación: " + e.getMessage());
            }

            // 2. Segundo intento: Deserializar como Solicitud
            try {
                long inicio = System.nanoTime();
                Solicitud solicitud = mapper.readValue(requestJson, Solicitud.class);
                enviarSolicitudATrabajador(clientAddr, requestJson, backend, workerQueue);
                long fin = System.nanoTime();
                registrarTiempoRespuesta(inicio, fin, true);
                return;

            } catch (JsonProcessingException e) {
                System.out.println("[DEBUG] No es solicitud de recursos: " + e.getMessage());
            }
            registrarTiempoRespuesta(0, 0, false);

        } catch (Exception e) {
            System.err.println("[BROKER] Error crítico: " + e.getMessage());
        }
    }

    /**
     * Procesa los mensajes de confirmación (aceptación o rechazo de asignaciones)
     */
    private void procesarConfirmacion(String clientAddr, ConfirmacionAsignacion confirmacion, Socket frontend) {
        String respuesta = null;
        String tipoConfirmacion = confirmacion.getEncabezado().split(":")[0];

        switch(tipoConfirmacion) {
            case "CONFIRMAR_ASIGNACION":
                System.out.println("[BROKER] Confirmación recibida de " + clientAddr);
                respuesta = "CONFIRMADO ACEPTACION";
                break;

            case "RECHAZAR_ASIGNACION":
                System.out.println("[BROKER] Rechazo recibido de " + clientAddr);
                boolean exito = AdministradorInstalaciones.getInstance().devolverRecursos(confirmacion.getResEnvio());
                respuesta = "CONFIRMADO RECHAZO";
                break;
        }

        // Enviar respuesta al cliente
        frontend.sendMore(clientAddr);
        frontend.sendMore("");
        frontend.send(respuesta);
    }

    /**
     * Envía una solicitud a un trabajador disponible
     */
    private void enviarSolicitudATrabajador(String clientAddr, String requestJson, Socket backend, Queue<String> workerQueue) {
        String workerAddr = workerQueue.poll();
        System.out.println("[BROKER] Solicitud genérica de " + clientAddr + " asignada a " + workerAddr);

        backend.sendMore(workerAddr);
        backend.sendMore("");
        backend.sendMore(clientAddr);
        backend.sendMore("");
        backend.send(requestJson);
    }

    public void registrarTiempoRespuesta(long inicio, long fin, boolean atendida) {
        long duracion = fin - inicio;
        tiemposRespuesta.add(duracion);

        if (atendida) {
            solicitudesAtendidas++;
        } else {
            solicitudesNoAtendidas++;
        }
    }

    public void imprimirMetricas() {
        if (tiemposRespuesta.isEmpty()) {
            System.out.println("\n[SERVIDOR] No se registraron tiempos de respuesta.");
            return;
        }

        long min = Collections.min(tiemposRespuesta);
        long max = Collections.max(tiemposRespuesta);
        double promedio = tiemposRespuesta.stream().mapToLong(Long::longValue).average().orElse(0.0);

        System.out.println("\n--- MÉTRICAS DE DESEMPEÑO DEL SERVIDOR CENTRAL ---");
        System.out.println("Solicitudes atendidas: " + solicitudesAtendidas);
        System.out.println("Solicitudes no atendidas: " + solicitudesNoAtendidas);
        System.out.printf("Tiempo mínimo de atención: %.2f ms%n", min / 1_000_000.0);
        System.out.printf("Tiempo máximo de atención: %.2f ms%n", max / 1_000_000.0);
        System.out.printf("Tiempo promedio de atención: %.2f ms%n", promedio / 1_000_000.0);
    }

}