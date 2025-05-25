package org.grupo4.asincrono.cliente;

// Imports de proyectos hermanos - REUTILIZACIÓN
import org.grupo4proyecto.entidades.Facultad;
import org.grupo4proyecto.entidades.Solicitud;
import org.grupo4proyecto.redes.ResultadoEnvio;

// Import del proyecto asíncrono
import org.grupo4.asincrono.configuracion.ConfiguracionAsincrono;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clase principal para ejecutar Facultades con patrón Asynchronous Client/Server
 * Integra con las entidades existentes del proyecto para los casos de prueba
 */
public class MainFacultadAsincrono {
    
    private static final Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        mostrarBanner();
        
        // Determinar modo de ejecución
        int modo = determinarModoEjecucion(args);
        
        switch (modo) {
            case 1 -> ejecutarCaso1();
            case 2 -> ejecutarCaso2();
            case 3 -> ejecutarCaso3();
            case 4 -> ejecutarCaso4();
            case 5 -> ejecutarCaso5();
            default -> ejecutarModoInteractivo(args);
        }
    }
    
    private static void mostrarBanner() {
        String banner = """
        ╔══════════════════════════════════════════════════════════════╗
        ║                   FACULTAD ASÍNCRONA                         ║
        ║              Asynchronous Client/Server Pattern              ║
        ╠══════════════════════════════════════════════════════════════╣
        ║  • Comunicación no bloqueante con DTI                       ║
        ║  • Reutiliza entidades del proyecto original                ║
        ║  • Soporte para todos los casos de prueba                   ║
        ║  • Puerto 5556 por defecto (servidor asíncrono)             ║
        ╚══════════════════════════════════════════════════════════════╝
        """;
        
        System.out.println(banner);
    }
    
    private static int determinarModoEjecucion(String[] args) {
        if (args.length > 0) {
            try {
                String primerArg = args[0].toLowerCase();
                if (primerArg.startsWith("caso")) {
                    return Integer.parseInt(primerArg.substring(4));
                }
            } catch (Exception e) {
                // Continuar con modo interactivo
            }
        }
        
        System.out.println("🔧 MODOS DE EJECUCIÓN:");
        System.out.println("1. Caso 1 - 1 Facultad, 2 programas (380 salones, 60 labs)");
        System.out.println("2. Caso 2 - Nuevo semestre con reset de recursos");
        System.out.println("3. Caso 3 - 3 facultades, 6 programas (30 salones, 10 labs)");
        System.out.println("4. Caso 4 - 3 facultades, 7 programas con alertas");
        System.out.println("5. Caso 5 - Tolerancia a fallas con réplica");
        System.out.println("0. Modo interactivo");
        
        System.out.print("Seleccione modo (0-5): ");
        try {
            return scanner.nextInt();
        } catch (Exception e) {
            return 0;
        }
    }
    
    // ==================== CASO 1 ====================
    private static void ejecutarCaso1() {
        System.out.println("\n🧪 === EJECUTANDO CASO 1 ===");
        System.out.println("📋 1 Facultad (Ingeniería), 2 programas, mismo semestre");
        
        try {
            ConfiguracionAsincrono config = ConfiguracionAsincrono.paraCasoPrueba(1);
            
            // Crear facultad
            Facultad facultad = crearFacultad("Facultad de Ingeniería", config);
            
            // Crear solicitudes para el caso 1
            List<Solicitud> solicitudes = Arrays.asList(
                new Solicitud("Facultad de Ingeniería", "Ingeniería de Sistemas", 1, 10, 4),
                new Solicitud("Facultad de Ingeniería", "Ingeniería Civil", 1, 8, 2)
            );
            
            ejecutarCasoConUnaFacultad(facultad, solicitudes, 1);
            
        } catch (Exception e) {
            System.err.println("❌ Error ejecutando Caso 1: " + e.getMessage());
        }
    }
    
    // ==================== CASO 2 ====================
    private static void ejecutarCaso2() {
        System.out.println("\n🧪 === EJECUTANDO CASO 2 ===");
        System.out.println("📋 Reset de semestre - mismos programas, diferentes recursos");
        
        try {
            ConfiguracionAsincrono config = ConfiguracionAsincrono.paraCasoPrueba(2);
            
            // Crear facultad
            Facultad facultad = crearFacultad("Facultad de Ingeniería", config);
            
            // Primero ejecutar solicitudes del semestre 1
            System.out.println("🔄 SEMESTRE 1:");
            List<Solicitud> solicitudesSem1 = Arrays.asList(
                new Solicitud("Facultad de Ingeniería", "Ingeniería de Sistemas", 1, 10, 4),
                new Solicitud("Facultad de Ingeniería", "Ingeniería Civil", 1, 8, 2)
            );
            
            ejecutarCasoConUnaFacultad(facultad, solicitudesSem1, 1);
            
            // Esperar confirmación para continuar
            System.out.println("\n⏳ Presione Enter para continuar al Semestre 2...");
            scanner.nextLine();
            scanner.nextLine();
            
            // Ahora ejecutar solicitudes del semestre 2
            System.out.println("\n🔄 SEMESTRE 2:");
            List<Solicitud> solicitudesSem2 = Arrays.asList(
                new Solicitud("Facultad de Ingeniería", "Ingeniería de Sistemas", 2, 8, 2),
                new Solicitud("Facultad de Ingeniería", "Ingeniería Civil", 2, 6, 3)
            );
            
            ejecutarCasoConUnaFacultad(facultad, solicitudesSem2, 2);
            
        } catch (Exception e) {
            System.err.println("❌ Error ejecutando Caso 2: " + e.getMessage());
        }
    }
    
    // ==================== CASO 3 ====================
    private static void ejecutarCaso3() {
        System.out.println("\n🧪 === EJECUTANDO CASO 3 ===");
        System.out.println("📋 3 facultades, 6 programas - todos los recursos se pueden satisfacer");
        
        try {
            ConfiguracionAsincrono config = ConfiguracionAsincrono.paraCasoPrueba(3);
            
            // Crear facultades
            List<Facultad> facultades = Arrays.asList(
                crearFacultad("Facultad de Ingeniería", config),
                crearFacultad("Facultad de Ciencias", config),
                crearFacultad("Facultad de Medicina", config)
            );
            
            // Crear solicitudes para cada facultad (2 programas cada una)
            Map<Facultad, List<Solicitud>> solicitudesPorFacultad = new HashMap<>();
            
            solicitudesPorFacultad.put(facultades.get(0), Arrays.asList(
                new Solicitud("Facultad de Ingeniería", "Ingeniería de Sistemas", 1, 5, 2),
                new Solicitud("Facultad de Ingeniería", "Ingeniería Civil", 1, 5, 2)
            ));
            
            solicitudesPorFacultad.put(facultades.get(1), Arrays.asList(
                new Solicitud("Facultad de Ciencias", "Biología", 1, 5, 2),
                new Solicitud("Facultad de Ciencias", "Química", 1, 5, 2)
            ));
            
            solicitudesPorFacultad.put(facultades.get(2), Arrays.asList(
                new Solicitud("Facultad de Medicina", "Medicina General", 1, 5, 2),
                new Solicitud("Facultad de Medicina", "Enfermería", 1, 5, 0) // Sin laboratorios
            ));
            
            ejecutarCasoConMultiplesFacultades(solicitudesPorFacultad, 3);
            
        } catch (Exception e) {
            System.err.println("❌ Error ejecutando Caso 3: " + e.getMessage());
        }
    }
    
    // ==================== CASO 4 ====================
    private static void ejecutarCaso4() {
        System.out.println("\n🧪 === EJECUTANDO CASO 4 ===");
        System.out.println("📋 3 facultades, 7 programas - se deben generar alertas");
        
        try {
            ConfiguracionAsincrono config = ConfiguracionAsincrono.paraCasoPrueba(4);
            
            // Crear facultades
            List<Facultad> facultades = Arrays.asList(
                crearFacultad("Facultad de Ingeniería", config),
                crearFacultad("Facultad de Ciencias", config),
                crearFacultad("Facultad de Medicina", config)
            );
            
            // Crear solicitudes que excedan la capacidad
            Map<Facultad, List<Solicitud>> solicitudesPorFacultad = new HashMap<>();
            
            solicitudesPorFacultad.put(facultades.get(0), Arrays.asList(
                new Solicitud("Facultad de Ingeniería", "Ingeniería de Sistemas", 1, 5, 2),
                new Solicitud("Facultad de Ingeniería", "Ingeniería Civil", 1, 5, 2)
            ));
            
            solicitudesPorFacultad.put(facultades.get(1), Arrays.asList(
                new Solicitud("Facultad de Ciencias", "Biología", 1, 5, 2),
                new Solicitud("Facultad de Ciencias", "Química", 1, 5, 2),
                new Solicitud("Facultad de Ciencias", "Física", 1, 5, 2)
            ));
            
            solicitudesPorFacultad.put(facultades.get(2), Arrays.asList(
                new Solicitud("Facultad de Medicina", "Medicina General", 1, 5, 2),
                new Solicitud("Facultad de Medicina", "Enfermería", 1, 5, 2) // Esta debería fallar
            ));
            
            ejecutarCasoConMultiplesFacultades(solicitudesPorFacultad, 4);
            
        } catch (Exception e) {
            System.err.println("❌ Error ejecutando Caso 4: " + e.getMessage());
        }
    }
    
    // ==================== CASO 5 ====================
    private static void ejecutarCaso5() {
        System.out.println("\n🧪 === EJECUTANDO CASO 5 ===");
        System.out.println("📋 Tolerancia a fallas - simulación de caída del servidor");
        
        try {
            ConfiguracionAsincrono config = ConfiguracionAsincrono.paraCasoPrueba(5);
            
            // Crear facultades
            List<Facultad> facultades = Arrays.asList(
                crearFacultad("Facultad de Ingeniería", config),
                crearFacultad("Facultad de Ciencias", config),
                crearFacultad("Facultad de Medicina", config)
            );
            
            // Fase 1: Procesar primeras dos facultades
            System.out.println("🔄 FASE 1: Procesando primeras dos facultades...");
            
            Map<Facultad, List<Solicitud>> fase1 = new HashMap<>();
            fase1.put(facultades.get(0), Arrays.asList(
                new Solicitud("Facultad de Ingeniería", "Ingeniería de Sistemas", 1, 5, 2),
                new Solicitud("Facultad de Ingeniería", "Ingeniería Civil", 1, 5, 2)
            ));
            
            fase1.put(facultades.get(1), Arrays.asList(
                new Solicitud("Facultad de Ciencias", "Biología", 1, 5, 2)
            ));
            
            ejecutarCasoConMultiplesFacultades(fase1, 5);
            
            // Simular falla del servidor
            System.out.println("\n💥 SIMULANDO FALLA DEL SERVIDOR...");
            simularFallaServidor(facultades.get(0));
            
            // Esperar a que se recupere
            Thread.sleep(6000);
            
            // Fase 2: Procesar tercera facultad con servidor recuperado
            System.out.println("\n🔄 FASE 2: Procesando tercera facultad con réplica...");
            
            Map<Facultad, List<Solicitud>> fase2 = new HashMap<>();
            fase2.put(facultades.get(2), Arrays.asList(
                new Solicitud("Facultad de Medicina", "Medicina General", 1, 5, 2),
                new Solicitud("Facultad de Medicina", "Enfermería", 1, 5, 2),
                new Solicitud("Facultad de Medicina", "Odontología", 1, 5, 2)
            ));
            
            ejecutarCasoConMultiplesFacultades(fase2, 5);
            
        } catch (Exception e) {
            System.err.println("❌ Error ejecutando Caso 5: " + e.getMessage());
        }
    }
    
    // ==================== MODO INTERACTIVO ====================
    private static void ejecutarModoInteractivo(String[] args) {
        System.out.println("\n🔧 === MODO INTERACTIVO ===");
        
        try {
            // Configurar facultad
            Facultad facultad = configurarFacultadInteractiva(args);
            ConfiguracionAsincrono config = new ConfiguracionAsincrono();
            
            // Generar solicitudes interactivamente
            List<Solicitud> solicitudes = generarSolicitudesInteractivas(facultad);
            
            ejecutarCasoConUnaFacultad(facultad, solicitudes, 1);
            
        } catch (Exception e) {
            System.err.println("❌ Error en modo interactivo: " + e.getMessage());
        }
    }
    
    // ==================== MÉTODOS AUXILIARES ====================
    
    private static Facultad crearFacultad(String nombre, ConfiguracionAsincrono config) throws Exception {
        Facultad facultad = new Facultad();
        facultad.setNombre(nombre);
        facultad.setDirServidorCentral(InetAddress.getByName(config.getClienteServidorIp()));
        facultad.setPuertoServidorCentral(config.getClienteServidorPuerto());
        return facultad;
    }
    
    private static void ejecutarCasoConUnaFacultad(Facultad facultad, List<Solicitud> solicitudes, int numeroCaso) {
        System.out.printf("🏢 Ejecutando con: %s%n", facultad.getNombre());
        System.out.printf("📍 Servidor: %s:%d%n", 
            facultad.getDirServidorCentral().getHostAddress(), 
            facultad.getPuertoServidorCentral());
        
        try (ClienteAsincrono cliente = new ClienteAsincrono(facultad)) {
            
            // Mostrar patrón de sockets antes de empezar
            mostrarPatronSockets();
            
            List<ResultadoEnvio> resultados = new ArrayList<>();
            List<Long> tiempos = new ArrayList<>();
            
            for (Solicitud solicitud : solicitudes) {
                System.out.printf("📤 Enviando: %s (%d salones, %d labs)%n", 
                    solicitud.getPrograma(), solicitud.getNumSalones(), solicitud.getNumLaboratorios());
                
                long inicio = System.nanoTime();
                ResultadoEnvio resultado = cliente.enviarSolicitudServidor(solicitud);
                long fin = System.nanoTime();
                
                tiempos.add(fin - inicio);
                
                if (resultado != null) {
                    resultados.add(resultado);
                    System.out.printf("📨 Respuesta: %s%n", resultado.getInfoGeneral());
                    
                    // Procesar confirmación
                    if (!resultado.getInfoGeneral().contains("ALERTA")) {
                        boolean aceptar = confirmarAsignacion(solicitud, resultado);
                        String confirmacion = cliente.confirmarAsignacion(solicitud, resultado, aceptar);
                        System.out.printf("✅ Confirmación: %s%n", confirmacion);
                    }
                } else {
                    System.out.println("❌ No se recibió respuesta del servidor");
                }
                
                System.out.println("────────────────────────────────────────");
            }
            
            // Guardar resultados en archivo
            guardarResultados(facultad, solicitudes, resultados, tiempos, numeroCaso);
            
            // Mostrar estadísticas
            mostrarEstadisticas(cliente, tiempos, resultados.size());
            
        } catch (Exception e) {
            System.err.printf("❌ Error ejecutando caso: %s%n", e.getMessage());
        }
    }
    
    private static void ejecutarCasoConMultiplesFacultades(Map<Facultad, List<Solicitud>> solicitudesPorFacultad, int numeroCaso) {
        System.out.printf("🏢 Ejecutando con %d facultades%n", solicitudesPorFacultad.size());
        
        // Mostrar patrón de sockets
        mostrarPatronSockets();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger solicitudesProcesadas = new AtomicInteger(0);
        AtomicInteger solicitudesExitosas = new AtomicInteger(0);
        
        for (Map.Entry<Facultad, List<Solicitud>> entry : solicitudesPorFacultad.entrySet()) {
            Facultad facultad = entry.getKey();
            List<Solicitud> solicitudes = entry.getValue();
            
            // Procesar cada facultad de forma asíncrona
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try (ClienteAsincrono cliente = new ClienteAsincrono(facultad)) {
                    
                    System.out.printf("🏢 [%s] Iniciando procesamiento...%n", facultad.getNombre());
                    
                    for (Solicitud solicitud : solicitudes) {
                        System.out.printf("📤 [%s] Enviando: %s%n", 
                            facultad.getNombre(), solicitud.getPrograma());
                        
                        ResultadoEnvio resultado = cliente.enviarSolicitudServidor(solicitud);
                        solicitudesProcesadas.incrementAndGet();
                        
                        if (resultado != null) {
                            System.out.printf("📨 [%s] %s: %s%n", 
                                facultad.getNombre(), solicitud.getPrograma(), resultado.getInfoGeneral());
                            
                            if (!resultado.getInfoGeneral().contains("ALERTA")) {
                                // Auto-aceptar para casos de prueba automáticos
                                cliente.confirmarAsignacion(solicitud, resultado, true);
                                solicitudesExitosas.incrementAndGet();
                            }
                        }
                    }
                    
                    System.out.printf("✅ [%s] Procesamiento completado%n", facultad.getNombre());
                    
                } catch (Exception e) {
                    System.err.printf("❌ [%s] Error: %s%n", facultad.getNombre(), e.getMessage());
                }
            });
            
            futures.add(future);
        }
        
        // Esperar a que todas las facultades terminen
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            
            System.out.println("\n📊 RESULTADOS FINALES:");
            System.out.printf("   • Solicitudes procesadas: %d%n", solicitudesProcesadas.get());
            System.out.printf("   • Solicitudes exitosas: %d%n", solicitudesExitosas.get());
            System.out.printf("   • Solicitudes fallidas: %d%n", 
                solicitudesProcesadas.get() - solicitudesExitosas.get());
            
        } catch (Exception e) {
            System.err.printf("❌ Error esperando resultados: %s%n", e.getMessage());
        }
    }
    
    private static void simularFallaServidor(Facultad facultad) {
        try (ClienteAsincrono cliente = new ClienteAsincrono(facultad)) {
            System.out.println("💥 Enviando comando de simulación de falla...");
            cliente.simularFallaServidor();
        } catch (Exception e) {
            System.err.printf("❌ Error simulando falla: %s%n", e.getMessage());
        }
    }
    
    private static void mostrarPatronSockets() {
        System.out.println("\n🔌 PATRÓN DE SOCKETS: ASYNCHRONOUS CLIENT/SERVER");
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ PROGRAMAS → FACULTAD → DTI                              │");
        System.out.println("│                                                         │");
        System.out.println("│ [Prog REQ] → [FAC REP] → [FAC DEALER] → [DTI ROUTER]   │");
        System.out.println("│                                                         │");
        System.out.println("│ Máquina 3    Máquina 2     Máquina 2     Máquina 1    │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println("📍 Comunicación directa, no bloqueante");
        System.out.println("📍 Procesamiento asíncrono en servidor");
        System.out.println();
    }
    
    private static boolean confirmarAsignacion(Solicitud solicitud, ResultadoEnvio resultado) {
        System.out.printf("❓ ¿Acepta la asignación para %s?%n", solicitud.getPrograma());
        System.out.printf("   Salones: %d, Labs: %d, Aulas móviles: %d%n", 
            resultado.getSalonesAsignados(), 
            resultado.getLabsAsignados(), 
            resultado.getAulaMovilAsignadas());
        System.out.print("   (s/n): ");
        
        try {
            String respuesta = scanner.nextLine().toLowerCase().trim();
            return respuesta.startsWith("s") || respuesta.equals("y") || respuesta.equals("yes");
        } catch (Exception e) {
            return true; // Por defecto aceptar
        }
    }
    
    private static void guardarResultados(Facultad facultad, List<Solicitud> solicitudes, 
                                        List<ResultadoEnvio> resultados, List<Long> tiempos, int numeroCaso) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String archivo = String.format("resultado_caso%d_%s_%s.txt", 
                numeroCaso, facultad.getNombre().replaceAll("[^a-zA-Z0-9]", "_"), timestamp);
            
            try (FileWriter writer = new FileWriter(archivo)) {
                writer.write("=".repeat(60) + "\n");
                writer.write(String.format("RESULTADOS CASO %d - %s\n", numeroCaso, facultad.getNombre()));
                writer.write("Patrón: Asynchronous Client/Server\n");
                writer.write("Timestamp: " + LocalDateTime.now() + "\n");
                writer.write("=".repeat(60) + "\n\n");
                
                for (int i = 0; i < solicitudes.size(); i++) {
                    Solicitud sol = solicitudes.get(i);
                    ResultadoEnvio res = i < resultados.size() ? resultados.get(i) : null;
                    long tiempo = i < tiempos.size() ? tiempos.get(i) : 0;
                    
                    writer.write(String.format("SOLICITUD %d:\n", i + 1));
                    writer.write(String.format("  Programa: %s\n", sol.getPrograma()));
                    writer.write(String.format("  Semestre: %d\n", sol.getSemestre()));
                    writer.write(String.format("  Solicitado: %d salones, %d laboratorios\n", 
                        sol.getNumSalones(), sol.getNumLaboratorios()));
                    
                    if (res != null) {
                        writer.write(String.format("  Asignado: %d salones, %d labs, %d aulas móviles\n",
                            res.getSalonesAsignados(), res.getLabsAsignados(), res.getAulaMovilAsignadas()));
                        writer.write(String.format("  Resultado: %s\n", res.getInfoGeneral()));
                    } else {
                        writer.write("  ERROR: No se recibió respuesta\n");
                    }
                    
                    writer.write(String.format("  Tiempo respuesta: %.2f ms\n", tiempo / 1_000_000.0));
                    writer.write("\n");
                }
                
                // Estadísticas
                if (!tiempos.isEmpty()) {
                    long min = Collections.min(tiempos);
                    long max = Collections.max(tiempos);
                    double promedio = tiempos.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    
                    writer.write("ESTADÍSTICAS:\n");
                    writer.write(String.format("  Tiempo mínimo: %.2f ms\n", min / 1_000_000.0));
                    writer.write(String.format("  Tiempo máximo: %.2f ms\n", max / 1_000_000.0));
                    writer.write(String.format("  Tiempo promedio: %.2f ms\n", promedio / 1_000_000.0));
                    writer.write(String.format("  Solicitudes exitosas: %d/%d\n", resultados.size(), solicitudes.size()));
                }
            }
            
            System.out.printf("💾 Resultados guardados en: %s%n", archivo);
            
        } catch (IOException e) {
            System.err.printf("❌ Error guardando resultados: %s%n", e.getMessage());
        }
    }
    
    private static void mostrarEstadisticas(ClienteAsincrono cliente, List<Long> tiempos, int exitosas) {
        ClienteAsincrono.EstadisticasCliente stats = cliente.obtenerEstadisticas();
        
        System.out.println("\n📊 ESTADÍSTICAS DEL CLIENTE:");
        System.out.printf("   • Identificador: %s%n", stats.identificador());
        System.out.printf("   • Facultad: %s%n", stats.nombreFacultad());
        System.out.printf("   • Mensajes enviados: %d%n", stats.mensajesEnviados());
        System.out.printf("   • Mensajes recibidos: %d%n", stats.mensajesRecibidos());
        System.out.printf("   • Solicitudes pendientes: %d%n", stats.solicitudesPendientes());
        System.out.printf("   • Servidor disponible: %s%n", stats.servidorDisponible() ? "SÍ" : "NO");
        
        if (!tiempos.isEmpty()) {
            long min = Collections.min(tiempos);
            long max = Collections.max(tiempos);
            double promedio = tiempos.stream().mapToLong(Long::longValue).average().orElse(0.0);
            
            System.out.println("\n⏱️ TIEMPOS DE RESPUESTA:");
            System.out.printf("   • Mínimo: %.2f ms%n", min / 1_000_000.0);
            System.out.printf("   • Máximo: %.2f ms%n", max / 1_000_000.0);
            System.out.printf("   • Promedio: %.2f ms%n", promedio / 1_000_000.0);
            System.out.printf("   • Exitosas: %d/%d%n", exitosas, tiempos.size());
        }
    }
    
    private static Facultad configurarFacultadInteractiva(String[] args) throws Exception {
        Facultad facultad = new Facultad();
        
        if (args.length >= 3) {
            facultad.setNombre(args[0]);
            facultad.setDirServidorCentral(InetAddress.getByName(args[1]));
            facultad.setPuertoServidorCentral(Integer.parseInt(args[2]));
        } else {
            System.out.print("Nombre de la facultad: ");
            facultad.setNombre(scanner.nextLine());
            
            System.out.print("IP del servidor DTI [localhost]: ");
            String ip = scanner.nextLine().trim();
            if (ip.isEmpty()) ip = "localhost";
            facultad.setDirServidorCentral(InetAddress.getByName(ip));
            
            System.out.print("Puerto del servidor DTI [5556]: ");
            String puerto = scanner.nextLine().trim();
            int puertoNum = puerto.isEmpty() ? 5556 : Integer.parseInt(puerto);
            facultad.setPuertoServidorCentral(puertoNum);
        }
        
        System.out.printf("✅ Facultad configurada: %s → %s:%d%n", 
            facultad.getNombre(), 
            facultad.getDirServidorCentral().getHostAddress(),
            facultad.getPuertoServidorCentral());
        
        return facultad;
    }
    
    private static List<Solicitud> generarSolicitudesInteractivas(Facultad facultad) {
        List<Solicitud> solicitudes = new ArrayList<>();
        
        System.out.print("¿Cuántos programas académicos procesará? ");
        int numProgramas = scanner.nextInt();
        scanner.nextLine(); // Consumir newline
        
        for (int i = 1; i <= numProgramas; i++) {
            System.out.printf("\n--- PROGRAMA %d ---\n", i);
            System.out.print("Nombre del programa: ");
            String programa = scanner.nextLine();
            
            System.out.print("Semestre: ");
            int semestre = scanner.nextInt();
            
            System.out.print("Número de salones: ");
            int salones = scanner.nextInt();
            
            System.out.print("Número de laboratorios: ");
            int labs = scanner.nextInt();
            scanner.nextLine(); // Consumir newline
            
            solicitudes.add(new Solicitud(facultad.getNombre(), programa, semestre, salones, labs));
        }
        
        return solicitudes;
    }
}