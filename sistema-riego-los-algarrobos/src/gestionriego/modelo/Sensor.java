package gestionriego.modelo;

/**
 * Clase ABSTRACTA que representa un sensor conectado a una válvula.
 * Define lo común (identificador y tipo) y obliga a las subclases a implementar generarLectura(),
 * porque cada tipo de sensor mide algo distinto. No se pueden crear objetos Sensor directamente.
 */
public abstract class Sensor {

    /** Valores de la columna sensores.tipo de la base de datos. */
    public static final String TIPO_HUMEDAD = "Humedad de suelo";
    public static final String TIPO_CLIMA = "Clima";

    protected int sensorId;
    protected String tipo;

    public Sensor(int sensorId, String tipo) {
        this.sensorId = sensorId;
        this.tipo = tipo;
    }

    /** Método abstracto: cada subclase lo implementa a su manera (polimorfismo). */
    public abstract double generarLectura();

    /** Unidad de medida de la lectura (%, °C). */
    public abstract String getUnidad();

    public int getSensorId() { return sensorId; }

    public String getTipo() { return tipo; }

    /** Texto que describe un valor leído por este sensor (usa el tipo y la unidad propios de cada subclase). */
    public String describir(double valor) {
        return String.format("Sensor #%d (%s): %.1f %s", sensorId, tipo, valor, getUnidad());
    }

    /** Método común que se apoya en el polimorfismo: no sabe qué subclase es. */
    public String describirLectura() {
        return describir(generarLectura());
    }
}
