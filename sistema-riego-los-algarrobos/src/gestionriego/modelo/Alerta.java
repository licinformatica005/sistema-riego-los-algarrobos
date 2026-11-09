package gestionriego.modelo;

import java.time.LocalDateTime;

/** Alerta generada por el sistema (tabla alertas). */
public class Alerta {

    public static final String HUMEDAD_CRITICA = "Humedad crítica";
    public static final String FALLA_COMUNICACION = "Falla de comunicación";

    private final int alertaId;
    private final Sector sector;
    private final LocalDateTime fecha;
    private final String tipo;
    private final String descripcion;
    private boolean atendida;

    public Alerta(int alertaId, Sector sector, LocalDateTime fecha, String tipo, String descripcion, boolean atendida) {
        this.alertaId = alertaId;
        this.sector = sector;
        this.fecha = fecha;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.atendida = atendida;
    }

    public void marcarAtendida() { atendida = true; }

    public int getAlertaId() { return alertaId; }
    public Sector getSector() { return sector; }
    public LocalDateTime getFecha() { return fecha; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public boolean isAtendida() { return atendida; }
}
