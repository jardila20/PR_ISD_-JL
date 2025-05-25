package org.grupo4.asincrono.cliente;

// Imports de proyectos hermanos - REUTILIZACIÓN
import org.grupo4proyecto.entidades.Solicitud;
import org.grupo4proyecto.entidades.Facultad;
import org.grupo4proyecto.redes.ResultadoEnvio;
import org.grupo4proyecto.redes.ConfirmacionAsignacion;

// Import del proyecto asíncrono
import org.grupo4.asincrono.configuracion.ConfiguracionAsincrono;

// Imports propios
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cliente Asíncrono para Facultades - Versión Completa
 * 
 * DISTRIBUCIÓN:
 * - Máquina 1: DTI (Servidor)
 * - Máquina 2: Facultades (Este cliente)
 * - Máquina 3: Programas Académicos
 * 
 * CASOS SOPORTADOS:
 * - Comunicación asíncrona con DTI
 * - Reset de semestre automático
 * - Tolerancia a fallas del servidor
 * - Distribución en múltiples máquinas
 * - Integración completa con entidades existentes
 */
public class ClienteAsincrono implements AutoCloseable {
    
    // Configuración del cliente
    private final Facultad facultad;
    private final ConfiguracionAsincrono configuracion;
    private final ZContext contexto;
    private final Socket cliente;
    private final String identificadorFacultad;
    
    // Manejo asíncrono
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService executorRespuestas = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, CompletableFuture<ResultadoEnvio>> solicitudesPendientes = new ConcurrentHashMap<>();
    private final AtomicLong contadorSolicitudes = new AtomicLong(0);
    
    // Estado del cliente
    private volatile boolean activo = true;
    private Thread hiloReceptor;
    private int semestreActual = 1;
    
    // Métricas y logging
    private final AtomicLong mensajesEnviados = new AtomicLong(0);
    private final AtomicLong mensajesRecibidos = new AtomicLong(0);
    private final AtomicLong erroresConexion = new AtomicLong(0);
    private final String archivoLog;
    
    // Gestión de conexión para tolerancia a fallas
    private volatile boolean servidorDisponible = true;
    private volatile long ultimoHeartbeat = System.currentTimeMillis();
    private final long timeoutConexion;
    private final int maxReintentos;
    
    /**
     * Constructor principal con facultad y configuración
     */
    public ClienteAsincrono(Facultad facultad, ConfiguracionAsincrono configuracion) {
        this.facultad = facultad;
        this.configuracion = configuracion;
        this.timeoutConexion = configuracion.getClienteTimeoutMs();
        this.maxReintentos = configuracion.getClienteReintentos();
        
        this.contexto = new ZContext();
        this.identificadorFacultad = generarIdentificadorFacultad();
        this.archivoLog = String.format("cliente_asincrono_%s_%s.log", 
            facultad.getNombre().replaceAll("[^a-zA-Z0-9]", "_"),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        
        // Crear socket DEALER para comunicación asíncrona
        this.cliente = contexto.createSocket(SocketType.DEALER);
        
        // Configurar socket
        configurarSocket();
        
        // Conectar al servidor DTI
        conectarAServidor();
        
        // Iniciar receptor de respuestas
        iniciarReceptorRespuestas();
        
        // Log inicial
        registrarEvento("CLIENTE_INICIADO", String.format("Facultad: %s, Servidor: %s:%d", 
            facultad.getNombre(), 
            facultad.getDirServidorCentral().getHostAddress(),
            facultad.getPuertoServidorCentral()));
    }
    
    /**
     * Constructor con facultad (usa configuración por defecto)
     */
    public ClienteAsincrono(Facultad facultad) {
        this(facultad, new ConfiguracionAsincrono());
    }
    
    /**
     * Constructor para casos de prueba específicos
     */
    public static ClienteAsincrono paraCasoPrueba(int numeroCaso, String nombreFacultad) throws Exception {
        ConfiguracionAsincrono config = ConfiguracionAsincrono.paraCasoPrueba(numeroCaso);
        
        Facultad facultad = new Facultad();
        facultad.setNombre(nombreFacultad);
        facultad.setDirServidorCentral(InetAddress.getByName(config.getClienteServidorIp()));
        facultad.setPuertoServidorCentral(config.getClienteServidorPuerto());
        
        return new ClienteAsincrono(facultad, config);
    }
    
    /**
     * Genera identificador único para la facultad
     */
    private String generarIdentificadorFacultad() {
        return String.format("FAC_%s_%d", 
            facultad.getNombre().replaceAll("[^a-zA-Z0-9]", ""), 
            System.currentTimeMillis() % 10000);
    }
    
    /**
     * Configura el socket con opciones específicas para casos de prueba
     */
    private void configurarSocket() {
        // Configurar identidad única
        cliente.setIdentity(identificadorFacultad.getBytes(ZMQ.CHARSET));
        
        // Configurar timeouts para tolerancia a fallas
        cliente.setReceiveTimeOut((int)timeoutConexion / 6);  // Timeout más corto para recepción
        cliente.setSendTimeOut((int)timeoutConexion / 6);     // Timeout más corto para envío
        
        // Configurar reconexión automática
        cliente.setReconnectIVL(1000);    // Intentar reconectar cada 1 segundo
        cliente.setReconnectIVLMax(5000); // Máximo 5 segundos entre intentos
        
        // Configurar buffer para evitar pérdida de mensajes
        cliente.setSndHWM(1000);
        cliente.setRcvHWM(1000);
        
        System.out.printf("[FACULTAD ASÍNCRONA] 🔧 Socket configurado para: %s%n", identificadorFacultad);
        registrarEvento("SOCKET_CONFIGURADO", String.format("ID: %s, Timeouts: %dms", identificadorFacultad, timeoutConexion));
    }
    
    /**
     * Conecta al servidor DTI con reintentos
     */
    private void conectarAServidor() {
        String endpoint = String.format("tcp://%s:%d", 
            facultad.getDirServidorCentral().getHostAddress(),
            facultad.getPuertoServidorCentral());
        
        int intentos = 0;
        boolean conectado = false;
        
        while (intentos < maxReintentos && !conectado) {
            try {
                cliente.connect(endpoint);
                conectado = verificarConexion();
                
                if (conectado) {
                    System.out.printf("[FACULTAD ASÍNCRONA] 🔗 %s conectada a DTI: %s (intento %d)%n", 
                        facultad.getNombre(), endpoint, intentos + 1);
                    registrarEvento("CONEXION_EXITOSA", String.format("Endpoint: %s, Intentos: %d", endpoint, intentos + 1));
                    mostrarSocketsPattern();
                } else {
                    intentos++;
                    if (intentos < maxReintentos) {
                        System.out.printf("[FACULTAD ASÍNCRONA] ⚠️ Intento %d fallido, reintentando...%n", intentos);
                        Thread.sleep(1000 * intentos); // Backoff exponencial
                    }
                }
                
            } catch (Exception e) {
                intentos++;
                erroresConexion.incrementAndGet();
                System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error conexión intento %d: %s%n", intentos, e.getMessage());
                
                if (intentos < maxReintentos) {
                    try {
                        Thread.sleep(1000 * intentos);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        if (!conectado) {
            servidorDisponible = false;
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ No se pudo conectar después de %d intentos%n", maxReintentos);
            registrarEvento("CONEXION_FALLIDA", String.format("Intentos agotados: %d", maxReintentos));
        }
    }
    
    /**
     * Verifica la conexión enviando un ping
     */
    private boolean verificarConexion() {
        try {
            cliente.send("PING_SERVER", ZMQ.DONTWAIT);
            String respuesta = cliente.recvStr(1000); // 1 segundo timeout
            return "PONG".equals(respuesta);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Muestra el patrón de sockets (requerido para casos de prueba)
     */
    private void mostrarSocketsPattern() {
        System.out.println("\n🔌 PATRÓN DE SOCKETS - FACULTAD:");
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ PROGRAMAS → FACULTAD → DTI                              │");
        System.out.println("│                                                         │");
        System.out.println("│ [Prog REQ] → [FAC REP] → [FAC DEALER] → [DTI ROUTER]   │");
        System.out.println("│                                                         │");
        System.out.println("│ Máquina 3    Máquina 2     Máquina 2     Máquina 1    │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.printf("📍 Facultad: %s (Socket DEALER)%n", identificadorFacultad);
        System.out.printf("📍 Conectado a DTI: %s:%d%n", 
            facultad.getDirServidorCentral().getHostAddress(),
            facultad.getPuertoServidorCentral());
        System.out.println("📍 Comunicación: Asíncrona, no bloqueante");
        System.out.println();
    }
    
    /**
     * Inicia hilo receptor de respuestas asíncronas
     */
    private void iniciarReceptorRespuestas() {
        hiloReceptor = new Thread(() -> {
            System.out.printf("[FACULTAD ASÍNCRONA] 🎧 Receptor iniciado para %s%n", facultad.getNombre());
            
            while (activo && !Thread.currentThread().isInterrupted()) {
                try {
                    // Escuchar respuestas del DTI
                    String respuesta = cliente.recvStr(ZMQ.DONTWAIT);
                    if (respuesta != null) {
                        procesarRespuestaAsincrona(respuesta);
                        mensajesRecibidos.incrementAndGet();
                        ultimoHeartbeat = System.currentTimeMillis();
                        servidorDisponible = true; // Servidor respondió, está disponible
                    } else {
                        Thread.sleep(10);
                        verificarHeartbeat();
                    }
                } catch (Exception e) {
                    if (activo) {
                        System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error en receptor: %s%n", e.getMessage());
                        erroresConexion.incrementAndGet();
                        verificarDisponibilidadServidor();
                    }
                }
            }
            
            System.out.printf("[FACULTAD ASÍNCRONA] 🛑 Receptor detenido para %s%n", facultad.getNombre());
        });
        
        hiloReceptor.setDaemon(true);
        hiloReceptor.start();
    }
    
    /**
     * Verifica heartbeat con el servidor
     */
    private void verificarHeartbeat() {
        long tiempoActual = System.currentTimeMillis();
        if (tiempoActual - ultimoHeartbeat > timeoutConexion) {
            System.out.printf("[FACULTAD ASÍNCRONA] 💓 Enviando heartbeat a DTI...%n");
            try {
                cliente.send("HEARTBEAT", ZMQ.DONTWAIT);
                mensajesEnviados.incrementAndGet();
            } catch (Exception e) {
                System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error enviando heartbeat: %s%n", e.getMessage());
                servidorDisponible = false;
            }
        }
    }
    
    /**
     * Verifica si el servidor DTI está disponible (para Caso 5 - tolerancia a fallas)
     */
    private void verificarDisponibilidadServidor() {
        try {
            // Enviar mensaje de ping
            cliente.send("PING_SERVER", ZMQ.DONTWAIT);
            mensajesEnviados.incrementAndGet();
            
            // Esperar respuesta breve
            String respuesta = cliente.recvStr(1000); // 1 segundo timeout
            
            if ("PONG".equals(respuesta)) {
                servidorDisponible = true;
                ultimoHeartbeat = System.currentTimeMillis();
                System.out.printf("[FACULTAD ASÍNCRONA] ✅ Servidor DTI disponible%n");
            } else {
                servidorDisponible = false;
                System.out.printf("[FACULTAD ASÍNCRONA] ⚠️ Servidor DTI no responde correctamente%n");
                registrarEvento("SERVIDOR_NO_DISPONIBLE", "Respuesta inválida o timeout");
            }
            
        } catch (Exception e) {
            servidorDisponible = false;
            System.out.printf("[FACULTAD ASÍNCRONA] ❌ Servidor DTI no disponible: %s%n", e.getMessage());
            registrarEvento("SERVIDOR_ERROR", e.getMessage());
        }
    }
    
    /**
     * Solicita reset de semestre al servidor (Caso 2)
     */
    public boolean solicitarResetSemestre(int nuevoSemestre) {
        try {
            if (nuevoSemestre == semestreActual) {
                return true; // Ya estamos en el semestre correcto
            }
            
            System.out.printf("[FACULTAD ASÍNCRONA] 🔄 Solicitando reset de semestre %d → %d%n", 
                semestreActual, nuevoSemestre);
            
            String comando = "RESET_SEMESTRE:" + nuevoSemestre;
            cliente.send(comando, ZMQ.DONTWAIT);
            mensajesEnviados.incrementAndGet();
            
            // Esperar confirmación
            String respuesta = cliente.recvStr((int)timeoutConexion);
            if (respuesta != null) {
                mensajesRecibidos.incrementAndGet();
            }
            
            if (respuesta != null && respuesta.startsWith("SEMESTRE_RESET_OK")) {
                semestreActual = nuevoSemestre;
                System.out.printf("[FACULTAD ASÍNCRONA] ✅ Semestre cambiado a %d%n", nuevoSemestre);
                registrarEvento("SEMESTRE_CAMBIADO", String.format("Nuevo semestre: %d", nuevoSemestre));
                return true;
            } else {
                System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error cambiando semestre: %s%n", respuesta);
                return false;
            }
            
        } catch (Exception e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error en reset de semestre: %s%n", e.getMessage());
            registrarEvento("ERROR_RESET_SEMESTRE", e.getMessage());
            return false;
        }
    }
    
    /**
     * Solicita simulación de falla del servidor (Caso 5)
     */
    public boolean simularFallaServidor() {
        try {
            System.out.println("[FACULTAD ASÍNCRONA] 💥 Solicitando simulación de falla del servidor...");
            
            cliente.send("SIMULAR_FALLA", ZMQ.DONTWAIT);
            mensajesEnviados.incrementAndGet();
            
            String respuesta = cliente.recvStr(15000); // 15 segundos para la simulación
            if (respuesta != null) {
                mensajesRecibidos.incrementAndGet();
            }
            
            if (respuesta != null && (respuesta.equals("REPLICA_ACTIVADA") || respuesta.equals("FALLA_SIMULADA"))) {
                System.out.println("[FACULTAD ASÍNCRONA] ✅ Simulación de falla procesada correctamente");
                registrarEvento("FALLA_SIMULADA", "Comando enviado exitosamente");
                return true;
            } else {
                System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error en simulación de falla: %s%n", respuesta);
                return false;
            }
            
        } catch (Exception e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error simulando falla: %s%n", e.getMessage());
            registrarEvento("ERROR_SIMULACION_FALLA", e.getMessage());
            return false;
        }
    }
    
    /**
     * Envía solicitud de forma asíncrona
     */
    public CompletableFuture<ResultadoEnvio> enviarSolicitudAsincrona(Solicitud solicitud) {
        if (!servidorDisponible) {
            return CompletableFuture.failedFuture(new RuntimeException("Servidor DTI no disponible"));
        }
        
        try {
            // Actualizar semestre si es necesario
            if (solicitud.getSemestre() != semestreActual) {
                if (!solicitarResetSemestre(solicitud.getSemestre())) {
                    return CompletableFuture.failedFuture(new RuntimeException("Error cambiando semestre"));
                }
            }
            
            // Crear identificador único para tracking
            String idSolicitud = generarIdSolicitud();
            
            // Crear Future para la respuesta
            CompletableFuture<ResultadoEnvio> futureRespuesta = new CompletableFuture<>();
            solicitudesPendientes.put(idSolicitud, futureRespuesta);
            
            // Configurar timeout para el Future
            futureRespuesta.orTimeout(timeoutConexion, TimeUnit.MILLISECONDS);
            
            // Enviar solicitud
            String payload = json.writeValueAsString(solicitud);
            cliente.send(payload, ZMQ.DONTWAIT);
            mensajesEnviados.incrementAndGet();
            
            System.out.printf("[FACULTAD ASÍNCRONA] 📤 Solicitud enviada desde %s: %s (%d salones, %d labs)%n", 
                facultad.getNombre(), solicitud.getPrograma(), 
                solicitud.getNumSalones(), solicitud.getNumLaboratorios());
            
            registrarEvento("SOLICITUD_ENVIADA", 
                String.format("ID: %s, Programa: %s, Salones: %d, Labs: %d", 
                    idSolicitud, solicitud.getPrograma(), solicitud.getNumSalones(), solicitud.getNumLaboratorios()));
            
            return futureRespuesta;
            
        } catch (Exception e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error enviando solicitud: %s%n", e.getMessage());
            registrarEvento("ERROR_ENVIO_SOLICITUD", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Método síncrono compatible para casos de prueba simples
     */
    public ResultadoEnvio enviarSolicitudServidor(Solicitud solicitud) {
        try {
            System.out.printf("[FACULTAD ASÍNCRONA] 🔄 Solicitud síncrona desde %s para: %s%n", 
                facultad.getNombre(), solicitud.getPrograma());
            
            CompletableFuture<ResultadoEnvio> future = enviarSolicitudAsincrona(solicitud);
            ResultadoEnvio resultado = future.get(timeoutConexion, TimeUnit.MILLISECONDS);
            
            System.out.printf("[FACULTAD ASÍNCRONA] 📨 Respuesta recibida: %s%n", 
                resultado.getInfoGeneral().length() > 50 ? 
                resultado.getInfoGeneral().substring(0, 50) + "..." : 
                resultado.getInfoGeneral());
            
            registrarEvento("SOLICITUD_COMPLETADA", 
                String.format("Programa: %s, Resultado: %s", solicitud.getPrograma(), resultado.getInfoGeneral()));
            
            return resultado;
            
        } catch (TimeoutException e) {
            System.err.println("[FACULTAD ASÍNCRONA] ⏰ Timeout esperando respuesta del DTI");
            registrarEvento("TIMEOUT", "Timeout esperando respuesta del servidor");
            return null;
        } catch (Exception e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error en solicitud síncrona: %s%n", e.getMessage());
            registrarEvento("ERROR_SOLICITUD_SINCRONA", e.getMessage());
            return null;
        }
    }
    
    /**
     * Confirma asignación (síncrono para casos de prueba)
     */
    public String confirmarAsignacion(Solicitud solicitud, ResultadoEnvio resultado, boolean aceptado) {
        try {
            String encabezado = aceptado ? 
                "CONFIRMAR_ASIGNACION:" + solicitud.getPrograma() :
                "RECHAZAR_ASIGNACION:" + solicitud.getPrograma();
            
            ConfirmacionAsignacion confirmacion = new ConfirmacionAsignacion(encabezado, resultado);
            String payload = json.writeValueAsString(confirmacion);
            
            cliente.send(payload, ZMQ.DONTWAIT);
            mensajesEnviados.incrementAndGet();
            
            System.out.printf("[FACULTAD ASÍNCRONA] 📋 Confirmación enviada desde %s: %s%n", 
                facultad.getNombre(), aceptado ? "ACEPTADA" : "RECHAZADA");
            
            // Esperar respuesta de confirmación
            String respuesta = cliente.recvStr((int)timeoutConexion);
            if (respuesta != null) {
                mensajesRecibidos.incrementAndGet();
            }
            
            registrarEvento("CONFIRMACION_ENVIADA", 
                String.format("Programa: %s, Tipo: %s, Respuesta: %s", 
                    solicitud.getPrograma(), aceptado ? "ACEPTACION" : "RECHAZO", respuesta));
            
            return respuesta != null ? respuesta : "ERROR_CONFIRMACION";
            
        } catch (Exception e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error en confirmación: %s%n", e.getMessage());
            registrarEvento("ERROR_CONFIRMACION", e.getMessage());
            return "[FACULTAD] Error en la recepción de la confirmación";
        }
    }
    
    /**
     * Procesa respuestas asíncronas del DTI
     */
    private void procesarRespuestaAsincrona(String respuestaJson) {
        try {
            // Manejar respuestas de heartbeat/ping
            if ("PONG".equals(respuestaJson)) {
                ultimoHeartbeat = System.currentTimeMillis();
                servidorDisponible = true;
                return;
            }
            
            // Procesar como ResultadoEnvio
            ResultadoEnvio resultado = json.readValue(respuestaJson, ResultadoEnvio.class);
            
            System.out.printf("[FACULTAD ASÍNCRONA] 📨 Respuesta asíncrona: %s%n", 
                resultado.getInfoGeneral().length() > 50 ? 
                resultado.getInfoGeneral().substring(0, 50) + "..." : 
                resultado.getInfoGeneral());
            
            // Completar la primera solicitud pendiente (simplificado para casos de prueba)
            if (!solicitudesPendientes.isEmpty()) {
                String primeraSolicitud = solicitudesPendientes.keys().nextElement();
                CompletableFuture<ResultadoEnvio> future = solicitudesPendientes.remove(primeraSolicitud);
                if (future != null && !future.isDone()) {
                    future.complete(resultado);
                }
            }
            
            registrarEvento("RESPUESTA_RECIBIDA", resultado.getInfoGeneral());
            
        } catch (Exception e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error procesando respuesta: %s%n", e.getMessage());
            registrarEvento("ERROR_PROCESAMIENTO_RESPUESTA", e.getMessage());
        }
    }
    
    /**
     * Genera ID único para solicitud
     */
    private String generarIdSolicitud() {
        return String.format("SOL-%s-%d", 
            identificadorFacultad, 
            contadorSolicitudes.incrementAndGet());
    }
    
    /**
     * Registra evento en archivo de log
     */
    private void registrarEvento(String tipo, String detalles) {
        try (FileWriter writer = new FileWriter(archivoLog, true)) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String linea = String.format("[%s] %s - %s: %s%n", 
                timestamp, facultad.getNombre(), tipo, detalles);
            writer.write(linea);
        } catch (IOException e) {
            System.err.println("Error escribiendo log: " + e.getMessage());
        }
    }
    
    /**
     * Obtiene estadísticas del cliente
     */
    public EstadisticasCliente obtenerEstadisticas() {
        return new EstadisticasCliente(
            identificadorFacultad,
            facultad.getNombre(),
            mensajesEnviados.get(),
            mensajesRecibidos.get(),
            solicitudesPendientes.size(),
            semestreActual,
            servidorDisponible,
            activo,
            erroresConexion.get(),
            archivoLog
        );
    }
    
    /**
     * Cancela todas las solicitudes pendientes
     */
    public void cancelarSolicitudesPendientes() {
        int canceladas = solicitudesPendientes.size();
        solicitudesPendientes.values().forEach(future -> future.cancel(true));
        solicitudesPendientes.clear();
        
        if (canceladas > 0) {
            System.out.printf("[FACULTAD ASÍNCRONA] 🚫 %d solicitudes canceladas para %s%n", 
                canceladas, facultad.getNombre());
            registrarEvento("SOLICITUDES_CANCELADAS", String.format("Cantidad: %d", canceladas));
        }
    }
    
    @Override
    public void close() {
        System.out.printf("[FACULTAD ASÍNCRONA] 🛑 Cerrando cliente %s...%n", facultad.getNombre());
        
        activo = false;
        
        // Cancelar solicitudes pendientes
        cancelarSolicitudesPendientes();
        
        // Detener hilo receptor
        if (hiloReceptor != null) {
            hiloReceptor.interrupt();
            try {
                hiloReceptor.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Cerrar executor
        executorRespuestas.shutdown();
        try {
            if (!executorRespuestas.awaitTermination(2, TimeUnit.SECONDS)) {
                executorRespuestas.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorRespuestas.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Cerrar conexiones ZMQ
        if (cliente != null) cliente.close();
        if (contexto != null) contexto.close();
        
        // Estadísticas finales
        EstadisticasCliente stats = obtenerEstadisticas();
        System.out.printf("[FACULTAD ASÍNCRONA] 📊 %s - Enviados: %d, Recibidos: %d, Errores: %d%n", 
            stats.nombreFacultad(), stats.mensajesEnviados(), stats.mensajesRecibidos(), stats.erroresConexion());
        
        registrarEvento("CLIENTE_CERRADO", "Cierre limpio completado");
        System.out.printf("[FACULTAD ASÍNCRONA] ✅ Cliente %s cerrado correctamente%n", facultad.getNombre());
    }
    
    // ======================== CLASES AUXILIARES ========================
    
    /**
     * Record para estadísticas del cliente (versión extendida)
     */
    public record EstadisticasCliente(
        String identificador,
        String nombreFacultad,
        long mensajesEnviados,
        long mensajesRecibidos,
        int solicitudesPendientes,
        int semestreActual,
        boolean servidorDisponible,
        boolean activo,
        long erroresConexion,
        String archivoLog
    ) {
        
        /**
         * Calcula la tasa de éxito de comunicación
         */
        public double tasaExito() {
            long total = mensajesEnviados;
            if (total == 0) return 0.0;
            return ((double) mensajesRecibidos / total) * 100.0;
        }
        
        /**
         * Calcula la tasa de error
         */
        public double tasaError() {
            long total = mensajesEnviados;
            if (total == 0) return 0.0;
            return ((double) erroresConexion / total) * 100.0;
        }
        
        /**
         * Genera reporte de estadísticas
         */
        public String generarReporte() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ESTADÍSTICAS CLIENTE ASÍNCRONO ===\n");
            sb.append(String.format("Facultad: %s (%s)\n", nombreFacultad, identificador));
            sb.append(String.format("Semestre actual: %d\n", semestreActual));
            sb.append(String.format("Estado: %s\n", activo ? "ACTIVO" : "INACTIVO"));
            sb.append(String.format("Servidor disponible: %s\n", servidorDisponible ? "SÍ" : "NO"));
            sb.append(String.format("Mensajes enviados: %d\n", mensajesEnviados));
            sb.append(String.format("Mensajes recibidos: %d\n", mensajesRecibidos));
            sb.append(String.format("Solicitudes pendientes: %d\n", solicitudesPendientes));
            sb.append(String.format("Errores de conexión: %d\n", erroresConexion));
            sb.append(String.format("Tasa de éxito: %.2f%%\n", tasaExito()));
            sb.append(String.format("Tasa de error: %.2f%%\n", tasaError()));
            sb.append(String.format("Archivo de log: %s\n", archivoLog));
            return sb.toString();
        }
    }
    
    /**
     * Configuración específica para el cliente
     */
    public static class ConfiguracionCliente {
        private long timeoutMs = 30000;
        private int reintentos = 3;
        private boolean heartbeatHabilitado = true;
        private long intervaloHeartbeat = 5000;
        private boolean logDetallado = false;
        
        // Getters y setters
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        
        public int getReintentos() { return reintentos; }
        public void setReintentos(int reintentos) { this.reintentos = reintentos; }
        
        public boolean isHeartbeatHabilitado() { return heartbeatHabilitado; }
        public void setHeartbeatHabilitado(boolean heartbeatHabilitado) { this.heartbeatHabilitado = heartbeatHabilitado; }
        
        public long getIntervaloHeartbeat() { return intervaloHeartbeat; }
        public void setIntervaloHeartbeat(long intervaloHeartbeat) { this.intervaloHeartbeat = intervaloHeartbeat; }
        
        public boolean isLogDetallado() { return logDetallado; }
        public void setLogDetallado(boolean logDetallado) { this.logDetallado = logDetallado; }
    }
    
    // ======================== MÉTODOS DE UTILIDAD ========================
    
    /**
     * Obtiene la configuración actual del cliente
     */
    public ConfiguracionAsincrono getConfiguracion() {
        return configuracion;
    }
    
    /**
     * Obtiene la facultad asociada
     */
    public Facultad getFacultad() {
        return facultad;
    }
    
    /**
     * Verifica si el cliente está activo
     */
    public boolean isActivo() {
        return activo;
    }
    
    /**
     * Verifica si el servidor está disponible
     */
    public boolean isServidorDisponible() {
        return servidorDisponible;
    }
    
    /**
     * Obtiene el semestre actual
     */
    public int getSemestreActual() {
        return semestreActual;
    }
    
    /**
     * Fuerza reconexión al servidor
     */
    public boolean reconectar() {
        System.out.printf("[FACULTAD ASÍNCRONA] 🔄 Forzando reconexión para %s...%n", facultad.getNombre());
        servidorDisponible = false;
        
        try {
            // Cerrar conexión actual
            cliente.disconnect(String.format("tcp://%s:%d", 
                facultad.getDirServidorCentral().getHostAddress(),
                facultad.getPuertoServidorCentral()));
            
            Thread.sleep(1000);
            
            // Reconectar
            conectarAServidor();
            
            return servidorDisponible;
        } catch (Exception e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error en reconexión: %s%n", e.getMessage());
            registrarEvento("ERROR_RECONEXION", e.getMessage());
            return false;
        }
    }
    
    /**
     * Resetea las métricas del cliente
     */
    public void resetearMetricas() {
        mensajesEnviados.set(0);
        mensajesRecibidos.set(0);
        erroresConexion.set(0);
        contadorSolicitudes.set(0);
        solicitudesPendientes.clear();
        
        System.out.printf("[FACULTAD ASÍNCRONA] 🔄 Métricas reseteadas para %s%n", facultad.getNombre());
        registrarEvento("METRICAS_RESETEADAS", "Todas las métricas han sido reiniciadas");
    }
    
    /**
     * Envía comando personalizado al servidor
     */
    public String enviarComando(String comando) {
        try {
            if (!servidorDisponible) {
                return "ERROR: Servidor no disponible";
            }
            
            cliente.send(comando, ZMQ.DONTWAIT);
            mensajesEnviados.incrementAndGet();
            
            String respuesta = cliente.recvStr((int)timeoutConexion);
            if (respuesta != null) {
                mensajesRecibidos.incrementAndGet();
            }
            
            registrarEvento("COMANDO_ENVIADO", String.format("Comando: %s, Respuesta: %s", comando, respuesta));
            return respuesta != null ? respuesta : "ERROR: Sin respuesta";
            
        } catch (Exception e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error enviando comando: %s%n", e.getMessage());
            registrarEvento("ERROR_COMANDO", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }
    
    /**
     * Obtiene información del servidor
     */
    public String obtenerInfoServidor() {
        return enviarComando("INFO_SERVIDOR");
    }
    
    /**
     * Solicita estadísticas del servidor
     */
    public String obtenerEstadisticasServidor() {
        return enviarComando("ESTADISTICAS_SERVIDOR");
    }
    
    /**
     * Genera reporte completo del cliente
     */
    public void generarReporte() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String archivo = String.format("reporte_cliente_%s_%s.txt", 
            facultad.getNombre().replaceAll("[^a-zA-Z0-9]", "_"), timestamp);
        
        try (FileWriter writer = new FileWriter(archivo)) {
            EstadisticasCliente stats = obtenerEstadisticas();
            writer.write(stats.generarReporte());
            writer.write("\n=== CONFIGURACIÓN ===\n");
            writer.write(String.format("Timeout: %d ms\n", timeoutConexion));
            writer.write(String.format("Reintentos máximos: %d\n", maxReintentos));
            writer.write(String.format("Identificador: %s\n", identificadorFacultad));
            writer.write(String.format("Archivo de log: %s\n", archivoLog));
            
            System.out.printf("[FACULTAD ASÍNCRONA] 📄 Reporte generado: %s%n", archivo);
            
        } catch (IOException e) {
            System.err.printf("[FACULTAD ASÍNCRONA] ❌ Error generando reporte: %s%n", e.getMessage());
        }
    }
}