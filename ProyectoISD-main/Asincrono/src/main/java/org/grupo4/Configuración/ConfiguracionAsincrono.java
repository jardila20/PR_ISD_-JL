package org.grupo4.asincrono.configuracion;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuración centralizada para el sistema asíncrono
 * Maneja la configuración tanto del servidor como de los clientes
 */
public class ConfiguracionAsincrono {
    
    // Configuración por defecto del servidor
    public static final String DEFAULT_SERVER_IP = "0.0.0.0";
    public static final int DEFAULT_SERVER_PORT = 5556;
    public static final int DEFAULT_MAX_SALONES = 380;
    public static final int DEFAULT_MAX_LABS = 60;
    public static final int DEFAULT_THREAD_POOL_SIZE = 10;
    
    // Configuración por defecto del cliente
    public static final String DEFAULT_CLIENT_SERVER_IP = "localhost";
    public static final int DEFAULT_CLIENT_SERVER_PORT = 5556;
    public static final long DEFAULT_TIMEOUT_MS = 30000;
    public static final int DEFAULT_RETRY_ATTEMPTS = 3;
    
    // Configuración para casos de prueba
    public static final int CASO_1_SALONES = 380;
    public static final int CASO_1_LABS = 60;
    public static final int CASO_3_SALONES = 30;
    public static final int CASO_3_LABS = 10;
    public static final int CASO_4_SALONES = 30;
    public static final int CASO_4_LABS = 12;
    public static final int CASO_5_SALONES = 30;
    public static final int CASO_5_LABS = 12;
    
    private Properties propiedades;
    private String archivoConfiguracion;
    
    /**
     * Constructor por defecto - usa valores hardcoded
     */
    public ConfiguracionAsincrono() {
        this.propiedades = new Properties();
        cargarConfiguracionPorDefecto();
    }
    
    /**
     * Constructor con archivo de configuración
     */
    public ConfiguracionAsincrono(String archivoConfiguracion) {
        this.archivoConfiguracion = archivoConfiguracion;
        this.propiedades = new Properties();
        
        if (!cargarDesdeArchivo()) {
            System.out.println("[CONFIG] No se pudo cargar " + archivoConfiguracion + ", usando valores por defecto");
            cargarConfiguracionPorDefecto();
        }
    }
    
    /**
     * Configuración específica para casos de prueba
     */
    public static ConfiguracionAsincrono paraCasoPrueba(int numeroCaso) {
        ConfiguracionAsincrono config = new ConfiguracionAsincrono();
        
        switch (numeroCaso) {
            case 1:
            case 2:
                config.propiedades.setProperty("servidor.maxSalones", String.valueOf(CASO_1_SALONES));
                config.propiedades.setProperty("servidor.maxLabs", String.valueOf(CASO_1_LABS));
                break;
            case 3:
                config.propiedades.setProperty("servidor.maxSalones", String.valueOf(CASO_3_SALONES));
                config.propiedades.setProperty("servidor.maxLabs", String.valueOf(CASO_3_LABS));
                break;
            case 4:
                config.propiedades.setProperty("servidor.maxSalones", String.valueOf(CASO_4_SALONES));
                config.propiedades.setProperty("servidor.maxLabs", String.valueOf(CASO_4_LABS));
                break;
            case 5:
                config.propiedades.setProperty("servidor.maxSalones", String.valueOf(CASO_5_SALONES));
                config.propiedades.setProperty("servidor.maxLabs", String.valueOf(CASO_5_LABS));
                // Configuración especial para tolerancia a fallas
                config.propiedades.setProperty("servidor.replicaHabilitada", "true");
                config.propiedades.setProperty("servidor.heartbeatInterval", "5000");
                break;
        }
        
        System.out.printf("[CONFIG] Configuración cargada para Caso %d: %d salones, %d labs%n", 
            numeroCaso, config.getMaxSalones(), config.getMaxLabs());
        
        return config;
    }
    
    /**
     * Carga configuración desde archivo
     */
    private boolean cargarDesdeArchivo() {
        try {
            // Intentar cargar desde resources primero
            InputStream input = getClass().getResourceAsStream("/" + archivoConfiguracion);
            if (input == null) {
                // Intentar cargar desde archivo externo
                input = new FileInputStream(archivoConfiguracion);
            }
            
            propiedades.load(input);
            input.close();
            
            System.out.println("[CONFIG] Configuración cargada desde: " + archivoConfiguracion);
            return true;
            
        } catch (IOException e) {
            System.err.println("[CONFIG] Error cargando configuración: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Carga valores por defecto
     */
    private void cargarConfiguracionPorDefecto() {
        // Configuración del servidor
        propiedades.setProperty("servidor.ip", DEFAULT_SERVER_IP);
        propiedades.setProperty("servidor.puerto", String.valueOf(DEFAULT_SERVER_PORT));
        propiedades.setProperty("servidor.maxSalones", String.valueOf(DEFAULT_MAX_SALONES));
        propiedades.setProperty("servidor.maxLabs", String.valueOf(DEFAULT_MAX_LABS));
        propiedades.setProperty("servidor.threadPoolSize", String.valueOf(DEFAULT_THREAD_POOL_SIZE));
        propiedades.setProperty("servidor.replicaHabilitada", "false");
        propiedades.setProperty("servidor.heartbeatInterval", "10000");
        
        // Configuración del cliente
        propiedades.setProperty("cliente.servidorIp", DEFAULT_CLIENT_SERVER_IP);
        propiedades.setProperty("cliente.servidorPuerto", String.valueOf(DEFAULT_CLIENT_SERVER_PORT));
        propiedades.setProperty("cliente.timeoutMs", String.valueOf(DEFAULT_TIMEOUT_MS));
        propiedades.setProperty("cliente.reintentos", String.valueOf(DEFAULT_RETRY_ATTEMPTS));
        
        System.out.println("[CONFIG] Configuración por defecto cargada");
    }
    
    // ============ GETTERS PARA SERVIDOR ============
    
    public String getServidorIp() {
        return propiedades.getProperty("servidor.ip", DEFAULT_SERVER_IP);
    }
    
    public int getServidorPuerto() {
        return Integer.parseInt(propiedades.getProperty("servidor.puerto", String.valueOf(DEFAULT_SERVER_PORT)));
    }
    
    public int getMaxSalones() {
        return Integer.parseInt(propiedades.getProperty("servidor.maxSalones", String.valueOf(DEFAULT_MAX_SALONES)));
    }
    
    public int getMaxLabs() {
        return Integer.parseInt(propiedades.getProperty("servidor.maxLabs", String.valueOf(DEFAULT_MAX_LABS)));
    }
    
    public int getThreadPoolSize() {
        return Integer.parseInt(propiedades.getProperty("servidor.threadPoolSize", String.valueOf(DEFAULT_THREAD_POOL_SIZE)));
    }
    
    public boolean isReplicaHabilitada() {
        return Boolean.parseBoolean(propiedades.getProperty("servidor.replicaHabilitada", "false"));
    }
    
    public long getHeartbeatInterval() {
        return Long.parseLong(propiedades.getProperty("servidor.heartbeatInterval", "10000"));
    }
    
    // ============ GETTERS PARA CLIENTE ============
    
    public String getClienteServidorIp() {
        return propiedades.getProperty("cliente.servidorIp", DEFAULT_CLIENT_SERVER_IP);
    }
    
    public int getClienteServidorPuerto() {
        return Integer.parseInt(propiedades.getProperty("cliente.servidorPuerto", String.valueOf(DEFAULT_CLIENT_SERVER_PORT)));
    }
    
    public long getClienteTimeoutMs() {
        return Long.parseLong(propiedades.getProperty("cliente.timeoutMs", String.valueOf(DEFAULT_TIMEOUT_MS)));
    }
    
    public int getClienteReintentos() {
        return Integer.parseInt(propiedades.getProperty("cliente.reintentos", String.valueOf(DEFAULT_RETRY_ATTEMPTS)));
    }
    
    // ============ UTILIDADES ============
    
    /**
     * Obtiene endpoint completo del servidor
     */
    public String getEndpointServidor() {
        return String.format("tcp://%s:%d", getServidorIp(), getServidorPuerto());
    }
    
    /**
     * Obtiene endpoint para cliente
     */
    public String getEndpointCliente() {
        return String.format("tcp://%s:%d", getClienteServidorIp(), getClienteServidorPuerto());
    }
    
    /**
     * Actualiza configuración en tiempo de ejecución
     */
    public void actualizarPropiedad(String clave, String valor) {
        propiedades.setProperty(clave, valor);
        System.out.printf("[CONFIG] Propiedad actualizada: %s = %s%n", clave, valor);
    }
    
    /**
     * Obtiene una propiedad específica
     */
    public String obtenerPropiedad(String clave, String valorPorDefecto) {
        return propiedades.getProperty(clave, valorPorDefecto);
    }
    
    /**
     * Valida la configuración
     */
    public boolean validarConfiguracion() {
        try {
            // Validar puertos
            int puerto = getServidorPuerto();
            if (puerto < 1024 || puerto > 65535) {
                System.err.println("[CONFIG] Puerto del servidor inválido: " + puerto);
                return false;
            }
            
            // Validar recursos
            if (getMaxSalones() <= 0 || getMaxLabs() <= 0) {
                System.err.println("[CONFIG] Recursos inválidos: salones=" + getMaxSalones() + ", labs=" + getMaxLabs());
                return false;
            }
            
            // Validar pool de hilos
            if (getThreadPoolSize() <= 0 || getThreadPoolSize() > 100) {
                System.err.println("[CONFIG] Tamaño de pool de hilos inválido: " + getThreadPoolSize());
                return false;
            }
            
            return true;
            
        } catch (NumberFormatException e) {
            System.err.println("[CONFIG] Error de formato en configuración: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Imprime resumen de configuración
     */
    public void imprimirResumen() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                   CONFIGURACIÓN ASÍNCRONA                   ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Servidor IP: %-47s ║%n", getServidorIp());
        System.out.printf("║ Servidor Puerto: %-43d ║%n", getServidorPuerto());
        System.out.printf("║ Max Salones: %-47d ║%n", getMaxSalones());
        System.out.printf("║ Max Laboratorios: %-42d ║%n", getMaxLabs());
        System.out.printf("║ Pool de Hilos: %-45d ║%n", getThreadPoolSize());
        System.out.printf("║ Réplica Habilitada: %-40s ║%n", isReplicaHabilitada() ? "SÍ" : "NO");
        System.out.printf("║ Timeout Cliente: %-43d ms ║%n", getClienteTimeoutMs());
        System.out.printf("║ Endpoint: %-50s ║%n", getEndpointServidor());
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
    
    /**
     * Obtiene configuración como Properties para compatibilidad
     */
    public Properties obtenerPropiedades() {
        return new Properties(propiedades);
    }
}