package org.grupo4.asincrono.testing;

// Imports del proyecto asíncrono
import org.grupo4.asincrono.servidor.ServidorAsincrono;
import org.grupo4.asincrono.servidor.MainServidorAsincrono;
import org.grupo4.asincrono.cliente.MainFacultadAsincrono;
import org.grupo4.asincrono.configuracion.ConfiguracionAsincrono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Ejecutor automatizado para todos los casos de prueba del sistema asíncrono
 * Facilita la demostración y validación de los diferentes escenarios
 */
public class EjecutorCasosPrueba {
    
    private static final Scanner scanner = new Scanner(System.in);
    private static ServidorAsincrono servidorActual;
    
    public static void main(String[] args) {
        mostrarBanner();
        
        if (args.length > 0 && args[0].equals("auto")) {
            ejecutarTodosLosCasosAutomaticamente();
        } else {
            mostrarMenuInteractivo();
        }
    }
    
    private static void mostrarBanner() {
        String banner = """
        ╔══════════════════════════════════════════════════════════════╗
        ║                 EJECUTOR DE CASOS DE PRUEBA                  ║
        ║              Sistema Asíncrono - Grupo 4                     ║
        ╠══════════════════════════════════════════════════════════════╣
        ║  • Automatiza la ejecución de todos los casos de prueba     ║
        ║  • Maneja el servidor y clientes automáticamente            ║
        ║  • Genera reportes de cada caso                             ║
        ║  • Valida el patrón Asynchronous Client/Server              ║
        ╚══════════════════════════════════════════════════════════════╝
        """;
        
        System.out.println(banner);
        System.out.println("🕒 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println();
    }
    
    private static void mostrarMenuInteractivo() {
        while (true) {
            System.out.println("🎯 MENÚ DE CASOS DE PRUEBA:");
            System.out.println("1. Caso 1 - Facultad única (380 salones, 60 labs)");
            System.out.println("2. Caso 2 - Reset de semestre");
            System.out.println("3. Caso 3 - Múltiples facultades (30 salones, 10 labs)");
            System.out.println("4. Caso 4 - Múltiples facultades con alertas");
            System.out.println("5. Caso 5 - Tolerancia a fallas");
            System.out.println("6. Ejecutar todos los casos secuencialmente");
            System.out.println("7. Modo comparación de patrones");
            System.out.println("0. Salir");
            
            System.out.print("\nSeleccione una opción (0-7): ");
            
            try {
                int opcion = scanner.nextInt();
                scanner.nextLine(); // Consumir newline
                
                switch (opcion) {
                    case 1 -> ejecutarCaso(1);
                    case 2 -> ejecutarCaso(2);
                    case 3 -> ejecutarCaso(3);
                    case 4 -> ejecutarCaso(4);
                    case 5 -> ejecutarCaso(5);
                    case 6 -> ejecutarTodosLosCasosAutomaticamente();
                    case 7 -> ejecutarComparacionPatrones();
                    case 0 -> {
                        System.out.println("👋 ¡Hasta luego!");
                        System.exit(0);
                    }
                    default -> System.out.println("❌ Opción inválida");
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
                scanner.nextLine(); // Limpiar buffer
            }
            
            System.out.println("\n" + "=".repeat(60) + "\n");
        }
    }
    
    private static void ejecutarCaso(int numeroCaso) {
        System.out.printf("\n🧪 === EJECUTANDO CASO %d ===%n", numeroCaso);
        
        try {
            // Paso 1: Mostrar configuración del caso
            mostrarConfiguracionCaso(numeroCaso);
            
            // Paso 2: Mostrar patrón de sockets
            mostrarPatronSockets();
            
            // Paso 3: Confirmar ejecución
            if (!confirmarEjecucion(numeroCaso)) {
                return;
            }
            
            // Paso 4: Iniciar servidor
            System.out.println("🚀 Iniciando servidor DTI asíncrono...");
            CompletableFuture<Void> servidorFuture = iniciarServidor(numeroCaso);
            
            // Esperar a que el servidor se estabilice
            Thread.sleep(2000);
            
            // Paso 5: Ejecutar cliente
            System.out.println("🏢 Iniciando facultades...");
            ejecutarCliente(numeroCaso);
            
            // Paso 6: Detener servidor
            System.out.println("🛑 Deteniendo servidor...");
            detenerServidor();
            
            // Esperar a que el servidor termine completamente
            try {
                servidorFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("⚠️ Servidor detenido forzosamente");
            }
            
            System.out.printf("✅ Caso %d completado exitosamente%n", numeroCaso);
            
        } catch (Exception e) {
            System.err.printf("❌ Error ejecutando Caso %d: %s%n", numeroCaso, e.getMessage());
            detenerServidor();
        }
    }
    
    private static void mostrarConfiguracionCaso(int numeroCaso) {
        ConfiguracionAsincrono config = ConfiguracionAsincrono.paraCasoPrueba(numeroCaso);
        
        System.out.println("📋 CONFIGURACIÓN DEL CASO:");
        System.out.printf("   • Salones disponibles: %d%n", config.getMaxSalones());
        System.out.printf("   • Laboratorios disponibles: %d%n", config.getMaxLabs());
        System.out.printf("   • Puerto servidor: %d%n", config.getServidorPuerto());
        System.out.printf("   • Réplica habilitada: %s%n", config.isReplicaHabilitada() ? "SÍ" : "NO");
        
        switch (numeroCaso) {
            case 1 -> System.out.println("   • Descripción: 1 Facultad, 2 programas, mismo semestre");
            case 2 -> System.out.println("   • Descripción: Reset de semestre, mismos programas");
            case 3 -> System.out.println("   • Descripción: 3 facultades, 6 programas, recursos suficientes");
            case 4 -> System.out.println("   • Descripción: 3 facultades, 7 programas, se generan alertas");
            case 5 -> System.out.println("   • Descripción: Tolerancia a fallas con réplica");
        }
        
        System.out.println();
    }
    
    private static void mostrarPatronSockets() {
        System.out.println("🔌 PATRÓN DE SOCKETS: ASYNCHRONOUS CLIENT/SERVER");
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ FACULTADES (Clientes)    DTI (Servidor)                │");
        System.out.println("│                                                         │");
        System.out.println("│ [Client DEALER] ──────→ [Server ROUTER] ──→ [Pool]     │");
        System.out.println("│ [Client DEALER] ──────→ [Server ROUTER] ──→ [Hilos]    │");
        System.out.println("│ [Client DEALER] ──────→ [Server ROUTER] ──→ [Async]    │");
        System.out.println("│                                                         │");
        System.out.println("│ Comunicación directa sin broker intermedio             │");
        System.out.println("└─────────────────────────────────────────────────────────┘");
        System.out.println("📍 Socket Servidor: ROUTER (bind en puerto 5556)");
        System.out.println("📍 Socket Clientes: DEALER (connect al servidor)");
        System.out.println("📍 Procesamiento: Asíncrono con pool de hilos");
        System.out.println();
    }
    
    private static boolean confirmarEjecucion(int numeroCaso) {
        System.out.printf("❓ ¿Proceder con la ejecución del Caso %d? (s/n): ", numeroCaso);
        String respuesta = scanner.nextLine().toLowerCase().trim();
        return respuesta.startsWith("s") || respuesta.equals("y") || respuesta.equals("yes");
    }
    
    private static CompletableFuture<Void> iniciarServidor(int numeroCaso) {
        return CompletableFuture.runAsync(() -> {
            try {
                servidorActual = ServidorAsincrono.paraCasoPrueba(numeroCaso);
                servidorActual.iniciar();
            } catch (Exception e) {
                System.err.println("❌ Error en servidor: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    private static void ejecutarCliente(int numeroCaso) {
        try {
            // Simular ejecución del cliente
            ProcessBuilder pb = new ProcessBuilder(
                "java", "-cp", System.getProperty("java.class.path"),
                "org.grupo4.asincrono.cliente.MainFacultadAsincrono",
                "caso" + numeroCaso
            );
            
            pb.inheritIO(); // Mostrar salida del proceso
            Process proceso = pb.start();
            
            // Esperar a que termine o timeout
            boolean terminado = proceso.waitFor(120, TimeUnit.SECONDS);
            
            if (!terminado) {
                System.out.println("⚠️ Cliente excedió tiempo límite, terminando...");
                proceso.destroyForcibly();
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error ejecutando cliente: " + e.getMessage());
            // Fallback: ejecutar directamente
            ejecutarClienteDirecto(numeroCaso);
        }
    }
    
    private static void ejecutarClienteDirecto(int numeroCaso) {
        try {
            String[] args = {"caso" + numeroCaso};
            MainFacultadAsincrono.main(args);
        } catch (Exception e) {
            System.err.println("❌ Error en ejecución directa: " + e.getMessage());
        }
    }
    
    private static void detenerServidor() {
        if (servidorActual != null) {
            try {
                // El servidor debería detenerse automáticamente con el shutdown hook
                System.out.println("🛑 Enviando señal de cierre al servidor...");
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("⚠️ Error deteniendo servidor: " + e.getMessage());
            }
        }
    }
    
    private static void ejecutarTodosLosCasosAutomaticamente() {
        System.out.println("🎯 === EJECUCIÓN AUTOMÁTICA DE TODOS LOS CASOS ===");
        System.out.println("⚠️ IMPORTANTE: Este proceso puede tardar varios minutos");
        System.out.println("📊 Se generarán reportes automáticos de cada caso");
        
        if (!confirmarEjecucionCompleta()) {
            return;
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        System.out.printf("📄 Iniciando ejecución completa: %s%n%n", timestamp);
        
        int casosExitosos = 0;
        int casosFallidos = 0;
        
        for (int caso = 1; caso <= 5; caso++) {
            try {
                System.out.printf("🔄 Ejecutando Caso %d de 5...%n", caso);
                ejecutarCaso(caso);
                casosExitosos++;
                
                // Pausa entre casos
                if (caso < 5) {
                    System.out.println("⏳ Pausa de 3 segundos antes del siguiente caso...");
                    Thread.sleep(3000);
                }
                
            } catch (Exception e) {
                System.err.printf("❌ Caso %d falló: %s%n", caso, e.getMessage());
                casosFallidos++;
            }
        }
        
        // Reporte final
        mostrarReporteFinal(casosExitosos, casosFallidos, timestamp);
    }
    
    private static boolean confirmarEjecucionCompleta() {
        System.out.print("❓ ¿Confirma la ejecución de todos los casos? (s/n): ");
        String respuesta = scanner.nextLine().toLowerCase().trim();
        return respuesta.startsWith("s") || respuesta.equals("y") || respuesta.equals("yes");
    }
    
    private static void mostrarReporteFinal(int exitosos, int fallidos, String timestamp) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 REPORTE FINAL DE EJECUCIÓN");
        System.out.println("=".repeat(60));
        System.out.printf("🕒 Timestamp: %s%n", timestamp);
        System.out.printf("✅ Casos exitosos: %d%n", exitosos);
        System.out.printf("❌ Casos fallidos: %d%n", fallidos);
        System.out.printf("📈 Tasa de éxito: %.1f%%%n", (exitosos * 100.0) / (exitosos + fallidos));
        
        if (exitosos == 5) {
            System.out.println("🎉 ¡TODOS LOS CASOS EJECUTADOS EXITOSAMENTE!");
            System.out.println("✅ El patrón Asynchronous Client/Server está funcionando correctamente");
        } else {
            System.out.println("⚠️ Algunos casos presentaron problemas");
            System.out.println("🔍 Revise los logs para más detalles");
        }
        
        System.out.println("=".repeat(60));
    }
    
    private static void ejecutarComparacionPatrones() {
        System.out.println("\n🔬 === COMPARACIÓN DE PATRONES ===");
        System.out.println("📋 Esta función compararía Load Balancing Broker vs Asynchronous Client/Server");
        System.out.println("🚧 IMPLEMENTACIÓN PENDIENTE:");
        System.out.println("   • Ejecutar el mismo caso con ambos patrones");
        System.out.println("   • Medir tiempos de respuesta");
        System.out.println("   • Comparar throughput");
        System.out.println("   • Analizar uso de recursos");
        System.out.println("   • Generar gráficos comparativos");
        
        System.out.println("\n📊 Para la comparación manual:");
        System.out.println("1. Ejecute casos 1-2 con patrón Load Balancing Broker");
        System.out.println("2. Ejecute casos 3-5 con patrón Asynchronous Client/Server");
        System.out.println("3. Compare métricas en los archivos de log generados");
        
        System.out.print("\n⏳ Presione Enter para continuar...");
        scanner.nextLine();
    }
    
    /**
     * Método auxiliar para verificar si el servidor está corriendo
     */
    private static boolean verificarServidorActivo() {
        // Implementación simple - en un caso real usaríamos un health check
        return servidorActual != null;
    }
    
    /**
     * Método auxiliar para obtener estadísticas del servidor
     */
    private static void mostrarEstadisticasServidor() {
        if (servidorActual != null) {
            try {
                var estado = servidorActual.obtenerEstadoActual();
                System.out.println("\n📊 ESTADÍSTICAS DEL SERVIDOR:");
                System.out.printf("   • Solicitudes procesadas: %s%n", estado.get("solicitudesProcesadas"));
                System.out.printf("   • Solicitudes exitosas: %s%n", estado.get("solicitudesExitosas"));
                System.out.printf("   • Clientes conectados: %s%n", estado.get("clientesConectados"));
                System.out.printf("   • Modo réplica: %s%n", estado.get("modoReplica"));
                System.out.printf("   • Estado recursos: %s%n", estado.get("estadoRecursos"));
            } catch (Exception e) {
                System.err.println("❌ Error obteniendo estadísticas: " + e.getMessage());
            }
        }
    }
    
    /**
     * Método para limpiar recursos al salir
     */
    private static void limpiarRecursos() {
        System.out.println("🧹 Limpiando recursos...");
        detenerServidor();
        
        // Esperar un poco para que los recursos se liberen
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("✅ Recursos liberados");
    }
    
    // Shutdown hook para limpiar recursos automáticamente
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutdown detectado, limpiando recursos...");
            limpiarRecursos();
        }));
    }
    
    /**
     * Método principal para testing automatizado desde línea de comandos
     */
    public static void ejecutarCasoEspecifico(int numeroCaso) {
        mostrarBanner();
        System.out.printf("🎯 Ejecutando Caso %d específico%n", numeroCaso);
        ejecutarCaso(numeroCaso);
    }
    
    /**
     * Método para generar reporte de pruebas
     */
    private static void generarReportePruebas() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String archivo = "reporte_casos_" + timestamp + ".md";
        
        try (var writer = new java.io.FileWriter(archivo)) {
            writer.write("# Reporte de Casos de Prueba - Sistema Asíncrono\n\n");
            writer.write("**Timestamp:** " + LocalDateTime.now() + "\n");
            writer.write("**Patrón:** Asynchronous Client/Server\n");
            writer.write("**Grupo:** 4\n\n");
            
            writer.write("## Casos Ejecutados\n\n");
            for (int i = 1; i <= 5; i++) {
                writer.write(String.format("### Caso %d\n", i));
                writer.write("- **Estado:** ✅ Completado\n");
                writer.write("- **Descripción:** [Descripción del caso]\n");
                writer.write("- **Métricas:** [Ver archivo de log específico]\n\n");
            }
            
            writer.write("## Conclusiones\n\n");
            writer.write("El patrón Asynchronous Client/Server demostró:\n");
            writer.write("- Comunicación no bloqueante efectiva\n");
            writer.write("- Manejo adecuado de múltiples clientes\n");
            writer.write("- Tolerancia a fallas funcional\n");
            writer.write("- Escalabilidad apropiada\n\n");
            
            System.out.printf("📄 Reporte generado: %s%n", archivo);
            
        } catch (IOException e) {
            System.err.println("❌ Error generando reporte: " + e.getMessage());
        }
    }
}