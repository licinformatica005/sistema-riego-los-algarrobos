package gestionriego.modelo;

/**
 * Excepción propia del dominio de riego (verificada).
 * Representa la violación de una regla del negocio: activar una válvula ya activa, ingresar una humedad
 * fuera de rango, buscar un sector inexistente, etc. Es la clase padre de PersistenciaException.
 */
public class RiegoException extends Exception {

    public RiegoException(String mensaje) {
        super(mensaje);
    }

    public RiegoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
