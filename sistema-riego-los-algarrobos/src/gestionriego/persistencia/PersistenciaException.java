package gestionriego.persistencia;

import gestionriego.modelo.RiegoException;

/**
 * Error de persistencia: envuelve a SQLException (o a una falla de configuración) para que las capas
 * superiores no dependan de java.sql. Conserva la excepción original como causa.
 */
public class PersistenciaException extends RiegoException {

    public PersistenciaException(String mensaje) {
        super(mensaje);
    }

    public PersistenciaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
