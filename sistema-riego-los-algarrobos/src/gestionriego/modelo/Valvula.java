package gestionriego.modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Electroválvula de un sector (tablas valvulas y sensores). Agrupa los sensores conectados a ella y conoce el
 * riego en curso. Los cambios de estado en memoria se aplican DESPUÉS de que la base de datos confirmó la operación.
 */
public class Valvula {

    private final int valvulaId;
    private final double caudal;                       // litros por minuto
    private boolean activa;
    private RegistroRiego registroEnCurso;
    private final ArrayList<Sensor> sensores = new ArrayList<>();   // guarda SensorHumedad y SensorClima

    public Valvula(int valvulaId, double caudal) throws RiegoException {
        if (caudal <= 0) {
            throw new RiegoException("El caudal de la válvula debe ser mayor que cero.");
        }
        this.valvulaId = valvulaId;
        this.caudal = caudal;
    }

    public void agregarSensor(Sensor sensor) {
        sensores.add(sensor);
    }

    /** Verifica que la válvula pueda abrirse. */
    public void validarActivable() throws RiegoException {
        if (activa) {
            throw new RiegoException("La válvula " + valvulaId + " ya está activa.");
        }
    }

    /** Verifica que la válvula pueda cerrarse con la duración indicada. */
    public void validarDetenible(int minutos) throws RiegoException {
        if (!activa || registroEnCurso == null) {
            throw new RiegoException("La válvula " + valvulaId + " ya está detenida.");
        }
        if (minutos <= 0) {
            throw new RiegoException("La duración del riego debe ser mayor que cero.");
        }
    }

    /** Marca la válvula como activa con el registro de riego que ya persistió el DAO. */
    public void abrir(RegistroRiego registro) {
        this.registroEnCurso = registro;
        this.activa = true;
    }

    /** Cierra el riego en curso en memoria. */
    public RegistroRiego cerrar(LocalDateTime fechaFin, double volumenLitros) {
        RegistroRiego cerrado = registroEnCurso;
        cerrado.cerrar(fechaFin, volumenLitros);
        this.registroEnCurso = null;
        this.activa = false;
        return cerrado;
    }

    public SensorHumedad getSensorHumedad() {
        for (Sensor s : sensores) {
            if (s instanceof SensorHumedad) {
                return (SensorHumedad) s;
            }
        }
        return null;
    }

    public SensorClima getSensorClima() {
        for (Sensor s : sensores) {
            if (s instanceof SensorClima) {
                return (SensorClima) s;
            }
        }
        return null;
    }

    public int getValvulaId() { return valvulaId; }
    public double getCaudal() { return caudal; }
    public boolean estaActiva() { return activa; }
    public String getEstado() { return activa ? "Activa" : "Inactiva"; }
    public RegistroRiego getRegistroEnCurso() { return registroEnCurso; }
    public ArrayList<Sensor> getSensores() { return sensores; }
}
