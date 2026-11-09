package gestionriego.persistencia;

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

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Segunda implementación de RiegoDAO: guarda todo en listas en memoria con los mismos datos de prueba del script SQL.
 * No se usa en el programa final; permite ejecutar las pruebas de la lógica de riego sin depender de MySQL, y
 * demuestra la ventaja de programar contra una interfaz.
 */
public class RiegoDAOMemoria implements RiegoDAO {

    private static class Lectura {
        final int sectorId; final double valor; final LocalDateTime fecha;
        Lectura(int sectorId, double valor, LocalDateTime fecha) { this.sectorId = sectorId; this.valor = valor; this.fecha = fecha; }
    }

    private static class Riego {
        final int id; final int valvulaId; final String tipo; final LocalDateTime inicio;
        LocalDateTime fin; double volumen;
        Riego(int id, int valvulaId, String tipo, LocalDateTime inicio) { this.id = id; this.valvulaId = valvulaId; this.tipo = tipo; this.inicio = inicio; }
    }

    private ControladorCampo controlador;
    private final ArrayList<Lectura> lecturas = new ArrayList<>();
    private final ArrayList<Riego> riegos = new ArrayList<>();
    private int ultimoRiego = 0;
    private int ultimaAlerta = 0;
    private int ultimoSector = 0;
    private int ultimoSensor = 0;

    @Override
    public ArrayList<String> getAdvertencias() {
        return new ArrayList<>();
    }

    @Override
    public ControladorCampo cargarControlador(String identificador) throws PersistenciaException {
        if (controlador == null) {
            try {
                construirDatosDePrueba(identificador);
            } catch (RiegoException e) {
                throw new PersistenciaException("Datos de prueba inválidos: " + e.getMessage(), e);
            }
        }
        return controlador;
    }

    private void construirDatosDePrueba(String identificador) throws RiegoException {
        controlador = new ControladorCampo(1, identificador, 0.0);
        LocalDateTime d20 = LocalDateTime.of(2026, 9, 20, 8, 0);

        Valvula v1 = new Valvula(1, 12.5);
        SensorHumedad h1 = new SensorHumedad(1);
        h1.registrarLectura(45.2, d20.withHour(10));
        v1.agregarSensor(h1);
        v1.agregarSensor(new SensorClima(2));
        Valvula v2 = new Valvula(2, 10.0);
        SensorHumedad h2 = new SensorHumedad(3);
        h2.registrarLectura(22.1, d20.withHour(8).withMinute(15));
        v2.agregarSensor(h2);
        Valvula v3 = new Valvula(3, 8.0);
        v3.agregarSensor(new SensorHumedad(4));

        Sector s1 = new Sector(1, "Sector Viñedo A", 4.5, "Vid", v1, new Umbral(30, 60));
        Sector s2 = new Sector(2, "Sector Olivar B", 6.2, "Olivo", v2, new Umbral(25, 55));
        Sector s3 = new Sector(3, "Sector Hortalizas C", 2.1, "Hortalizas", v3, new Umbral(35, 65));
        controlador.agregarSector(s1);
        controlador.agregarSector(s2);
        controlador.agregarSector(s3);
        ultimoSector = 3;
        ultimoSensor = 4;

        lecturas.add(new Lectura(1, 28.5, d20));
        lecturas.add(new Lectura(1, 45.2, d20.withHour(10)));
        lecturas.add(new Lectura(2, 22.1, d20.withMinute(15)));

        Riego r1 = new Riego(++ultimoRiego, 1, RegistroRiego.AUTOMATICO, d20);
        r1.fin = d20.withMinute(45); r1.volumen = 562.5;
        Riego r2 = new Riego(++ultimoRiego, 2, RegistroRiego.MANUAL, LocalDateTime.of(2026, 9, 19, 7, 0));
        r2.fin = LocalDateTime.of(2026, 9, 19, 7, 30); r2.volumen = 300.0;
        riegos.add(r1);
        riegos.add(r2);
        controlador.registrarConsumo(862.5 - 862.5);   // el script SQL parte con volumen_agua_total = 0

        controlador.agregarAlerta(new Alerta(++ultimaAlerta, s2, d20.withMinute(15), Alerta.HUMEDAD_CRITICA,
                "Humedad por debajo del umbral mínimo configurado", false));
    }

    @Override
    public Sector insertarSector(ControladorCampo c, String nombre, double superficie, String tipoCultivo,
                                 double caudal, double humedadMinima, double humedadMaxima) throws RiegoException {
        Umbral umbral = new Umbral(humedadMinima, humedadMaxima);
        int id = ++ultimoSector;
        Valvula valvula = new Valvula(id, caudal);
        valvula.agregarSensor(new SensorHumedad(++ultimoSensor));
        return new Sector(id, nombre, superficie, tipoCultivo, valvula, umbral);
    }

    @Override
    public void registrarLecturaHumedad(SensorHumedad sensor, double humedad, LocalDateTime fecha) {
        lecturas.add(new Lectura(sectorDeSensor(sensor.getSensorId()), humedad, fecha));
    }

    @Override
    public void registrarLecturaClima(SensorClima sensor, double temperatura, double precipitacion, LocalDateTime fecha) {
        // el modelo en memoria no necesita guardar el clima para las pruebas
    }

    @Override
    public RegistroRiego iniciarRiego(Valvula valvula, String tipoActivacion, LocalDateTime inicio) {
        Riego riego = new Riego(++ultimoRiego, valvula.getValvulaId(), tipoActivacion, inicio);
        riegos.add(riego);
        return new RegistroRiego(riego.id, inicio, tipoActivacion);
    }

    @Override
    public void finalizarRiego(ControladorCampo c, Valvula valvula, RegistroRiego registro, LocalDateTime fin, double volumenLitros) {
        for (Riego r : riegos) {
            if (r.id == registro.getRegistroId()) {
                r.fin = fin;
                r.volumen = volumenLitros;
            }
        }
    }

    @Override
    public Alerta registrarAlerta(Sector sector, String tipo, String descripcion, LocalDateTime fecha) {
        return new Alerta(++ultimaAlerta, sector, fecha, tipo, descripcion, false);
    }

    @Override
    public void marcarAlertaAtendida(int alertaId) {
        // la marca se aplica sobre el objeto Alerta que ya está en memoria
    }

    @Override
    public ArrayList<Double> ultimasHumedades(int sectorId, int cantidad) {
        ArrayList<Lectura> delSector = new ArrayList<>();
        for (Lectura l : lecturas) {
            if (l.sectorId == sectorId) {
                delSector.add(l);
            }
        }
        ArrayList<Double> valores = new ArrayList<>();
        for (int i = delSector.size() - 1; i >= 0 && valores.size() < cantidad; i--) {
            valores.add(delSector.get(i).valor);
        }
        return valores;
    }

    @Override
    public double[][] consumoPorSector(ControladorCampo c, LocalDateTime desde, LocalDateTime hasta) {
        ArrayList<Sector> sectores = c.getSectores();
        double[][] matriz = new double[sectores.size()][2];
        for (Riego r : riegos) {
            if (r.fin == null || r.inicio.isBefore(desde) || !r.inicio.isBefore(hasta)) {
                continue;
            }
            for (int i = 0; i < sectores.size(); i++) {
                if (sectores.get(i).getValvula().getValvulaId() == r.valvulaId) {
                    matriz[i][RegistroRiego.MANUAL.equals(r.tipo) ? 1 : 0] += r.volumen;
                }
            }
        }
        return matriz;
    }

    private int sectorDeSensor(int sensorId) {
        for (Sector s : controlador.getSectores()) {
            for (Sensor sensor : s.getValvula().getSensores()) {
                if (sensor.getSensorId() == sensorId) {
                    return s.getSectorId();
                }
            }
        }
        return -1;
    }
}
