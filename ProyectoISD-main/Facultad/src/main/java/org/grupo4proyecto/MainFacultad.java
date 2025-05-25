package org.grupo4proyecto;


import org.grupo4proyecto.entidades.Facultad;
import org.grupo4proyecto.entidades.Solicitud;
import org.grupo4proyecto.redes.ClienteFacultad;
import org.grupo4proyecto.redes.ResultadoEnvio;
import org.grupo4proyecto.repositorio.ContenedorDatos;
import org.grupo4proyecto.repositorio.RepositorioPrograma;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class MainFacultad {

    static int semestrePorDefecto = 1;

    public static void main (String[] args) {
        ContenedorDatos datos = new ContenedorDatos();

        List<Long> tiemposRespuesta = new ArrayList<>();
        int solicitudesAtendidas = 0;
        int solicitudesNoAtendidas = 0;
        boolean atendida = false;


        if (interpreteArgumentos (args, datos)) {

            if (datos.solicitudes.isEmpty()) {
                cargarSolicitudesEmergencia(datos);
            }

            Facultad facultad = datos.facultad;
            List<Solicitud> solicitudes = datos.solicitudes;
            ResultadoEnvio res = null;

            System.out.println (facultad.toString());
            System.out.println (solicitudes.toString());

            Scanner scanner = new Scanner(System.in);


            try (ClienteFacultad clienteFacultad = new ClienteFacultad(datos.facultad)) {

                for (int i = 0; i < solicitudes.size(); i++) {
                    long inicio = System.nanoTime();
                    res = clienteFacultad.enviarSolicitudServidor(solicitudes.get(i));
                    long fin = System.nanoTime();
                    long duracion = fin - inicio;
                    tiemposRespuesta.add(duracion);

                    if (res.getInfoGeneral().equals("[ALERTA] No hay suficientes aulas o laboratorios para responder a la demanda")) {
                        System.out.println(res.getInfoGeneral());
                        solicitudesNoAtendidas++;
                        return;
                    }

                    System.out.println(res + "\n Ingresa: Si o No");
                    System.out.print(">> ");
                    String opcion = scanner.nextLine();

                    if (opcion.trim().toLowerCase().equals("si")) {
                        clienteFacultad.confirmarAsignacion(solicitudes.get(i), res, true);
                        facultad.getProgramas().get(i).setNumLabs(res.getSalonesAsignados());
                        facultad.getProgramas().get(i).setNumLabs(res.getLabsAsignados());
                    } else if (opcion.trim().toLowerCase().equals("no")) {
                        clienteFacultad.confirmarAsignacion(solicitudes.get(i), res, false);
                    } else {
                        clienteFacultad.confirmarAsignacion(solicitudes.get(i), res, false);
                        System.out.println("Ingrese una opcion valida");
                    }
                    solicitudesAtendidas++;
                }
            }

            if (!tiemposRespuesta.isEmpty()) {
                long min = Collections.min(tiemposRespuesta);
                long max = Collections.max(tiemposRespuesta);
                double promedio = tiemposRespuesta.stream().mapToLong(Long::longValue).average().orElse(0.0);

                System.out.println("\n--- MÉTRICAS DE DESEMPEÑO ---");
                System.out.println("Solicitudes atendidas: " + solicitudesAtendidas);
                System.out.println("Solicitudes no atendidas: " + solicitudesNoAtendidas);
                System.out.printf("Tiempo mínimo de respuesta: %.2f ms%n", min / 1_000_000.0);
                System.out.printf("Tiempo máximo de respuesta: %.2f ms%n", max / 1_000_000.0);
                System.out.printf("Tiempo promedio de respuesta: %.2f ms%n", promedio / 1_000_000.0);
            } else {
                System.out.println("\nNo se registraron tiempos de respuesta.");
            }

        }
    }

    public static boolean interpreteArgumentos(String[] args, ContenedorDatos datos) {
        // formato de argumentos:
        // [0] Nombre facultad
        // [1] IP servidor
        // [2] Puerto
        // [3] Semestre (opcional)
        // [4] Archivo programas (opcional)

        if (args.length >= 3) {
            try {
                datos.facultad.setNombre(args[0]);
                datos.facultad.setDirServidorCentral(InetAddress.getByName(args[1]));
                datos.facultad.setPuertoServidorCentral(Integer.parseInt(args[2]));

                int semestre = args.length >= 4 ? Integer.parseInt(args[3]) : 1;

                if (args.length >= 5) {
                    RepositorioPrograma.inicializarCliente(datos, args[4], semestre);
                } else {
                    cargarProgramasPorDefecto(datos, semestre);
                }
                return true;

            } catch (Exception e) {
                System.err.println("Error en argumentos: " + e.getMessage());
                mostrarAyuda();
                return false;
            }
        } else if (args.length == 0) {
            // Valores por defecto
            datos.facultad.setNombre("Facultad de Ingeniería");
            cargarConfiguracionServidor(datos.facultad);
            cargarProgramasPorDefecto(datos, semestrePorDefecto);
            return true;
        }
        mostrarAyuda();
        return false;
    }

    /**
     * Muestra las instrucciones de uso del programa
     */
    private static void mostrarAyuda() {
        String ayuda = """
        ==================================================================
        SISTEMA DE GESTIÓN DE RECURSOS PARA FACULTADES - USO DEL PROGRAMA
        ==================================================================
        
        Modo de uso:
        1. Sin parámetros (valores por defecto):
           java -jar Facultad.jar
           * Usará:
             - Nombre facultad: 'Facultad de Ingeniería'
             - IP servidor: localhost
             - Puerto: 5555
             - Semestre: 1
             - Programas: programaDefecto.txt
        
        2. Con parámetros personalizados:
           java -jar Facultad.jar <nombre> <ip> <puerto> [semestre] [archivo_programas]
           
           Ejemplo completo:
           java -jar Facultad.jar "Facultad de Ciencias" 192.168.1.100 5555 2 misProgramas.txt
        
        3. Parámetros mínimos requeridos:
           java -jar Facultad.jar <nombre> <ip> <puerto>
           
           Ejemplo:
           java -jar Facultad.jar "Facultad de Medicina" 127.0.0.1 5556
        
        ==================================================================
        ARCHIVOS DE CONFIGURACIÓN:
        - configCliente.properties: Contiene IP/puerto por defecto
        - programaDefecto.txt: Listado de programas con formato:
          Nombre Programa,salones,laboratorios
        ==================================================================
        """;

        System.out.println(ayuda);
    }

    private static void cargarProgramasPorDefecto(ContenedorDatos datos, int semestre) {
        try (InputStream input = MainFacultad.class.getResourceAsStream("/programasDefecto.txt")) {
            if (input == null) throw new IOException("Archivo no encontrado en recursos");
            RepositorioPrograma.inicializarCliente(datos, input, semestre);
        } catch (Exception e) {
            System.err.println("Error cargando programas: " + e.getMessage());
            cargarSolicitudesEmergencia(datos);
        }
    }

    private static void cargarSolicitudesEmergencia(ContenedorDatos datos) {
        datos.solicitudes.add(new Solicitud(
                datos.facultad.getNombre(),
                "Programa de Emergencia",
                semestrePorDefecto,
                5,
                5
        ));
    }

    public static void cargarConfiguracionServidor(Facultad facultad) {

        try (InputStream input = new FileInputStream("src/main/resources/configCliente.properties")) {

            Properties prop = new Properties();
            // Cargar archivo de propiedades
            prop.load(input);

            // Obtener y validar dirección IP
            String ip = prop.getProperty("server.ip", "localhost");
            try {
                InetAddress direccion = InetAddress.getByName(ip);
                facultad.setDirServidorCentral(direccion);
            } catch (UnknownHostException e) {
                System.err.println("Dirección IP inválida en configuración, usando localhost");
                facultad.setDirServidorCentral(InetAddress.getLoopbackAddress());
            }

            // Obtener y validar puerto
            String puerto = prop.getProperty("server.port", "5555");
            try {
                facultad.setPuertoServidorCentral(Integer.parseInt(puerto));
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido en configuración, usando 5555");
                facultad.setPuertoServidorCentral(5555);
            }

        } catch (IOException e) {
            System.err.println("No se encontró configCliente.properties, usando valores por defecto");
            // Valores por defecto
            facultad.setDirServidorCentral(InetAddress.getLoopbackAddress());
            facultad.setPuertoServidorCentral(5555);
        }
    }
}