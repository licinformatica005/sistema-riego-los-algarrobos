package gestionriego.modelo;

/**
 * Umbrales de humedad de un sector (tabla umbrales). Es una entidad: guarda sus datos y valida que sean coherentes.
 * La regla de decisión (activar, mantener o detener) está en GestorRiego, no acá.
 */
public class Umbral {

    private final double humedadMinima;
    private final double humedadMaxima;

    public Umbral(double humedadMinima, double humedadMaxima) throws RiegoException {
        validar(humedadMinima, humedadMaxima);
        this.humedadMinima = humedadMinima;
        this.humedadMaxima = humedadMaxima;
    }

    private static void validar(double min, double max) throws RiegoException {
        if (min < 0 || max > 100) {
            throw new RiegoException("Los umbrales deben estar entre 0 y 100 %.");
        }
        if (min >= max) {
            throw new RiegoException("El umbral mínimo debe ser menor que el máximo.");
        }
    }

    public double getHumedadMinima() { return humedadMinima; }

    public double getHumedadMaxima() { return humedadMaxima; }
}
