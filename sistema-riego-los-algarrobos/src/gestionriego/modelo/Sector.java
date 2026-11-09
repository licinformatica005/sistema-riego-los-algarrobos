package gestionriego.modelo;

import java.time.LocalDateTime;

/** Sector de cultivo (tabla sectores). Se vincula de forma exclusiva con una Valvula y un Umbral. */
public class Sector {

    private final int sectorId;
    private final String nombre;
    private final double superficie;     // hectáreas
    private final String tipoCultivo;
    private final Valvula valvula;
    private final Umbral umbral;

    public Sector(int sectorId, String nombre, double superficie, String tipoCultivo,
                  Valvula valvula, Umbral umbral) throws RiegoException {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new RiegoException("El nombre del sector no puede estar vacío.");
        }
        if (superficie <= 0) {
            throw new RiegoException("La superficie debe ser mayor que cero.");
        }
        this.sectorId = sectorId;
        this.nombre = nombre.trim();
        this.superficie = superficie;
        this.tipoCultivo = (tipoCultivo == null) ? "" : tipoCultivo.trim();
        this.valvula = valvula;
        this.umbral = umbral;
    }

    /** Humedad actual = última lectura del sensor de humedad de su válvula (null si no hay). */
    public Double getHumedadActual() {
        SensorHumedad sensor = valvula.getSensorHumedad();
        return (sensor == null) ? null : sensor.getUltimaLectura();
    }

    /** Fecha de la última lectura de humedad recibida (null si nunca llegó una). */
    public LocalDateTime getFechaUltimaLectura() {
        SensorHumedad sensor = valvula.getSensorHumedad();
        return (sensor == null) ? null : sensor.getFechaUltimaLectura();
    }

    public int getSectorId() { return sectorId; }
    public String getNombre() { return nombre; }
    public double getSuperficie() { return superficie; }
    public String getTipoCultivo() { return tipoCultivo; }
    public Valvula getValvula() { return valvula; }
    public Umbral getUmbral() { return umbral; }
}
