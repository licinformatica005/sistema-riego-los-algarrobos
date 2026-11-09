package gestionriego.modelo;

import java.time.LocalDateTime;

/** Sensor de humedad de suelo. Hereda de Sensor y sobrescribe generarLectura(). */
public class SensorHumedad extends Sensor {

    private Double ultimaLectura;               // null mientras no haya recibido ninguna lectura
    private LocalDateTime fechaUltimaLectura;

    public SensorHumedad(int sensorId) {
        super(sensorId, TIPO_HUMEDAD);
    }

    /** Guarda en memoria la última lectura (la persistencia la realiza el DAO). */
    public void registrarLectura(double humedad, LocalDateTime fecha) {
        this.ultimaLectura = humedad;
        this.fechaUltimaLectura = fecha;
    }

    /** Simula una medición de humedad entre 0 y 100 %. No guarda nada: solo genera el valor. */
    @Override
    public double generarLectura() {
        return Math.round(Math.random() * 1000) / 10.0;
    }

    @Override
    public String getUnidad() { return "%"; }

    public Double getUltimaLectura() { return ultimaLectura; }

    public LocalDateTime getFechaUltimaLectura() { return fechaUltimaLectura; }
}
