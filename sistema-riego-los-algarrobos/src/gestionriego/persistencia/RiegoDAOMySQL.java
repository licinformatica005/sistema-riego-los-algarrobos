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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Implementación del DAO con JDBC puro sobre MySQL (sin frameworks, como exige la consigna).
 * Criterios aplicados en todos los métodos:
 *  - PreparedStatement con parámetros (?) para evitar inyección SQL;
 *  - try-with-resources para cerrar Connection, PreparedStatement y ResultSet;
 *  - las operaciones que tocan varias tablas se ejecutan en una transacción (commit / rollback);
 *  - todo SQLException se traduce a PersistenciaException, con un mensaje comprensible para el usuario.
 */
public class RiegoDAOMySQL implements RiegoDAO {

    private final ConexionBD conexionBD;
    private final ArrayList<String> advertencias = new ArrayList<>();

    public RiegoDAOMySQL(ConexionBD conexionBD) {
        this.conexionBD = conexionBD;
    }

    // ------------------------------------------------------------------ consultas (SELECT)

    @Override
    public ArrayList<String> getAdvertencias() {
        return advertencias;
    }

    @Override
    public ControladorCampo cargarControlador(String identificador) throws PersistenciaException {
        advertencias.clear();
        String sqlControlador = "SELECT controlador_id, identificador, volumen_agua_total FROM controladores WHERE identificador = ?";
        String sqlSectores =
                "SELECT s.sector_id, s.nombre, s.superficie, s.tipo_cultivo, v.valvula_id, v.caudal, v.estado, "
              + "       u.humedad_minima, u.humedad_maxima "
              + "FROM sectores s "
              + "INNER JOIN valvulas v ON v.sector_id = s.sector_id "
              + "INNER JOIN umbrales u ON u.sector_id = s.sector_id "
              + "WHERE s.controlador_id = ? ORDER BY s.sector_id";
        String sqlAlertas =
                "SELECT a.alerta_id, a.sector_id, a.fecha_alerta, a.tipo_alerta, a.descripcion, a.atendida "
              + "FROM alertas a INNER JOIN sectores s ON s.sector_id = a.sector_id "
              + "WHERE s.controlador_id = ? ORDER BY a.fecha_alerta, a.alerta_id";

        try (Connection con = conexionBD.obtener()) {
            ControladorCampo controlador;
            try (PreparedStatement ps = con.prepareStatement(sqlControlador)) {
                ps.setString(1, identificador);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new PersistenciaException("No existe el controlador '" + identificador
                                + "' en la base de datos. Importe el script schema_completo.sql.");
                    }
                    controlador = new ControladorCampo(rs.getInt("controlador_id"), rs.getString("identificador"),
                            rs.getDouble("volumen_agua_total"));
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sqlSectores)) {
                ps.setInt(1, controlador.getControladorId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Valvula valvula = new Valvula(rs.getInt("valvula_id"), rs.getDouble("caudal"));
                        cargarSensores(con, valvula);
                        boolean figuraActiva = "Activa".equalsIgnoreCase(rs.getString("estado"));
                        cargarEstadoDeRiego(con, valvula, figuraActiva);
                        Umbral umbral = new Umbral(rs.getDouble("humedad_minima"), rs.getDouble("humedad_maxima"));
                        controlador.agregarSector(new Sector(rs.getInt("sector_id"), rs.getString("nombre"),
                                rs.getDouble("superficie"), rs.getString("tipo_cultivo"), valvula, umbral));
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sqlAlertas)) {
                ps.setInt(1, controlador.getControladorId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Sector sector = controlador.buscarSectorPorId(rs.getInt("sector_id"));
                        controlador.agregarAlerta(new Alerta(rs.getInt("alerta_id"), sector,
                                rs.getTimestamp("fecha_alerta").toLocalDateTime(), rs.getString("tipo_alerta"),
                                rs.getString("descripcion"), rs.getBoolean("atendida")));
                    }
                }
            }
            return controlador;
        } catch (SQLException e) {
            throw new PersistenciaException("Error al consultar la base de datos: " + e.getMessage(), e);
        } catch (PersistenciaException e) {
            throw e;
        } catch (RiegoException e) {
            throw new PersistenciaException("Los datos almacenados no son válidos: " + e.getMessage(), e);
        }
    }

    /** Crea el sensor que corresponde a cada fila de la tabla sensores (herencia + polimorfismo). */
    private void cargarSensores(Connection con, Valvula valvula) throws SQLException {
        String sqlSensores = "SELECT sensor_id, tipo FROM sensores WHERE valvula_id = ? ORDER BY sensor_id";
        String sqlUltima = "SELECT humedad_porcentaje, fecha_registro FROM registros_humedad "
                         + "WHERE sensor_id = ? ORDER BY fecha_registro DESC, registro_id DESC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sqlSensores)) {
            ps.setInt(1, valvula.getValvulaId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("sensor_id");
                    Sensor sensor;
                    if (Sensor.TIPO_CLIMA.equalsIgnoreCase(rs.getString("tipo"))) {
                        sensor = new SensorClima(id);
                    } else {
                        SensorHumedad humedad = new SensorHumedad(id);
                        try (PreparedStatement ps2 = con.prepareStatement(sqlUltima)) {
                            ps2.setInt(1, id);
                            try (ResultSet rs2 = ps2.executeQuery()) {
                                if (rs2.next()) {
                                    humedad.registrarLectura(rs2.getDouble("humedad_porcentaje"),
                                            rs2.getTimestamp("fecha_registro").toLocalDateTime());
                                }
                            }
                        }
                        sensor = humedad;
                    }
                    valvula.agregarSensor(sensor);
                }
            }
        }
    }

    /**
     * Restaura el riego en curso de una válvula. Si la tabla valvulas dice 'Activa' pero no hay un registro de riego
     * abierto (dato inconsistente, por ejemplo tras una caída del sistema), se corrige el estado a 'Inactiva'.
     */
    private void cargarEstadoDeRiego(Connection con, Valvula valvula, boolean figuraActiva) throws SQLException {
        String sql = "SELECT registro_id, fecha_inicio, tipo_activacion FROM registros_riego "
                   + "WHERE valvula_id = ? AND fecha_fin IS NULL ORDER BY fecha_inicio DESC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, valvula.getValvulaId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    valvula.abrir(new RegistroRiego(rs.getInt("registro_id"),
                            rs.getTimestamp("fecha_inicio").toLocalDateTime(), rs.getString("tipo_activacion")));
                    return;
                }
            }
        }
        if (figuraActiva) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE valvulas SET estado = 'Inactiva' WHERE valvula_id = ?")) {
                ps.setInt(1, valvula.getValvulaId());
                ps.executeUpdate();
            }
            advertencias.add("La válvula " + valvula.getValvulaId()
                    + " figuraba activa sin un riego abierto: se corrigió su estado a Inactiva.");
        }
    }

    @Override
    public ArrayList<Double> ultimasHumedades(int sectorId, int cantidad) throws PersistenciaException {
        String sql = "SELECT rh.humedad_porcentaje FROM registros_humedad rh "
                   + "INNER JOIN sensores se ON se.sensor_id = rh.sensor_id "
                   + "INNER JOIN valvulas v ON v.valvula_id = se.valvula_id "
                   + "WHERE v.sector_id = ? ORDER BY rh.fecha_registro DESC, rh.registro_id DESC LIMIT ?";
        ArrayList<Double> valores = new ArrayList<>();
        try (Connection con = conexionBD.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, sectorId);
            ps.setInt(2, cantidad);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    valores.add(rs.getDouble(1));
                }
            }
            return valores;
        } catch (SQLException e) {
            throw new PersistenciaException("Error al consultar las lecturas de humedad: " + e.getMessage(), e);
        }
    }

    @Override
    public double[][] consumoPorSector(ControladorCampo controlador, LocalDateTime desde, LocalDateTime hasta)
            throws PersistenciaException {
        String sql = "SELECT s.sector_id, rr.tipo_activacion, SUM(rr.volumen_litros) AS litros "
                   + "FROM registros_riego rr "
                   + "INNER JOIN valvulas v ON v.valvula_id = rr.valvula_id "
                   + "INNER JOIN sectores s ON s.sector_id = v.sector_id "
                   + "WHERE s.controlador_id = ? AND rr.fecha_fin IS NOT NULL "
                   + "  AND rr.fecha_inicio >= ? AND rr.fecha_inicio < ? "
                   + "GROUP BY s.sector_id, rr.tipo_activacion";
        ArrayList<Sector> sectores = controlador.getSectores();
        double[][] matriz = new double[sectores.size()][2];     // [sector][0 = automático, 1 = manual]
        try (Connection con = conexionBD.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, controlador.getControladorId());
            ps.setTimestamp(2, Timestamp.valueOf(desde));
            ps.setTimestamp(3, Timestamp.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int fila = indiceDeSector(sectores, rs.getInt("sector_id"));
                    int columna = RegistroRiego.MANUAL.equalsIgnoreCase(rs.getString("tipo_activacion")) ? 1 : 0;
                    if (fila >= 0) {
                        matriz[fila][columna] += rs.getDouble("litros");
                    }
                }
            }
            return matriz;
        } catch (SQLException e) {
            throw new PersistenciaException("Error al consultar el consumo de agua: " + e.getMessage(), e);
        }
    }

    private int indiceDeSector(ArrayList<Sector> sectores, int sectorId) {
        for (int i = 0; i < sectores.size(); i++) {
            if (sectores.get(i).getSectorId() == sectorId) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ altas y modificaciones (INSERT / UPDATE)

    @Override
    public Sector insertarSector(ControladorCampo controlador, String nombre, double superficie, String tipoCultivo,
                                 double caudal, double humedadMinima, double humedadMaxima) throws RiegoException {
        // Las entidades validan los datos ANTES de abrir la transacción
        Umbral umbral = new Umbral(humedadMinima, humedadMaxima);
        try (Connection con = conexionBD.obtener()) {
            con.setAutoCommit(false);
            try {
                int sectorId = siguienteId(con, "sectores", "sector_id");
                int valvulaId = siguienteId(con, "valvulas", "valvula_id");
                int sensorId = siguienteId(con, "sensores", "sensor_id");
                int umbralId = siguienteId(con, "umbrales", "umbral_id");

                Valvula valvula = new Valvula(valvulaId, caudal);
                valvula.agregarSensor(new SensorHumedad(sensorId));
                Sector sector = new Sector(sectorId, nombre, superficie, tipoCultivo, valvula, umbral);

                ejecutar(con, "INSERT INTO sectores (sector_id, nombre, superficie, tipo_cultivo, controlador_id) VALUES (?, ?, ?, ?, ?)",
                        sectorId, sector.getNombre(), superficie, sector.getTipoCultivo(), controlador.getControladorId());
                ejecutar(con, "INSERT INTO valvulas (valvula_id, sector_id, caudal, estado) VALUES (?, ?, ?, ?)",
                        valvulaId, sectorId, caudal, "Inactiva");
                ejecutar(con, "INSERT INTO sensores (sensor_id, valvula_id, tipo) VALUES (?, ?, ?)",
                        sensorId, valvulaId, Sensor.TIPO_HUMEDAD);
                ejecutar(con, "INSERT INTO umbrales (umbral_id, sector_id, humedad_minima, humedad_maxima) VALUES (?, ?, ?, ?)",
                        umbralId, sectorId, humedadMinima, humedadMaxima);
                con.commit();
                return sector;
            } catch (SQLException | RiegoException e) {
                deshacer(con);          // ninguna de las cuatro filas queda guardada
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo guardar el sector: " + e.getMessage(), e);
        }
    }

    @Override
    public void registrarLecturaHumedad(SensorHumedad sensor, double humedad, LocalDateTime fecha) throws PersistenciaException {
        try (Connection con = conexionBD.obtener()) {
            int id = siguienteId(con, "registros_humedad", "registro_id");
            ejecutar(con, "INSERT INTO registros_humedad (registro_id, sensor_id, fecha_registro, humedad_porcentaje) VALUES (?, ?, ?, ?)",
                    id, sensor.getSensorId(), Timestamp.valueOf(fecha), humedad);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo guardar la lectura de humedad: " + e.getMessage(), e);
        }
    }

    @Override
    public void registrarLecturaClima(SensorClima sensor, double temperatura, double precipitacion, LocalDateTime fecha)
            throws PersistenciaException {
        try (Connection con = conexionBD.obtener()) {
            int id = siguienteId(con, "registros_clima", "registro_id");
            ejecutar(con, "INSERT INTO registros_clima (registro_id, sensor_id, fecha_registro, temperatura, precipitacion_mm) VALUES (?, ?, ?, ?, ?)",
                    id, sensor.getSensorId(), Timestamp.valueOf(fecha), temperatura, precipitacion);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo guardar la lectura de clima: " + e.getMessage(), e);
        }
    }

    @Override
    public RegistroRiego iniciarRiego(Valvula valvula, String tipoActivacion, LocalDateTime inicio) throws PersistenciaException {
        try (Connection con = conexionBD.obtener()) {
            con.setAutoCommit(false);
            try {
                int id = siguienteId(con, "registros_riego", "registro_id");
                ejecutar(con, "INSERT INTO registros_riego (registro_id, valvula_id, fecha_inicio, fecha_fin, volumen_litros, tipo_activacion) "
                            + "VALUES (?, ?, ?, NULL, 0, ?)", id, valvula.getValvulaId(), Timestamp.valueOf(inicio), tipoActivacion);
                actualizarUnaFila(con, "UPDATE valvulas SET estado = 'Activa' WHERE valvula_id = ?", valvula.getValvulaId());
                con.commit();
                return new RegistroRiego(id, inicio, tipoActivacion);
            } catch (SQLException e) {
                deshacer(con);
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo registrar el inicio del riego: " + e.getMessage(), e);
        }
    }

    @Override
    public void finalizarRiego(ControladorCampo controlador, Valvula valvula, RegistroRiego registro,
                               LocalDateTime fin, double volumenLitros) throws PersistenciaException {
        try (Connection con = conexionBD.obtener()) {
            con.setAutoCommit(false);
            try {
                actualizarUnaFila(con, "UPDATE registros_riego SET fecha_fin = ?, volumen_litros = ? WHERE registro_id = ?",
                        Timestamp.valueOf(fin), volumenLitros, registro.getRegistroId());
                actualizarUnaFila(con, "UPDATE valvulas SET estado = 'Inactiva' WHERE valvula_id = ?", valvula.getValvulaId());
                actualizarUnaFila(con, "UPDATE controladores SET volumen_agua_total = volumen_agua_total + ? WHERE controlador_id = ?",
                        volumenLitros, controlador.getControladorId());
                con.commit();
            } catch (SQLException e) {
                deshacer(con);
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo registrar el fin del riego: " + e.getMessage(), e);
        }
    }

    @Override
    public Alerta registrarAlerta(Sector sector, String tipo, String descripcion, LocalDateTime fecha) throws PersistenciaException {
        try (Connection con = conexionBD.obtener()) {
            int id = siguienteId(con, "alertas", "alerta_id");
            ejecutar(con, "INSERT INTO alertas (alerta_id, sector_id, fecha_alerta, tipo_alerta, descripcion, atendida) VALUES (?, ?, ?, ?, ?, FALSE)",
                    id, sector.getSectorId(), Timestamp.valueOf(fecha), tipo, descripcion);
            return new Alerta(id, sector, fecha, tipo, descripcion, false);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo guardar la alerta: " + e.getMessage(), e);
        }
    }

    @Override
    public void marcarAlertaAtendida(int alertaId) throws PersistenciaException {
        try (Connection con = conexionBD.obtener()) {
            actualizarUnaFila(con, "UPDATE alertas SET atendida = TRUE WHERE alerta_id = ?", alertaId);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo marcar la alerta como atendida: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ auxiliares JDBC

    /** Identificador siguiente = máximo actual + 1 (el script del TP2 define las claves como INT sin AUTO_INCREMENT). */
    private int siguienteId(Connection con, String tabla, String columna) throws SQLException {
        // tabla y columna son constantes del código (nunca datos del usuario), por eso se pueden concatenar
        String sql = "SELECT COALESCE(MAX(" + columna + "), 0) + 1 FROM " + tabla;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void ejecutar(Connection con, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            asignar(ps, parametros);
            ps.executeUpdate();
        }
    }

    /** Ejecuta un UPDATE que debe modificar exactamente una fila; si no, se considera un error. */
    private void actualizarUnaFila(Connection con, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            asignar(ps, parametros);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("La actualización no modificó ninguna fila (registro inexistente).");
            }
        }
    }

    private void asignar(PreparedStatement ps, Object[] parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            ps.setObject(i + 1, parametros[i]);
        }
    }

    private void deshacer(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignorada) {
            // si el rollback falla, la conexión se cierra igual y el servidor descarta la transacción
        }
    }
}
