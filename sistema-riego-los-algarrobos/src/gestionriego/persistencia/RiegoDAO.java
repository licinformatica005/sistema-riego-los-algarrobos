package gestionriego.persistencia;

import gestionriego.modelo.Alerta;
import gestionriego.modelo.ControladorCampo;
import gestionriego.modelo.RegistroRiego;
import gestionriego.modelo.RiegoException;
import gestionriego.modelo.Sector;
import gestionriego.modelo.SensorClima;
import gestionriego.modelo.SensorHumedad;
import gestionriego.modelo.Valvula;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * INTERFAZ de acceso a datos (patrón DAO). Define QUÉ operaciones de persistencia necesita el sistema, sin decir
 * CÓMO se resuelven. GestorRiego depende de esta interfaz y no de MySQL: la implementación real usa JDBC
 * (RiegoDAOMySQL) y una implementación en memoria (RiegoDAOMemoria) permite probar la lógica sin base de datos.
 */
public interface RiegoDAO {

    /** Avisos generados durante la última carga (por ejemplo, estados corregidos); vacía si no hubo ninguno. */
    ArrayList<String> getAdvertencias();

    /** Carga el controlador con sus sectores, válvulas, sensores, umbrales y alertas (consultas SELECT). */
    ControladorCampo cargarControlador(String identificador) throws PersistenciaException;

    /** Inserta un sector junto con su válvula, su sensor de humedad y su umbral, en una sola transacción. */
    Sector insertarSector(ControladorCampo controlador, String nombre, double superficie, String tipoCultivo,
                          double caudal, double humedadMinima, double humedadMaxima) throws RiegoException;

    void registrarLecturaHumedad(SensorHumedad sensor, double humedad, LocalDateTime fecha) throws PersistenciaException;

    void registrarLecturaClima(SensorClima sensor, double temperatura, double precipitacion, LocalDateTime fecha)
            throws PersistenciaException;

    /** Abre un riego: inserta el registro y actualiza el estado de la válvula (transacción). */
    RegistroRiego iniciarRiego(Valvula valvula, String tipoActivacion, LocalDateTime inicio) throws PersistenciaException;

    /** Cierra un riego: actualiza el registro, la válvula y el volumen total del controlador (transacción). */
    void finalizarRiego(ControladorCampo controlador, Valvula valvula, RegistroRiego registro,
                        LocalDateTime fin, double volumenLitros) throws PersistenciaException;

    Alerta registrarAlerta(Sector sector, String tipo, String descripcion, LocalDateTime fecha) throws PersistenciaException;

    void marcarAlertaAtendida(int alertaId) throws PersistenciaException;

    /** Últimas lecturas de humedad de un sector (la más reciente primero). */
    ArrayList<Double> ultimasHumedades(int sectorId, int cantidad) throws PersistenciaException;

    /**
     * Litros regados por sector y tipo de activación en un período. Devuelve una matriz con una fila por sector
     * (en el orden de controlador.getSectores()) y dos columnas: [0] automático y [1] manual.
     */
    double[][] consumoPorSector(ControladorCampo controlador, LocalDateTime desde, LocalDateTime hasta)
            throws PersistenciaException;
}
