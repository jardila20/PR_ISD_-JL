package org.grupo4.repositorio;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Configuracion {
    /**
     * Carga las propiedades del servidor y devuelve una lista de strings:
     *   server.maxSalones = <valor>
     *   server.maxLabs = <valor>
     *   server.ip = <valor>
     *   server.port = <valor>
     */
    public static List<String> cargarConfiguracionServidor(String rutaConfig) {
        // Valores por defecto
        int maxSalones = 380;
        int maxLabs = 60;
        String ip = "0.0.0.0";
        String port = "5555";
        String inproc = "backend";

        // Corregir ruta por defecto
        if (rutaConfig == null) {
            rutaConfig = "configServidor.properties"; // Ruta desde classpath
        }

        try (InputStream input = Configuracion.class.getClassLoader()
                .getResourceAsStream(rutaConfig)) {

            if (input != null) { // Evitar NPE si el archivo no existe
                Properties prop = new Properties();
                prop.load(input);

                maxSalones = Integer.parseInt(prop.getProperty("server.maxSalones", "380"));
                maxLabs = Integer.parseInt(prop.getProperty("server.maxLabs", "60"));
                ip = prop.getProperty("server.ip", "0.0.0.0");
                port = prop.getProperty("server.port", "5555");
                inproc = prop.getProperty("server.inproc", "backend");
            } else {
                System.err.println("Archivo de configuración no encontrado. Usando valores por defecto.");
            }

        } catch (Exception e) {
            System.err.println("Error cargando configuración. Usando valores por defecto. Detalle: " + e.getMessage());
        }

        List<String> valores = new ArrayList<>();
        valores.add(String.valueOf(maxSalones));
        valores.add(String.valueOf(maxLabs));
        valores.add(ip);
        valores.add(port);

        return valores;
    }
}
