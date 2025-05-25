package org.grupo4proyecto.repositorio;

import org.grupo4proyecto.entidades.Facultad;
import org.grupo4proyecto.entidades.Solicitud;

import java.util.ArrayList;
import java.util.List;

// Hacer comportamiento de paso por referencia
public class ContenedorDatos {
    public Facultad facultad;
    public List<Solicitud> solicitudes;

    public ContenedorDatos() {
        facultad = new Facultad();
        solicitudes = new ArrayList<> ();
    }
}
