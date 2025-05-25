package org.grupo4proyecto.entidades;

public class Programa {
    private String nombre;
    private int numSalones;
    private int numLabs;

    public Programa (String nombre) {
        this.nombre = nombre;
    }

    public String getNombre () {
        return nombre;
    }

    public void setNombre (String nombre) {
        this.nombre = nombre;
    }

    public int getNumSalones () {
        return numSalones;
    }

    public void setNumSalones (int numSalones) {
        this.numSalones = numSalones;
    }

    public int getNumLabs () {
        return numLabs;
    }

    public void setNumLabs (int numLabs) {
        this.numLabs = numLabs;
    }

    @Override
    public String toString () {
        return "Programa{" +
                "nombre='" + nombre + '\'' +
                ", numSalones=" + numSalones +
                ", numLabs=" + numLabs +
                '}';
    }
}
