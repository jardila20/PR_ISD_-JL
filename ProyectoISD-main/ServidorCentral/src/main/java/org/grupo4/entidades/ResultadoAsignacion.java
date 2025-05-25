package org.grupo4.entidades;
/*
    garantiza que una vez creados, no cambien, esto
    previene efectos secundarios no deseados
    y hace el código más predecible
 */
public record ResultadoAsignacion(
        int labsAsignados,
        int aulaMovilAsignadas,
        int salonesAsignados
) {
    // Inmutabilidad garantizada por el record
    // Métodos autogenerados: toString(), equals(), hashCode()
    public boolean esExitoso() {
        return labsAsignados > 0 || aulaMovilAsignadas > 0 || salonesAsignados > 0;
    }
}
