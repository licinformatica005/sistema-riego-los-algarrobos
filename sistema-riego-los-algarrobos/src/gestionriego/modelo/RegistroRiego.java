package gestionriego.modelo;

import java.time.LocalDateTime;

/** Registro de un ciclo de riego (tabla registros_riego). Mientras el riego está en curso, fechaFin es null. */
public class RegistroRiego {

    public static final String AUTOMATICO = "Automático";
    public static final String MANUAL = "Manual";

    private final int registroId;
    private final LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private double volumenLitros;
    private final String tipoActivacion;

    public RegistroRiego(int registroId, LocalDateTime fechaInicio, String tipoActivacion) {
        this.registroId = registroId;
        this.fechaInicio = fechaInicio;
        this.tipoActivacion = tipoActivacion;
    }

    public void cerrar(LocalDateTime fechaFin, double volumenLitros) {
        this.fechaFin = fechaFin;
        this.volumenLitros = volumenLitros;
    }

    public boolean estaEnCurso() { return fechaFin == null; }

    public int getRegistroId() { return registroId; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public LocalDateTime getFechaFin() { return fechaFin; }
    public double getVolumenLitros() { return volumenLitros; }
    public String getTipoActivacion() { return tipoActivacion; }
}
