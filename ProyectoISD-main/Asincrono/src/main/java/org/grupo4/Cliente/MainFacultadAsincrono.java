package org.grupo4.asincrono.cliente;

// Imports de proyectos hermanos - REUTILIZACIÓN
import org.grupo4proyecto.entidades.Facultad;
import org.grupo4proyecto.entidades.Solicitud;
import org.grupo4proyecto.redes.ResultadoEnvio;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clase principal para probar el Cliente Asíncrono
 * Demuestra las capacidades del patrón Asynchronous Client/Server desde el lado del cliente
 */
public class MainClienteAsincrono {
    
    private static final String FACULTAD_DEFAULT = "Facultad de Ingeniería (Async)";
    private static final String IP_DEFAULT = "localhost";
    private static final int PUERTO_DEFAULT = 5556; // Puerto del servidor asíncrono
    private static final int SEMESTRE_DEFAULT = 1;
    
    public static void main(String[] args) {
        mostrarBanner();
        
        // Configurar facultad
        Facultad facultad = configurarFacultad(args);
        
        // Generar solicitudes de prueba
        List<Solicitud> solicitudes = generarSolicitudesPrueba(facultad);
        
        // Mostrar menú de opciones
        int opcion = mostrarMenuYObtenerOpcion();
        
        // Ejecutar opción seleccionada
        ejecutarOpcion(opcion, facultad, solicitudes);
    }
    
    /**
     * Muestra banner del cliente asíncrono
     */
    private static void mostrarBanner() {
        String banner = """
        ╔══════════════════════════════════════════════════════════════╗
        ║                    CLIENTE ASÍNCRONO                         ║
        ║              Asynchronous Client/Server Pattern              ║
        ╠══════════════════════════════════════════════════════════════╣
        ║  • Comunicación no bloqueante con servidor                  ║
        ║  • Callbacks y CompletableFuture                            ║
        ║  • Múltiples solicitudes concurrentes                       ║
        ║  • Reutiliza entidades del proyecto Facultad                ║
        ║  • Se conecta al puerto 5556 por defecto                    ║
        ╚══════════════════════════════════════════════════════════════╝
        """;
        
        System.out.println(banner);
    }
    
    /**
     * Configura la facultad según argumentos o valores por defecto
     */
    private static Facultad configurarFacultad(String[] args) {
        Facultad facultad = new Facultad();
        
        try {
            if (args.length >= 3) {
                // Configuración desde argumentos
                facultad.setNombre(args[0]);
                facultad.setDirServidorCentral(InetAddress.getByName(args[1]));
                facultad.setPuertoServidorCentral(Integer.parseInt(args[2]));
                
                System.out.printf("📋 Configuración desde argumentos:%n");
                System.out.printf("   • Facultad: %s%n", args[0]);
                System.out.printf("   • Servidor: %s:%s%n", args[1], args[2]);
                
            } else {
                // Configuración por defecto
                facultad.setNombre(FACULTAD_DEFAULT);
                facultad.setDirServidorCentral(InetAddress.getByName(IP_DEFAULT));
                facultad.setPuertoServidorCentral(PUERTO_DEFAULT);
                
                System.out.printf("📋 Configuración por defecto:%n");
                System.out.printf("   • Facultad: %s%n", FACULTAD_DEFAULT);
                System.out.printf("   • Servidor: %s:%d%n", IP_DEFAULT, PUERTO_DEFAULT);
            }
            
            System.out.println();
            return facultad;
            
        } catch (Exception e) {
            System.err.printf("❌ Error en configuración: %s%n", e.getMessage());
            System.err.println("Usando configuración por defecto...");
            
            try {
                facultad.setNombre(FACULTAD_DEFAULT);
                facultad.setDirServidorCentral(InetAddress.getByName(IP_DEFAULT));
                facultad.setPuertoServidorCentral(PUERTO_DEFAULT);
            } catch (Exception ex) {
                throw new RuntimeException("Error fatal en configuración", ex);
            }
            
            return facultad;
        }
    }
    
    /**
     * Genera solicitudes de prueba
     */
    private static List<Solicitud> generarSolicitudesPrueba(Facultad facultad) {
        List<Solicitud> solicitudes = new ArrayList<>();
        
        // Solicitudes variadas para demostrar diferentes casos
        solicitudes.add(new Solicitud(facultad.getNombre(), "Ingeniería de Sistemas", SEMESTRE_DEFAULT, 5, 3));
        solicitudes.add(new Solicitud(facultad.getNombre(), "Ingeniería Civil", SEMESTRE_DEFAULT, 7, 2));
        solicitudes.add(new Solicitud(facultad.getNombre(), "Ingeniería Electrónica", SEMESTRE_DEFAULT, 4, 4));
        solicitudes.add(new Solicitud(facultad.getNombre(), "Ingeniería Industrial", SEMESTRE_DEFAULT, 6, 3));
        solicitudes.add(new Solicitud(facultad.getNombre(), "Ingeniería Mecánica", SEMESTRE_DEFAULT, 8, 1));
        
        System.out.printf("📚 Generadas %d solicitudes de prueba%n%n", solicitudes.size());
        return solicitudes;
    }
    
    /**
     * Muestra menú de opciones y obtiene selección del usuario
     */
    private static int mostrarMenuYObtenerOpcion() {
        String menu = """
        ═══════════════════════════════════════════════════════════════
        SELECCIONE MODO DE OPERACIÓN:
        ═══════════════════════════════════════════════════════════════
        
        1. 🔄 MODO SÍNCRONO COMPATIBLE
           • Comportamiento idéntico al cliente original
           • Una solicitud a la vez
           • Espera respuesta antes de continuar
        
        2. ⚡ MODO ASÍNCRONO CON FUTURES
           • Envía todas las solicitudes inmediatamente
           • Procesa respuestas conforme llegan
           • Demuestra paralelismo real
        
        3. 📞 MODO ASÍNCRONO CON CALLBACKS
           • Callbacks automáticos para respuestas
           • Procesamiento en segundo plano
           • No bloquea hilo principal
        
        4. 🔬 MODO EXPERIMENTAL (múltiples clientes)
           • Simula múltiples facultades conectadas
           • Prueba de carga del servidor
           • Métricas detalladas
        
        5. ❌ SALIR
        
        ═══════════════════════════════════════════════════════════════
        """;
        
        System.out.print(menu);
        System.out.print("Ingrese su opción (1-5): ");
        
        Scanner scanner = new Scanner(System.in);
        try {
            return scanner.nextInt();
        } catch (Exception e) {
            return 1; // Por defecto
        }
    }
    
    /**
     * Ejecuta la opción seleccionada
     */
    private static void ejecutarOpcion(int opcion, Facultad facultad, List<Solicitud> solicitudes) {
        switch (opcion) {
            case 1 -> ejecutarModoSincrono(facultad, solicitudes);
            case 2 -> ejecutarModoAsincronoConFutures(facultad, solicitudes);
            case 3 -> ejecutarModoAsincronoConCallbacks(facultad, solicitudes);
            case 4 -> ejecutarModoExperimental(facultad, solicitudes);
            case 5 -> {
                System.out.println("👋 ¡Hasta luego!");
                System.exit(0);
            }
            default -> {
                System.out.println("⚠️ Opción no válida, ejecutando modo síncrono por defecto");
                ejecutarModoSincrono(facultad, solicitudes);
            }
        }
    }
    
    /**
     * Modo 1: Operación síncrona compatible
     */
    private static void ejecutarModoSincrono(Facultad facultad, List<Solicitud> solicitudes) {
        System.out.println("\n🔄 === MODO SÍNCRONO COMPATIBLE ===");
        
        List<Long> tiempos = new ArrayList<>();
        int exitosas = 0, fallidas = 0;
        
        try (ClienteAsincrono cliente = new ClienteAsincrono(facultad)) {
            Scanner scanner = new Scanner(System.in);
            
            for (Solicitud solicitud : solicitudes) {
                long inicio = System.nanoTime();
                
                System.out.printf("📤 Enviando solicitud: %s (%d salones, %d labs)%n", 
                    solicitud.getPrograma(), solicitud.getNumSalones(), solicitud.getNumLaboratorios());
                
                ResultadoEnvio resultado = cliente.enviarSolicitudServidor(solicitud);
                
                long fin = System.nanoTime();
                tiempos.add(fin - inicio);
                
                if (resultado != null) {
                    System.out.printf("📨 Respuesta: %s%n", resultado.getInfoGeneral());
                    
                    if (!resultado.getInfoGeneral().contains("ALERTA")) {
                        System.out.print("¿Acepta la asignación? (s/n): ");
                        String respuesta = scanner.nextLine();
                        
                        boolean aceptado = respuesta.toLowerCase().startsWith("s");
                        String confirmacion = cliente.confirmarAsignacion(solicitud, resultado, aceptado);
                        
                        System.out.printf("✅ Confirmación: %s%n", confirmacion);
                        exitosas++;
                    } else {
                        fallidas++;
                    }
                } else {
                    fallidas++;
                }
                
                System.out.println("────────────────────────────────────────");
            }
            
            imprimirMetricas("SÍNCRONO", tiempos, exitosas, fallidas);
        }
    }
    
    /**
     * Modo 2: Operación asíncrona con CompletableFuture
     */
    private static void ejecutarModoAsincronoConFutures(Facultad facultad, List<Solicitud> solicitudes) {
        System.out.println("\n⚡ === MODO ASÍNCRONO CON FUTURES ===");
        
        List<Long> tiempos = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger exitosas = new AtomicInteger(0);
        AtomicInteger fallidas = new AtomicInteger(0);
        
        try (ClienteAsincrono cliente = new ClienteAsincrono(facultad)) {
            Scanner scanner = new Scanner(System.in);
            
            // Enviar todas las solicitudes de forma asíncrona
            List<CompletableFuture<ResultadoEnvio>> futures = new ArrayList<>();
            long inicioGeneral = System.nanoTime();
            
            System.out.println("📤 Enviando todas las solicitudes de forma asíncrona...");
            
            for (Solicitud solicitud : solicitudes) {
                long inicioIndividual = System.nanoTime();
                
                CompletableFuture<ResultadoEnvio> future = cliente.enviarSolicitudAsincrona(solicitud)
                    .thenApply(resultado -> {
                        long finIndividual = System.nanoTime();
                        tiempos.add(finIndividual - inicioIndividual);
                        
                        System.out.printf("📨 Respuesta asíncrona para %s: %s%n", 
                            solicitud.getPrograma(), resultado.getInfoGeneral());
                        return resultado;
                    });
                
                futures.add(future);
            }
            
            // Esperar todas las respuestas
            System.out.println("⏳ Esperando todas las respuestas asíncronas...");
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            long finGeneral = System.nanoTime();
            System.out.printf("🚀 Todas las solicitudes procesadas en %.2f ms%n%n", 
                (finGeneral - inicioGeneral) / 1_000_000.0);
            
            // Procesar confirmaciones
            for (int i = 0; i < futures.size(); i++) {
                try {
                    ResultadoEnvio resultado = futures.get(i).get();
                    Solicitud solicitud = solicitudes.get(i);
                    
                    if (resultado != null && !resultado.getInfoGeneral().contains("ALERTA")) {
                        System.out.printf("📋 Confirmación para %s:%n", solicitud.getPrograma());
                        System.