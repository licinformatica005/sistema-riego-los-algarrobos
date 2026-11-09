package gestionriego.modelo;

/** Sensor de clima. Hereda de Sensor; su lectura principal es la temperatura y además informa la precipitación. */
public class SensorClima extends Sensor {

    private double ultimaTemperatura;
    private double ultimaPrecipitacion;

    public SensorClima(int sensorId) {
        super(sensorId, TIPO_CLIMA);
    }

    /** Simula una temperatura entre 10 y 40 °C y una precipitación (la mayoría de las veces nula). */
    @Override
    public double generarLectura() {
        ultimaTemperatura = Math.round((10 + Math.random() * 30) * 10) / 10.0;
        ultimaPrecipitacion = (Math.random() < 0.8) ? 0.0 : Math.round(Math.random() * 150) / 10.0;
        return ultimaTemperatura;
    }

    @Override
    public String getUnidad() { return "°C"; }

    public double getUltimaTemperatura() { return ultimaTemperatura; }

    public double getUltimaPrecipitacion() { return ultimaPrecipitacion; }
}
