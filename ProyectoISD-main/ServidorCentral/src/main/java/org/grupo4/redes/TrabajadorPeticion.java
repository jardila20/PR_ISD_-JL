package org.grupo4.redes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.grupo4.entidades.AdministradorInstalaciones;
import org.grupo4.entidades.ResultadoAsignacion;
import org.grupo4.entidades.Solicitud;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.Arrays;

public class TrabajadorPeticion extends Thread{
    private final String id;
    private final ZContext contexto;
    private final ZMQ.Socket trabajador;
    private final ObjectMapper json = new ObjectMapper();

    public TrabajadorPeticion(ZContext contexto, String id) {
        this.id = id;
        this.contexto = contexto;
        this.trabajador = contexto.createSocket(SocketType.DEALER);
        this.trabajador.setIdentity(id.getBytes(ZMQ.CHARSET));
        this.trabajador.connect("inproc://backend");
        System.out.println("[TRABAJADOR " + id + "] Conectado al broker");
    }


    @Override
    public void run() {
        // 1) Indicar al broker que estoy listo
        System.out.println("[TRABAJADOR " + id + "] Enviando señal READY");
        trabajador.sendMore("");
        trabajador.send("READY");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 2) Recibir el mensaje multipart del broker:
                // Formato: [empty][clientAddr][empty][request]
                trabajador.recv();
                String dirCliente = trabajador.recvStr();
                trabajador.recv();
                String peticion = trabajador.recvStr();

                System.out.println("[TRABAJADOR " + id + "] Procesando solicitud de "
                        + dirCliente + ": " + peticion);

                // 3) Procesar la solicitud
                String reqJson = peticion;
                Solicitud solicitud = json.readValue(reqJson, Solicitud.class);
                String resJson = procesarSolicitud(solicitud);

                System.out.println("[TRABAJADOR " + id + "] Enviando respuesta a " + dirCliente);

                // 4) Enviar respuesta al broker:
                // Formato: [empty][clientAddr][empty][response]
                trabajador.sendMore("");
                trabajador.sendMore(dirCliente);
                trabajador.sendMore("");
                trabajador.send(resJson);

            } catch (ZMQException e) {
                if (e.getErrorCode() != ZMQ.Error.ETERM.getCode()) {
                    System.out.println("[TRABAJADOR " + id + "] Error: " + e.getMessage());
                }
            } catch (JsonProcessingException e) {
                System.out.println("[TRABAJADOR " + id + "] Error: " + e.getMessage());
            }
        }
    }

    public String obtenerInfoGeneral (ResultadoAsignacion resultado, Solicitud solicitud) {
        /*
         * Estructura Repuesta
         * Trama 1: Informacion general
         * Trama 2: Laboratorios asignados
         * Trama 3: Aulas moviles
         * Trama 4: Salones asignados
         */

        String infoGeneral = "[ALERTA] No hay suficientes aulas o laboratorios para responder a la demanda";
        //Caso 1
        if (resultado.esExitoso()) {
            if(resultado.aulaMovilAsignadas() == 0) {
                infoGeneral = String.format("Asignacion exitosa de laboratorios y salones para %s", solicitud.getPrograma());
            } else {
                infoGeneral = String.format("Asignacion exitosa para %s, algunos laboratorios se asignaron como aulas moviles", solicitud.getPrograma());
            }
        }

        return infoGeneral;
    }

    public String procesarSolicitud(Solicitud peticion) {
        try {
            ResultadoAsignacion resultado = AdministradorInstalaciones.getInstance().asignar(
                    peticion.getNumSalones(),
                    peticion.getNumLaboratorios());

            ResultadoEnvio resEnvio = new ResultadoEnvio(
                    obtenerInfoGeneral(resultado, peticion),
                    resultado.labsAsignados(),
                    resultado.aulaMovilAsignadas(),
                    resultado.salonesAsignados());

            return json.writeValueAsString(resEnvio);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
