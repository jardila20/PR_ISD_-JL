package org.grupo4proyecto.repositorio;

import org.grupo4proyecto.entidades.Programa;
import org.grupo4proyecto.entidades.Solicitud;
import java.io.*;

public class RepositorioPrograma {
    public static void inicializarCliente(ContenedorDatos datos, String archivo, int semestre) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split("\\s*,\\s*");
                if (partes.length == 3) {
                    datos.solicitudes.add(crearSolicitud(datos, partes, semestre));
                }
            }
        }
    }

    public static void inicializarCliente(ContenedorDatos datos, InputStream input, int semestre) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split("\\s*,\\s*");
                if (partes.length == 3) {
                    datos.solicitudes.add(crearSolicitud(datos, partes, semestre));
                }
            }
        }
    }

    private static Solicitud crearSolicitud(ContenedorDatos datos, String[] partes, int semestre) {
        datos.facultad.getProgramas().add(new Programa(partes[0].trim()));
        return new Solicitud(
                datos.facultad.getNombre(),
                partes[0].trim(),
                semestre,
                Integer.parseInt(partes[1].trim()),
                Integer.parseInt(partes[2].trim())
        );
    }
}