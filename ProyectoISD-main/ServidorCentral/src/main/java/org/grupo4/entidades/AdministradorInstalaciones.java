package org.grupo4.entidades;

import org.grupo4.concurrencia.ContadorAtomico;
import org.grupo4.redes.ResultadoEnvio;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.grupo4.repositorio.Configuracion.cargarConfiguracionServidor;

public class AdministradorInstalaciones {
    private static volatile AdministradorInstalaciones singleton;
    private final ContadorAtomico salones;
    private final ContadorAtomico labs;
    private final ContadorAtomico aulasMoviles;

    // Valores maximos parametrizables
    public AdministradorInstalaciones() {
        List<String> valores = cargarConfiguracionServidor(null);

        int maxSalones = Integer.parseInt(valores.get(0));
        int maxLabs = Integer.parseInt(valores.get(1));

        this.salones = new ContadorAtomico(maxSalones);
        this.labs = new ContadorAtomico(maxLabs);
        this.aulasMoviles = new ContadorAtomico(0);
    }

    public AdministradorInstalaciones(int salones, int labs) {
        this.salones = new ContadorAtomico(salones);
        this.labs = new ContadorAtomico(labs);
        this.aulasMoviles = new ContadorAtomico(0);
    }


    public static AdministradorInstalaciones getInstance() {
        if (singleton == null) {
            synchronized(AdministradorInstalaciones.class) {
                if(singleton == null) {
                    singleton = new AdministradorInstalaciones();
                }
            }
        }
        return singleton;
    }

    public static AdministradorInstalaciones getInstance(int salones, int labs) {
        if (singleton == null) {
            synchronized(AdministradorInstalaciones.class) {
                if(singleton == null) {
                    singleton = new AdministradorInstalaciones(salones, labs);
                }
            }
        }
        return singleton;
    }

    public ResultadoAsignacion asignar(int salonesNecesitados, int labsNecesitados) {
        synchronized(this) {
            // Caso 1: Hay suficientes salones y labs disponibles
            if (labs.get() >= labsNecesitados && salones.get() >= salonesNecesitados) {
                labs.decrementar(labsNecesitados);
                salones.decrementar(salonesNecesitados);
                return new ResultadoAsignacion(labsNecesitados, 0, salonesNecesitados);
            }

            // Caso 2: Faltan labs pero podemos convertir salones en aulas móviles
            int labsDisponibles = labs.get();
            int labsFaltantes = labsNecesitados - labsDisponibles;
            int salonesRequeridos = salonesNecesitados + labsFaltantes;

            if (salones.get() >= salonesRequeridos) {
                labs.decrementar(labsDisponibles);
                salones.decrementar(salonesRequeridos);
                aulasMoviles.incrementar(labsFaltantes);

                return new ResultadoAsignacion(labsDisponibles, salonesNecesitados, labsFaltantes);
            }

            // Caso 3: No hay recursos suficientes
            return new ResultadoAsignacion(0, 0, 0);
        }
    }

    public boolean devolverRecursos(ResultadoEnvio asignacion) {
        // 1. Calcular valores futuros
        int labsFuturos = labs.get() + asignacion.getLabsAsignados();
        int salonesFuturos = salones.get() + asignacion.getSalonesAsignados() + asignacion.getAulaMovilAsignadas();
        int aulasMovilesFuturas = aulasMoviles.get() - asignacion.getAulaMovilAsignadas();

        // 2. Validar integridad
        boolean operacionValida =
                labsFuturos >= 0 &&
                        salonesFuturos >= 0 &&
                        aulasMovilesFuturas >= 0 &&
                        asignacion.getAulaMovilAsignadas() <= aulasMoviles.get();

        if (!operacionValida) {
            return false;
        }

        // 3. Aplicar cambios con incrementar/decrementar (sin usar set)
        labs.incrementar(asignacion.getLabsAsignados());
        salones.incrementar(asignacion.getSalonesAsignados() + asignacion.getAulaMovilAsignadas());

        if (asignacion.getAulaMovilAsignadas() > 0) {
            aulasMoviles.decrementar(asignacion.getAulaMovilAsignadas());
        }

        return true;
    }

    // Método para obtener estadísticas actuales
    public String getEstadisticas() {
        return String.format("Salones disponibles: %d, Laboratorios disponibles: %d, Aulas móviles: %d",
                salones.get(), labs.get(), aulasMoviles.get());
    }

}
