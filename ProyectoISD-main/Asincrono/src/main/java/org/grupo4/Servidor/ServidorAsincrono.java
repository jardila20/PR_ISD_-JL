package org.grupo4.asincrono.servidor;

// Imports de proyectos hermanos - REUTILIZACIÓN
import org.grupo4.concurrencia.ContadorAtomico;
import org.grupo4.entidades.AdministradorInstalaciones;
import org.grupo4.entidades.ResultadoAsignacion;
import org.grupo4proyecto.entidades.Solicitud;
import org.grupo4proyecto.redes.ResultadoEnvio;
import org.grupo4proyecto.redes.ConfirmacionAsignacion;

// Import del proyecto asíncrono
import org.grupo4.asincrono.configuracion.ConfiguracionAsincrono;

// Imports propios
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servidor Asíncrono DTI - Implementación completa para casos de prueba
 * 
 * PATRÓN: Asynchronous Client/Server
 * CASOS DE PRUEBA SOPORTADOS:
 * - Caso 1: 1 Facultad, 2 programas (380 salones, 60 labs)
 * - Caso 2: Nuevo semestre con reset de recursos
 * - Caso 3: 3 facultades, 6 programas (30 salones, 10 labs)
 * - Caso 4: 3 facultades, 7 programas con alertas (30 salones, 12 labs)
 * - Caso 5: Tolerancia a fallas con réplica
 */
public class ServidorAsincrono {
    
    // Configuración del servidor
    private final ConfiguracionAsincrono configuracion;
    private final String ip;
    private final String puerto;
    private int maxSalones;
    private int maxLabs;
    private final int numHilos;
    
    // Gestión de semestres para reset de recursos
    private int semestreActual = 1;
    private final Map<Integer, String> estadosPorSemestre = new ConcurrentHashMap<>();
    
    // Pool de hilos para procesamiento asíncrono
    private final ExecutorService poolHilos;
    
    // Métricas y estado
    private final AtomicLong solicitudesProcesadas = new AtomicLong(0);
    private final AtomicLong solicitudesExitosas = new AtomicLong(0);
    private final AtomicLong solicitudesFallidas = new AtomicLong(0);
    private final List<Long> tiemposRespuesta = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> clientesConectados = new ConcurrentHashMap<>();
    private final Map<String, Long> ultimoHeartbeat = new ConcurrentHashMap<>();
    
    // Estado del servidor y tolerancia a fallas
    private volatile boolean ejecutandose = true;
    private volatile boolean modoReplica = false;
    private volatile boolean servidorPrincipalActivo = true;
    private Thread hiloHeartbeat;
    
    // Persistencia y logs
    private final String archivoLog;
    private final String archivoPersistencia;
    private final ObjectMapper json = new ObjectMapper();
    
    /**
     * Constructor principal con configuración
     */
    public ServidorAsincrono(ConfiguracionAsincrono configuracion) {
        this.configuracion = configuracion;
        this.ip = configuracion.getServidorIp();
        this.puerto = String.valueOf(configuracion.getServidorPuerto());
        this.maxSalones = configuracion.getMaxSalones();
        this.maxLabs = configuracion.getMaxLabs();
        this.numHilos = configuracion.getThreadPoolSize();
        this.poolHilos = Executors.newFixedThreadPool(numHilos);
        
        // Configurar archivos
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.archivoLog = "dti_asincrono_" + timestamp + ".log";
        this.archivoPersistencia = "solicitudes_" + timestamp + ".json";
        
        // Inicializar AdministradorInstalaciones
        AdministradorInstalaciones.getInstance(maxSalones, maxLabs);
        
        mostrarBannerInicio();
        registrarEvento("SERVIDOR_INICIADO", String.format("Recursos: %d salones, %d labs", maxSalones, maxLabs));
    }
    
    /**
     * Constructor para casos de prueba específicos
     */
    public static ServidorAsincrono paraCasoPrueba(int numeroCaso) {
        ConfiguracionAsincrono config = ConfiguracionAsincrono.paraCasoPrueba(numeroCaso);
        return new ServidorAsincrono(config);
    }
    
    /**
     * Constructor con parámetros específicos (para compatibilidad)
     */
    public ServidorAsincrono(String ip, String puerto, int maxSalones, int maxLabs, int numHilos) {
        this.configuracion = new ConfiguracionAsincrono();
        this.configuracion.actualizarPropiedad("servidor.ip", ip);
        this.configuracion.actualizarPropiedad("servidor.puerto", puerto);
        this.configuracion.actualizarPropiedad("servidor.maxSalones", String.valueOf(maxSalones));
        this.configuracion.actualizarPropiedad("servidor.maxLabs", String.valueOf(maxLabs));
        this.configuracion.actualizarPropiedad("servidor.threadPoolSize", String.valueOf(numHilos));
        
        this.ip = ip;
        this.puerto = puerto;
        this.maxSalones = maxSalones;
        this.maxLabs = maxLabs;
        this.numHilos = numHilos;
        this.poolHilos = Executors.newFixedThreadPool(numHilos);
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.archivoLog = "dti_asincrono_" + timestamp + ".log";
        this.archivoPersistencia = "solicitudes_" + timestamp + ".json";
        
        AdministradorInstalaciones.getInstance(maxSalones, maxLabs);
        
        mostrarBannerInicio();
        registrarEvento("SERVIDOR_INICIADO", String.format("Recursos: %d salones, %d labs", maxSalones, maxLabs));
    }
    
    /**
     * Constructor por defecto
     */
    public ServidorAsincrono() {
        this(new ConfiguracionAsincrono());
    }
    
    /**
     * Muestra banner de inicio
     */
    private void mostrarBannerInicio() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    SERVIDOR DTI ASÍNCRONO                   ║");
        System.out.println("║              Patrón: Asynchronous Client/Server             ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ IP: %-56s ║%n", ip);
        System.out.printf("║ Puerto: %-51s ║%n", puerto);
        System.out.printf("║ Salones iniciales: %-40d ║%n", maxSalones);
        System.out.printf("║ Laboratorios iniciales: %-36d ║%n", maxLabs);
        System.out.printf("║ Hilos de procesamiento: %-36d ║%n", numHilos);
        System.out.printf("║ Réplica habilitada: %-40s ║%n", configuracion.isReplicaHabilitada() ? "SÍ" : "NO");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
    
    /**
     * Inicia el servidor asíncrono con soporte completo para casos de prueba
     */
    public void iniciar() {
        if (!configuracion.validarConfiguracion()) {
            System.err.println("[DTI ASÍNCRONO] ❌ Configuración inválida, no se puede iniciar el servidor");
            return;
        }
        
        mostrarSocketsPattern();
        
        try (ZContext contexto = new ZContext()) {
            // Socket ROUTER para múltiples clientes asíncronos
            Socket servidor = contexto.createSocket(SocketType.ROUTER);
            String endpoint = "tcp://" + ip + ":" + puerto;
            
            servidor.bind(endpoint);
            System.out.println("\n[DTI ASÍNCRONO] 🚀 Servidor iniciado en " + endpoint);
            System.out.println("[DTI ASÍNCRONO] 📡 Esperando conexiones de facultades...");
            System.out.println("[DTI ASÍNCRONO] 📄 Log guardándose en: " + archivoLog);
            System.out.println("[DTI ASÍNCRONO] 💾 Persistencia en: " + archivoPersistencia);
            
            registrarEvento("SERVIDOR_ACTIVO", "Esperando conexiones en " + endpoint);
            
            // Configurar shutdown hook
            configurarShutdownHook();
            
            // Iniciar heartbeat si la réplica está habilitada
            if (configuracion.isReplicaHabilitada()) {
                iniciarHeartbeat();
            }
            
            // Bucle principal - escuchar solicitudes asíncronas
            while (ejecutandose && !Thread.currentThread().isInterrupted()) {
                try {
                    // Recibir mensaje de cliente (no bloqueante)
                    String[] mensaje = recibirMensajeCliente(servidor);
                    if (mensaje != null) {
                        String clienteId = mensaje[0];
                        String solicitudJson = mensaje[1];
                        
                        // Actualizar heartbeat del cliente
                        ultimoHeartbeat.put(clienteId, System.currentTimeMillis());
                        
                        // Procesar de forma asíncrona
                        procesarSolicitudAsincrona(servidor, clienteId, solicitudJson);
                    }
                    
                    // Verificar health de clientes conectados
                    verificarHealthClientes();
                    
                } catch (Exception e) {
                    if (ejecutandose) {
                        System.err.println("[DTI ASÍNCRONO] ❌ Error procesando solicitud: " + e.getMessage());
                        registrarEvento("ERROR", e.getMessage());
                    }
                }
                
                // Pequeña pausa para evitar consumo excesivo de CPU
                Thread.sleep(1);
            }
            
        } catch (Exception e) {
            System.err.println("[DTI ASÍNCRONO] ❌ Error fatal: " + e.getMessage());
            registrarEvento("ERROR_FATAL", e.getMessage());
        } finally {
            detenerHeartbeat();
        }
    }
    
    /**
     * Muestra el patrón de sockets utilizado (requerido para casos de prueba)
     */
    private void mostrarSocketsPattern() {
        System.out.println("\n🔌 PATRÓN DE SOCKETS: ASYNCHRONOUS CLIENT/SERVER");
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ FACULTADES (Clientes)    DTI (Servidor)                │");
        System.out.println("│                                                         │");
        System.out.println("│ [Client DEALER] ──────→ [Server ROUTER] ──→ [Pool]     │");
        System.out.println("│ [Client DEALER] ──────→ [Server ROUTER] ──→ [Hilos]    │");
        System.out.println("│ [Client DEALER] ──────→ [Server ROUTER] ──→ [Async]    │");
        System.out.println("│                                                         │");
        System.out.println("│ Comunicación directa sin broker intermedio             │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println("📍 Máquina DTI: " + ip + ":" + puerto);
        System.out.println("📍 Socket Servidor: ROUTER (bind)");
        System.out.println("📍 Socket Clientes: DEALER (connect)\n");
    }
    
    /**
     * Recibe mensaje de cliente con formato ZeroMQ
     */
    private String[] recibirMensajeCliente(Socket servidor) {
        try {
            String clienteId = servidor.recvStr(ZMQ.DONTWAIT);
            if (clienteId == null) {
                return null;
            }
            
            servidor.recv(); // Frame vacío
            String solicitud = servidor.recvStr();
            
            // Registrar cliente conectado
            clientesConectados.put(clienteId, System.currentTimeMillis());
            
            return new String[]{clienteId, solicitud};
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Procesa solicitud usando pool de hilos (asíncrono)
     */
    private void procesarSolicitudAsincrona(Socket servidor, String clienteId, String solicitudJson) {
        long inicioTiempo = System.nanoTime();
        
        poolHilos.submit(() -> {
            try {
                System.out.println("[DTI ASÍNCRONO] 📝 Procesando solicitud de: " + clienteId);
                
                // Determinar tipo de mensaje
                String respuesta = procesarMensaje(solicitudJson, clienteId);
                
                // Enviar respuesta asíncrona
                enviarRespuestaAsincrona(servidor, clienteId, respuesta);
                
                // Registrar métricas
                long finTiempo = System.nanoTime();
                registrarMetricas(inicioTiempo, finTiempo, true);
                
                System.out.println("[DTI ASÍNCRONO] ✅ Respuesta enviada a: " + clienteId);
                
            } catch (Exception e) {
                System.err.println("[DTI ASÍNCRONO] ❌ Error en hilo: " + e.getMessage());
                registrarMetricas(inicioTiempo, System.nanoTime(), false);
                registrarEvento("ERROR_PROCESAMIENTO", "Cliente: " + clienteId + ", Error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Procesa diferentes tipos de mensajes
     */
    private String procesarMensaje(String mensajeJson, String clienteId) throws JsonProcessingException {
        
        // Comando especial para reset de semestre (Caso 2)
        if (mensajeJson.startsWith("RESET_SEMESTRE")) {
            return procesarResetSemestre(mensajeJson);
        }
        
        // Comando para simular falla del servidor (Caso 5)
        if (mensajeJson.startsWith("SIMULAR_FALLA")) {
            return procesarSimularFalla();
        }
        
        // Comando para activar réplica (Caso 5)
        if (mensajeJson.startsWith("ACTIVAR_REPLICA")) {
            return procesarActivarReplica();
        }
        
        // Comando para ping/heartbeat
        if (mensajeJson.equals("PING_SERVER") || mensajeJson.equals("HEARTBEAT")) {
            return procesarHeartbeat(clienteId);
        }
        
        // Intentar como confirmación
        try {
            ConfirmacionAsignacion confirmacion = json.readValue(mensajeJson, ConfirmacionAsignacion.class);
            return procesarConfirmacion(confirmacion, clienteId);
        } catch (JsonProcessingException e) {
            // No es confirmación, continuar
        }
        
        // Procesar como solicitud de recursos
        Solicitud solicitud = json.readValue(mensajeJson, Solicitud.class);
        return procesarSolicitudRecursos(solicitud, clienteId);
    }
    
    /**
     * Procesa heartbeat de cliente
     */
    private String procesarHeartbeat(String clienteId) {
        ultimoHeartbeat.put(clienteId, System.currentTimeMillis());
        return "PONG";
    }
    
    /**
     * Procesa reset de semestre (Caso 2)
     */
    private String procesarResetSemestre(String comando) {
        try {
            String[] partes = comando.split(":");
            int nuevoSemestre = Integer.parseInt(partes[1]);
            
            if (nuevoSemestre != semestreActual) {
                // Guardar estado del semestre anterior
                String estadoAnterior = AdministradorInstalaciones.getInstance().getEstadisticas();
                estadosPorSemestre.put(semestreActual, estadoAnterior);
                
                // Reset recursos para nuevo semestre
                AdministradorInstalaciones.getInstance(maxSalones, maxLabs);
                semestreActual = nuevoSemestre;
                
                String mensaje = String.format("Semestre cambiado a %d. Recursos reseteados: %d salones, %d labs", 
                    nuevoSemestre, maxSalones, maxLabs);
                
                System.out.println("[DTI ASÍNCRONO] 🔄 " + mensaje);
                registrarEvento("RESET_SEMESTRE", mensaje);
                
                return "SEMESTRE_RESET_OK:" + nuevoSemestre;
            }
            
            return "SEMESTRE_SIN_CAMBIOS:" + semestreActual;
            
        } catch (Exception e) {
            registrarEvento("ERROR_RESET_SEMESTRE", e.getMessage());
            return "ERROR_RESET_SEMESTRE:" + e.getMessage();
        }
    }
    
    /**
     * Simula falla del servidor (Caso 5)
     */
    private String procesarSimularFalla() {
        System.out.println("[DTI ASÍNCRONO] 💥 SIMULANDO FALLA DEL SERVIDOR...");
        registrarEvento("FALLA_SIMULADA", "Servidor principal fallando, activando réplica");
        
        servidorPrincipalActivo = false;
        
        // Simular caída por 5 segundos
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (configuracion.isReplicaHabilitada()) {
                    modoReplica = true;
                    servidorPrincipalActivo = true;
                    System.out.println("[DTI ASÍNCRONO] 🔄 RÉPLICA ACTIVADA - Servidor recuperado");
                    registrarEvento("REPLICA_ACTIVADA", "Servidor funcionando como réplica");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        return "FALLA_SIMULADA";
    }
    
    /**
     * Activa modo réplica manualmente (Caso 5)
     */
    private String procesarActivarReplica() {
        if (configuracion.isReplicaHabilitada()) {
            modoReplica = true;
            servidorPrincipalActivo = true;
            System.out.println("[DTI ASÍNCRONO] 🔄 RÉPLICA ACTIVADA MANUALMENTE");
            registrarEvento("REPLICA_ACTIVADA_MANUAL", "Réplica activada por comando manual");
            return "REPLICA_ACTIVADA";
        } else {
            return "REPLICA_NO_HABILITADA";
        }
    }
    
    /**
     * Procesa solicitud de recursos con logging detallado
     */
    private String procesarSolicitudRecursos(Solicitud solicitud, String clienteId) throws JsonProcessingException {
        
        System.out.printf("[DTI ASÍNCRONO] 🏢 Procesando: %s (%s) - %d salones, %d labs (Semestre %d)%n",
                solicitud.getPrograma(), 
                solicitud.getFacultad(),
                solicitud.getNumSalones(),
                solicitud.getNumLaboratorios(),
                solicitud.getSemestre());
        
        // Verificar si necesitamos reset de semestre
        if (solicitud.getSemestre() != semestreActual) {
            procesarResetSemestre("RESET_SEMESTRE:" + solicitud.getSemestre());
        }
        
        // Procesar asignación
        ResultadoAsignacion resultado = AdministradorInstalaciones.getInstance()
                .asignar(solicitud.getNumSalones(), solicitud.getNumLaboratorios());
        
        // Generar respuesta
        String infoGeneral = generarInfoGeneral(resultado, solicitud);
        ResultadoEnvio respuesta = new ResultadoEnvio(
                infoGeneral,
                resultado.labsAsignados(),
                resultado.aulaMovilAsignadas(),
                resultado.salonesAsignados()
        );
        
        // Logging y métricas
        String estadoRecursos = AdministradorInstalaciones.getInstance().getEstadisticas();
        String tipoResultado = resultado.esExitoso() ? "EXITOSA" : "FALLIDA";
        
        System.out.printf("[DTI ASÍNCRONO] %s Asignación %s para %s%n", 
            resultado.esExitoso() ? "✅" : "⚠️", tipoResultado, solicitud.getPrograma());
        System.out.println("[DTI ASÍNCRONO] 📊 " + estadoRecursos);
        
        // Registrar en archivo
        String evento = String.format("SOLICITUD_%s", tipoResultado);
        String detalles = String.format("Cliente: %s, Programa: %s, Solicitado: %d/%d, Asignado: %d/%d/%d, Estado: %s",
            clienteId, solicitud.getPrograma(),
            solicitud.getNumSalones(), solicitud.getNumLaboratorios(),
            resultado.salonesAsignados(), resultado.labsAsignados(), resultado.aulaMovilAsignadas(),
            estadoRecursos);
        
        registrarEvento(evento, detalles);
        
        // Persistir solicitud
        persistirSolicitud(solicitud, resultado, clienteId);
        
        if (resultado.esExitoso()) {
            solicitudesExitosas.incrementAndGet();
        } else {
            solicitudesFallidas.incrementAndGet();
        }
        
        return json.writeValueAsString(respuesta);
    }
    
    /**
     * Procesa confirmación con logging
     */
    private String procesarConfirmacion(ConfirmacionAsignacion confirmacion, String clienteId) {
        String tipoConfirmacion = confirmacion.getEncabezado().split(":")[0];
        
        switch (tipoConfirmacion) {
            case "CONFIRMAR_ASIGNACION":
                System.out.println("[DTI ASÍNCRONO] ✅ Confirmación de aceptación de " + clienteId);
                registrarEvento("CONFIRMACION_ACEPTADA", "Cliente: " + clienteId);
                return "CONFIRMADO ACEPTACION";
                
            case "RECHAZAR_ASIGNACION":
                System.out.println("[DTI ASÍNCRONO] ↩️ Rechazo de " + clienteId + ", devolviendo recursos");
                
                boolean exito = AdministradorInstalaciones.getInstance()
                        .devolverRecursos(confirmacion.getResEnvio());
                        
                String estadoFinal = AdministradorInstalaciones.getInstance().getEstadisticas();
                System.out.println("[DTI ASÍNCRONO] 📊 " + estadoFinal);
                
                registrarEvento("CONFIRMACION_RECHAZADA", 
                    String.format("Cliente: %s, Recursos devueltos: %s, Estado: %s", 
                        clienteId, exito ? "OK" : "ERROR", estadoFinal));
                
                return "CONFIRMADO RECHAZO";
                
            default:
                return "CONFIRMACION DESCONOCIDA";
        }
    }
    
    /**
     * Envía respuesta asíncrona
     */
    private synchronized void enviarRespuestaAsincrona(Socket servidor, String clienteId, String respuesta) {
        try {
            servidor.sendMore(clienteId);
            servidor.sendMore("");
            servidor.send(respuesta);
        } catch (Exception e) {
            System.err.println("[DTI ASÍNCRONO] ❌ Error enviando respuesta a " + clienteId + ": " + e.getMessage());
            registrarEvento("ERROR_ENVIO", "Cliente: " + clienteId + ", Error: " + e.getMessage());
        }
    }
    
    /**
     * Genera información general
     */
    private String generarInfoGeneral(ResultadoAsignacion resultado, Solicitud solicitud) {
        if (resultado.esExitoso()) {
            if (resultado.aulaMovilAsignadas() == 0) {
                return String.format("Asignación exitosa de laboratorios y salones para %s", solicitud.getPrograma());
            } else {
                return String.format("Asignación exitosa para %s, algunos laboratorios se asignaron como aulas móviles", solicitud.getPrograma());
            }
        }
        return "[ALERTA] No hay suficientes aulas o laboratorios para responder a la demanda";
    }
    
    /**
     * Persiste solicitud en archivo JSON
     */
    private void persistirSolicitud(Solicitud solicitud, ResultadoAsignacion resultado, String clienteId) {
        try (FileWriter writer = new FileWriter(archivoPersistencia, true)) {
            Map<String, Object> registro = new HashMap<>();
            registro.put("timestamp", LocalDateTime.now().toString());
            registro.put("clienteId", clienteId);
            registro.put("solicitud", solicitud);
            registro.put("resultado", resultado);
            registro.put("semestre", semestreActual);
            registro.put("modoReplica", modoReplica);
            
            String linea = json.writeValueAsString(registro) + "\n";
            writer.write(linea);
        } catch (IOException e) {
            System.err.println("[DTI ASÍNCRONO] ❌ Error persistiendo solicitud: " + e.getMessage());
        }
    }
    
    /**
     * Registra evento en archivo de log
     */
    private void registrarEvento(String tipo, String detalles) {
        try (FileWriter writer = new FileWriter(archivoLog, true)) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String linea = String.format("[%s] [%s] %s: %s%n", 
                timestamp, modoReplica ? "REPLICA" : "PRINCIPAL", tipo, detalles);
            writer.write(linea);
        } catch (IOException e) {
            System.err.println("Error escribiendo log: " + e.getMessage());
        }
    }
    
    /**
     * Registra métricas
     */
    private void registrarMetricas(long inicio, long fin, boolean exitosa) {
        long duracion = fin - inicio;
        tiemposRespuesta.add(duracion);
        solicitudesProcesadas.incrementAndGet();
        
        if (!exitosa) {
            solicitudesFallidas.incrementAndGet();
        }
    }
    
    /**
     * Inicia hilo de heartbeat para tolerancia a fallas
     */
    private void iniciarHeartbeat() {
        hiloHeartbeat = new Thread(() -> {
            System.out.println("[DTI ASÍNCRONO] 💓 Heartbeat iniciado para tolerancia a fallas");
            
            while (ejecutandose && !Thread.currentThread().isInterrupted()) {
                try {
                    // Verificar estado del servidor
                    if (!servidorPrincipalActivo && !modoReplica) {
                        System.out.println("[DTI ASÍNCRONO] ⚠️ Servidor principal inactivo, activando réplica...");
                        procesarActivarReplica();
                    }
                    
                    Thread.sleep(configuracion.getHeartbeatInterval());
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[DTI ASÍNCRONO] ❌ Error en heartbeat: " + e.getMessage());
                }
            }
            
            System.out.println("[DTI ASÍNCRONO] 💓 Heartbeat detenido");
        });
        
        hiloHeartbeat.setDaemon(true);
        hiloHeartbeat.start();
    }
    
    /**
     * Detiene hilo de heartbeat
     */
    private void detenerHeartbeat() {
        if (hiloHeartbeat != null) {
            hiloHeartbeat.interrupt();
            try {
                hiloHeartbeat.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Verifica health de clientes conectados
     */
    private void verificarHealthClientes() {
        long tiempoActual = System.currentTimeMillis();
        long timeoutCliente = configuracion.getClienteTimeoutMs();
        
        Iterator<Map.Entry<String, Long>> iterator = ultimoHeartbeat.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            String clienteId = entry.getKey();
            long ultimoContact = entry.getValue();
            
            if (tiempoActual - ultimoContact > timeoutCliente) {
                System.out.printf("[DTI ASÍNCRONO] ⚠️ Cliente %s sin heartbeat, removiendo%n", clienteId);
                iterator.remove();
                clientesConectados.remove(clienteId);
                registrarEvento("CLIENTE_DESCONECTADO", "Cliente: " + clienteId + " (timeout)");
            }
        }
    }
    
    /**
     * Configura shutdown hook
     */
    private void configurarShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[DTI ASÍNCRONO] 🛑 Cerrando servidor...");
            ejecutandose = false;
            detenerHeartbeat();
            poolHilos.shutdown();
            imprimirMetricasFinales();
            registrarEvento("SERVIDOR_CERRADO", "Shutdown limpio completado");
        }));
    }
    
    /**
     * Imprime métricas finales para casos de prueba
     */
    public void imprimirMetricasFinales() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                MÉTRICAS FINALES DTI ASÍNCRONO               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Semestre procesado: %-40d ║%n", semestreActual);
        System.out.printf("║ Solicitudes totales: %-38d ║%n", solicitudesProcesadas.get());
        System.out.printf("║ Solicitudes exitosas: %-37d ║%n", solicitudesExitosas.get());
        System.out.printf("║ Solicitudes fallidas: %-37d ║%n", solicitudesFallidas.get());
        System.out.printf("║ Clientes únicos: %-43d ║%n", clientesConectados.size());
        System.out.printf("║ Modo réplica: %-46s ║%n", modoReplica ? "SÍ" : "NO");
        
        if (!tiemposRespuesta.isEmpty()) {
            long min = Collections.min(tiemposRespuesta);
            long max = Collections.max(tiemposRespuesta);
            double promedio = tiemposRespuesta.stream().mapToLong(Long::longValue).average().orElse(0.0);
            
            System.out.printf("║ Tiempo mínimo: %-42.2f ms ║%n", min / 1_000_000.0);
            System.out.printf("║ Tiempo máximo: %-42.2f ms ║%n", max / 1_000_000.0);
            System.out.printf("║ Tiempo promedio: %-40.2f ms ║%n", promedio / 1_000_000.0);
        }
        
        // Estado final de recursos
        String estadoFinal = AdministradorInstalaciones.getInstance().getEstadisticas();
        System.out.printf("║ Estado final: %-46s ║%n", estadoFinal);
        System.out.printf("║ Archivo de log: %-42s ║%n", archivoLog);
        System.out.printf("║ Persistencia: %-44s ║%n", archivoPersistencia);
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        
        // Mostrar historial de semestres si hay
        if (estadosPorSemestre.size() > 0) {
            System.out.println("\n📚 HISTORIAL DE SEMESTRES:");
            estadosPorSemestre.forEach((semestre, estado) -> 
                System.out.printf("   Semestre %d: %s%n", semestre, estado));
        }
    }
    
    /**
     * Obtiene estado actual para monitoreo
     */
    public Map<String, Object> obtenerEstadoActual() {
        Map<String, Object> estado = new HashMap<>();
        estado.put("semestre", semestreActual);
        estado.put("solicitudesProcesadas", solicitudesProcesadas.get());
        estado.put("solicitudesExitosas", solicitudesExitosas.get());
        estado.put("solicitudesFallidas", solicitudesFallidas.get());
        estado.put("clientesConectados", clientesConectados.size());
        estado.put("modoReplica", modoReplica);
        estado.put("servidorPrincipalActivo", servidorPrincipalActivo);
        estado.put("estadoRecursos", AdministradorInstalaciones.getInstance().getEstadisticas());
        estado.put("archivoLog", archivoLog);
        estado.put("archivoPersistencia", archivoPersistencia);
        estado.put("configuracion", configuracion.obtenerPropiedades());
        return estado;
    }
    
    /**
     * Resetea métricas (útil para casos de prueba)
     */
    public void resetearMetricas() {
        solicitudesProcesadas.set(0);
        solicitudesExitosas.set(0);
        solicitudesFallidas.set(0);
        tiemposRespuesta.clear();
        clientesConectados.clear();
        ultimoHeartbeat.clear();
        System.out.println("[DTI ASÍNCRONO] 🔄 Métricas reseteadas");
        registrarEvento("METRICAS_RESETEADAS", "Métricas del servidor reiniciadas");
    }
    
    /**
     * Obtiene configuración actual
     */
    public ConfiguracionAsincrono getConfiguracion() {
        return configuracion;
    }
}