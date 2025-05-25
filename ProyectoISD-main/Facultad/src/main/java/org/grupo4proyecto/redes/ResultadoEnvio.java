package org.grupo4proyecto.redes;

public class ResultadoEnvio {
    private String infoGeneral;
    private int labsAsignados;
    private int aulaMovilAsignadas;
    private int salonesAsignados;

    public ResultadoEnvio(String infoGeneral, int labsAsignados, int aulaMovilAsignadas, int salonesAsignados) {
        this.infoGeneral = infoGeneral;
        this.labsAsignados = labsAsignados;
        this.aulaMovilAsignadas = aulaMovilAsignadas;
        this.salonesAsignados = salonesAsignados;
    }

    public ResultadoEnvio() {}

    public String getInfoGeneral() {
        return infoGeneral;
    }

    public void setInfoGeneral(String infoGeneral) {
        this.infoGeneral = infoGeneral;
    }

    public int getLabsAsignados() {
        return labsAsignados;
    }

    public void setLabsAsignados(int labsAsignados) {
        this.labsAsignados = labsAsignados;
    }

    public int getAulaMovilAsignadas() {
        return aulaMovilAsignadas;
    }

    public void setAulaMovilAsignadas(int aulaMovilAsignadas) {
        this.aulaMovilAsignadas = aulaMovilAsignadas;
    }

    public int getSalonesAsignados() {
        return salonesAsignados;
    }

    public void setSalonesAsignados(int salonesAsignados) {
        this.salonesAsignados = salonesAsignados;
    }

    @Override
    public String toString() {
        return "==== RESULTADO ENVIO ====\n" +
                "Informacion General = " + infoGeneral + "\n" +
                "Laboratorios Asignados = " + labsAsignados + "\n" +
                "Aulas Moviles Asignadas = " + aulaMovilAsignadas + "\n" +
                "Salones Asignados = " + salonesAsignados + "\n" +
                "======================";
    }
}