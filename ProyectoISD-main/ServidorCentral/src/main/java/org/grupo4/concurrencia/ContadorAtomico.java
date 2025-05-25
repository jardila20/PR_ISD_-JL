package org.grupo4.concurrencia;

import java.util.concurrent.atomic.AtomicInteger;

// Mantener contadores numéricos que pueden ser modificados de forma segura
// por varios hilos.
public class ContadorAtomico {
    // Usa AtomicInteger para garantizar que las operaciones sean indivisibles
    private final AtomicInteger valor;

    public ContadorAtomico(int valorInicial) {
        this.valor = new AtomicInteger(valorInicial);
    }

    /*
    El ciclo permite reintentar la operación si compareAndSet falla,
    lo que ocurre cuando otro hilo modificó value entre la lectura (get())
    y la escritura (compareAndSet()).
     */
    public boolean decrementar(int cantidad) {
        while(true) {
            int valorActual = valor.get();
            if (valorActual < cantidad)
                return false;
            if(valor.compareAndSet(valorActual, valorActual - cantidad)) {
                return true;
            }
        }
    }

    public boolean incrementar(int cantidad) {
        while (true) {
            int valorActual = valor.get();
            if(valor.compareAndSet(valorActual, valorActual + cantidad)) {
                return true;
            }
        }
    }

    public int get() {
        return valor.get();
    }
}
