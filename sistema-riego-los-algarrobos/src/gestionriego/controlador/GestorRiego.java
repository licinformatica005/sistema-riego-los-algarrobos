package gestionriego.controlador;

import gestionriego.modelo.AccionRiego;
import gestionriego.modelo.Alerta;
import gestionriego.modelo.ControladorCampo;
import gestionriego.modelo.RegistroRiego;
import gestionriego.modelo.RiegoException;
import gestionriego.modelo.Sector;
import gestionriego.modelo.Sensor;
import gestionriego.modelo.SensorClima;
import gestionriego.modelo.SensorHumedad;
import gestionriego.modelo.Umbral;
import gestionriego.modelo.Valvula;
import gestionriego.persistencia.ArchivoReporte;
import gestionriego.persistencia.PersistenciaException;
import gestionriego.persistencia.RiegoDAO;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * CONTROLADOR del patrón MVC (y clase de control del PUD). Recibe las órdenes de la Vista, aplica las reglas del
 * negocio sobre las entidades del Modelo y le pide al DAO que persista los cambios. No imprime ni lee datos
 * del usuario: devuelve resultados o lanza excepciones, y la Vista decide cómo mostrarlos.
 */
public class GestorRiego {

    /** Duración simulada del riego cuando el programa corta el riego por su cuenta (opción de lecturas simuladas). */
    public static final int MINUTOS_SIMULADOS_POR_DEFECTO = 30;
    public static final int HORAS_SIN_LECTURA_PARA_ALERTA = 2;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RiegoDAO dao;
    private final ArchivoReporte archivo;
    private final String identificador;
    private ControladorCampo controlador;

    public GestorRiego(RiegoDAO dao, ArchivoReporte archivo, String identificador) {
        this.dao = dao;
        this.archivo = archivo;
        this.identificador = identificador;
    }

    // ------------------------------------------------------------------ carga inicial

    /** Carga desde la base de datos el controlador con todos sus sectores. */
    public void iniciar() throws PersistenciaException {
        controlador = dao.cargarControlador(identificador);
    }

    public ArrayList<String> getAdvertencias() {
        return dao.getAdvertencias();
    }

    public ControladorCampo getControlador() {
        return controlador;
    }

    // ------------------------------------------------------------------ regla de decisión del riego

    /**
     *  humedad &lt; mínimo  -> ACTIVAR;  humedad &gt;= máximo -> DETENER;  en otro caso -> MANTENER.
     */
    public AccionRiego verificarUmbral(Umbral umbral, double humedad) throws RiegoException {
        if (humedad < 0 || humedad > 100) {
            throw new RiegoException("La humedad debe estar entre 0 y 100 % (se recibió " + humedad + ").");
        }
        if (humedad < umbral.getHumedadMinima()) {
            return AccionRiego.ACTIVAR;
        } else if (humedad >= umbral.getHumedadMaxima()) {
            return AccionRiego.DETENER;
        } else {
            return AccionRiego.MANTENER;
        }
    }

    /** Indica si procesar esta lectura cortaría un riego en curso (la Vista pregunta entonces la duración). */
    public boolean cortaraRiego(Sector sector, double humedad) throws RiegoException {
        return verificarUmbral(sector.getUmbral(), humedad) == AccionRiego.DETENER && sector.getValvula().estaActiva();
    }

    // ------------------------------------------------------------------ casos de uso

    /** RF01: alta de un sector (inserta en sectores, valvulas, sensores y umbrales). */
    public Sector agregarSector(String nombre, double superficie, String cultivo, double caudal,
                                double humedadMinima, double humedadMaxima) throws RiegoException {
        Sector sector = dao.insertarSector(controlador, nombre, superficie, cultivo, caudal, humedadMinima, humedadMaxima);
        controlador.agregarSector(sector);
        return sector;
    }

    /**
     * RF02, RF04 y RF05: procesa una lectura de humedad. Primero la guarda en la base de datos y luego decide
     * si activa, mantiene o detiene el riego.
     * @param minutosRiego duración simulada del riego en curso; solo se usa si hay que detenerlo
     */
    public String procesarLectura(Sector sector, double humedad, int minutosRiego) throws RiegoException {
        Umbral umbral = sector.getUmbral();
        Valvula valvula = sector.getValvula();
        AccionRiego accion = verificarUmbral(umbral, humedad);      // valida el rango antes de guardar

        SensorHumedad sensor = valvula.getSensorHumedad();
        if (sensor == null) {
            throw new RiegoException("El sector " + sector.getNombre() + " no tiene un sensor de humedad.");
        }
        LocalDateTime ahora = LocalDateTime.now();
        dao.registrarLecturaHumedad(sensor, humedad, ahora);        // INSERT en registros_humedad
        sensor.registrarLectura(humedad, ahora);

        switch (accion) {
            case ACTIVAR:
                Alerta alerta = dao.registrarAlerta(sector, Alerta.HUMEDAD_CRITICA, String.format(
                        "Humedad %.1f %% por debajo del umbral mínimo (%.1f %%).", humedad, umbral.getHumedadMinima()), ahora);
                controlador.agregarAlerta(alerta);
                if (valvula.estaActiva()) {
                    return "Humedad baja: la válvula ya estaba activa, el riego continúa. Se registró una alerta.";
                }
                iniciarRiego(sector, RegistroRiego.AUTOMATICO);
                return "Humedad por debajo del mínimo: se ACTIVÓ el riego automático. Se generó una alerta.";
            case DETENER:
                if (valvula.estaActiva()) {
                    RegistroRiego registro = finalizarRiego(sector, minutosRiego);
                    return String.format("Humedad en el máximo: se DETUVO el riego. Volumen suministrado: %.1f L.",
                            registro.getVolumenLitros());
                }
                return "Humedad en el máximo o por encima: no hay riego en curso, no se hace nada.";
            default:
                return "Humedad dentro del rango configurado: se MANTIENE el estado actual de la válvula.";
        }
    }

    /**
     * Simula la llegada de lecturas desde el campo. Recorre TODOS los sensores de la válvula sin distinguir el tipo:
     * cada uno responde con su propia generarLectura() (polimorfismo). La humedad se procesa con el riego automático.
     */
    public ArrayList<String> recibirLecturas(Sector sector) throws RiegoException {
        ArrayList<String> mensajes = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();
        Double humedad = null;
        for (Sensor sensor : sector.getValvula().getSensores()) {
            double valor = sensor.generarLectura();
            mensajes.add(sensor.describir(valor));
            if (sensor instanceof SensorHumedad) {
                humedad = valor;
            } else if (sensor instanceof SensorClima) {
                SensorClima clima = (SensorClima) sensor;
                dao.registrarLecturaClima(clima, valor, clima.getUltimaPrecipitacion(), ahora);
            }
        }
        if (humedad != null) {
            mensajes.add(procesarLectura(sector, humedad, MINUTOS_SIMULADOS_POR_DEFECTO));
        }
        return mensajes;
    }

    /** RF03: activación manual. */
    public void activarManual(Sector sector) throws RiegoException {
        iniciarRiego(sector, RegistroRiego.MANUAL);
    }

    /** RF03: corte manual. Devuelve el registro con el volumen suministrado. */
    public RegistroRiego detenerManual(Sector sector, int minutos) throws RiegoException {
        return finalizarRiego(sector, minutos);
    }

    private void iniciarRiego(Sector sector, String tipo) throws RiegoException {
        Valvula valvula = sector.getValvula();
        valvula.validarActivable();
        RegistroRiego registro = dao.iniciarRiego(valvula, tipo, LocalDateTime.now());   // INSERT + UPDATE
        valvula.abrir(registro);                                                           // memoria, tras confirmar la BD
    }

    private RegistroRiego finalizarRiego(Sector sector, int minutos) throws RiegoException {
        Valvula valvula = sector.getValvula();
        valvula.validarDetenible(minutos);
        RegistroRiego registro = valvula.getRegistroEnCurso();
        LocalDateTime fin = registro.getFechaInicio().plusMinutes(minutos);
        double volumen = valvula.getCaudal() * minutos;
        dao.finalizarRiego(controlador, valvula, registro, fin, volumen);                  // 3 UPDATE en una transacción
        valvula.cerrar(fin, volumen);
        controlador.registrarConsumo(volumen);
        return registro;
    }

    // ------------------------------------------------------------------ consultas y reportes

    public ArrayList<Sector> sectoresOrdenadosPorHumedad() {
        return controlador.sectoresOrdenadosPorHumedad();
    }

    public Sector buscarSector(int id) throws RiegoException {
        return controlador.buscarSectorPorId(id);
    }

    /** RF07: consumo por sector y tipo de activación en un período (fechas inclusivas; null = sin límite). */
    public double[][] consumo(LocalDate desde, LocalDate hasta) throws RiegoException {
        LocalDateTime inicio = (desde == null) ? LocalDate.of(1970, 1, 1).atStartOfDay() : desde.atStartOfDay();
        LocalDateTime fin = (hasta == null) ? LocalDate.of(2100, 1, 1).atStartOfDay() : hasta.plusDays(1).atStartOfDay();
        if (!inicio.isBefore(fin)) {
            throw new RiegoException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
        return dao.consumoPorSector(controlador, inicio, fin);
    }

    /**
     * Estadísticas de las últimas lecturas de humedad de un sector. Las lecturas llegan en un ArrayList desde la base
     * de datos y se pasan a un ARREGLO para ordenarlas y calcular. Devuelve {cantidad, mínimo, promedio, mediana, máximo}.
     */
    public double[] estadisticasHumedad(Sector sector, int cantidad) throws RiegoException {
        ArrayList<Double> lista = dao.ultimasHumedades(sector.getSectorId(), cantidad);
        if (lista.isEmpty()) {
            throw new RiegoException("El sector " + sector.getNombre() + " todavía no tiene lecturas de humedad.");
        }
        double[] valores = new double[lista.size()];
        for (int i = 0; i < valores.length; i++) {
            valores[i] = lista.get(i);
        }
        Arrays.sort(valores);
        double suma = 0;
        for (double v : valores) {
            suma += v;
        }
        int n = valores.length;
        double mediana = (n % 2 == 1) ? valores[n / 2] : (valores[n / 2 - 1] + valores[n / 2]) / 2;
        return new double[]{n, valores[0], suma / n, mediana, valores[n - 1]};
    }

    /** RF05: genera una alerta por cada sector sin lecturas recientes (si no tiene ya una pendiente). */
    public ArrayList<String> verificarComunicacion() throws RiegoException {
        ArrayList<String> resultado = new ArrayList<>();
        LocalDateTime limite = LocalDateTime.now().minusHours(HORAS_SIN_LECTURA_PARA_ALERTA);
        for (Sector sector : controlador.getSectores()) {
            LocalDateTime ultima = sector.getFechaUltimaLectura();
            if (ultima != null && !ultima.isBefore(limite)) {
                resultado.add(sector.getNombre() + ": comunicación normal.");
            } else if (tieneAlertaPendiente(sector, Alerta.FALLA_COMUNICACION)) {
                resultado.add(sector.getNombre() + ": sin lecturas recientes (ya existe una alerta pendiente).");
            } else {
                String descripcion = (ultima == null)
                        ? "El sensor de humedad nunca envió una lectura."
                        : "Sin lecturas desde " + ultima.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".";
                controlador.agregarAlerta(dao.registrarAlerta(sector, Alerta.FALLA_COMUNICACION, descripcion, LocalDateTime.now()));
                resultado.add(sector.getNombre() + ": FALLA DE COMUNICACIÓN. Se generó una alerta.");
            }
        }
        return resultado;
    }

    private boolean tieneAlertaPendiente(Sector sector, String tipo) {
        for (Alerta a : controlador.getAlertas()) {
            if (a.getSector() == sector && a.getTipo().equals(tipo) && !a.isAtendida()) {
                return true;
            }
        }
        return false;
    }

    public void atenderAlerta(int alertaId) throws RiegoException {
        for (Alerta a : controlador.getAlertas()) {
            if (a.getAlertaId() == alertaId) {
                if (a.isAtendida()) {
                    throw new RiegoException("La alerta " + alertaId + " ya estaba atendida.");
                }
                dao.marcarAlertaAtendida(alertaId);
                a.marcarAtendida();
                return;
            }
        }
        throw new RiegoException("No existe una alerta con el identificador " + alertaId + ".");
    }

    // ------------------------------------------------------------------ archivos de texto

    /** Exporta el consumo del período a un archivo de texto y devuelve el archivo creado. */
    public File exportarReporteConsumo(LocalDate desde, LocalDate hasta) throws RiegoException {
        double[][] matriz = consumo(desde, hasta);
        ArrayList<String> lineas = new ArrayList<>();
        lineas.add("REPORTE DE CONSUMO DE AGUA - Los Algarrobos (" + controlador.getIdentificador() + ")");
        lineas.add("Generado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        lineas.add("Período: " + (desde == null ? "sin límite" : desde.format(FORMATO_FECHA))
                + " a " + (hasta == null ? "sin límite" : hasta.format(FORMATO_FECHA)));
        lineas.add("");
        lineas.add(String.format("%-4s %-22s %14s %12s %12s", "ID", "Sector", "Automático (L)", "Manual (L)", "Total (L)"));
        double totalAuto = 0;
        double totalManual = 0;
        ArrayList<Sector> sectores = controlador.getSectores();
        for (int i = 0; i < sectores.size(); i++) {
            lineas.add(String.format("%-4d %-22s %14.1f %12.1f %12.1f", sectores.get(i).getSectorId(),
                    sectores.get(i).getNombre(), matriz[i][0], matriz[i][1], matriz[i][0] + matriz[i][1]));
            totalAuto += matriz[i][0];
            totalManual += matriz[i][1];
        }
        lineas.add(String.format("%-27s %14.1f %12.1f %12.1f", "TOTAL", totalAuto, totalManual, totalAuto + totalManual));
        return archivo.guardar(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")), lineas);
    }

    public File ultimoReporte() {
        return archivo.ultimoReporte();
    }

    public ArrayList<String> leerReporte(File reporte) throws RiegoException {
        return archivo.leer(reporte);
    }
}
