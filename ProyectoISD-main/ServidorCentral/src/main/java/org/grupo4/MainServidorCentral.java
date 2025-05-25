package org.grupo4;

import org.grupo4.redes.ServidorCentral;
import java.io.InputStream;

public class MainServidorCentral {
    // Valores por defecto
    private static final int DEFAULT_MAX_SALONES = 380;
    private static final int DEFAULT_MAX_LABS = 60;
    private static final String DEFAULT_IP = "0.0.0.0";
    private static final String DEFAULT_PORT = "5555";
    private static final String DEFAULT_INPROC = "backend";

    public static void main(String[] args) {
        ServidorCentral servidor;


        servidor = crearServidorConConfigPorDefecto();


        servidor.loadBalancingBroker();

        //Manejar interrupcion
        Runtime.getRuntime().addShutdownHook(
            new Thread(
                () -> {
                    servidor.imprimirMetricas();
                }
            )
        );
    }

    private static ServidorCentral crearServidorConConfigPorDefecto() {
        try {
            // Intentar cargar config.properties desde recursos
            InputStream input = MainServidorCentral.class.getResourceAsStream("/configServidor.properties");
            if (input != null) {
                return new ServidorCentral("configServidor.properties");
            }
        } catch (Exception e) {
            System.err.println("Error cargando config predeterminada: " + e.getMessage());
        }

        // Valores quemados en código
        System.out.println("Usando configuración por defecto:");
        System.out.println("Max salones: " + DEFAULT_MAX_SALONES);
        System.out.println("Max laboratorios: " + DEFAULT_MAX_LABS);
        System.out.println("IP: " + DEFAULT_IP);
        System.out.println("Puerto: " + DEFAULT_PORT);

        return new ServidorCentral(
                DEFAULT_IP,
                DEFAULT_PORT,
                DEFAULT_INPROC,
                DEFAULT_MAX_SALONES,
                DEFAULT_MAX_LABS
        );
    }
}