package gestionriego;

import gestionriego.controlador.GestorRiego;
import gestionriego.persistencia.ArchivoReporte;
import gestionriego.persistencia.ConexionBD;
import gestionriego.persistencia.PersistenciaException;
import gestionriego.persistencia.RiegoDAO;
import gestionriego.persistencia.RiegoDAOMySQL;
import gestionriego.vista.MonitorSector;

/**
 * Clase principal. Arma las tres capas del patrón MVC y las conecta:
 * ConexionBD y RiegoDAOMySQL (Modelo / persistencia) -> GestorRiego (Controlador) -> MonitorSector (Vista).
 */
public class GestionRiego {

    public static void main(String[] args) {
        try {
            ConexionBD conexion = new ConexionBD();
            RiegoDAO dao = new RiegoDAOMySQL(conexion);
            GestorRiego gestor = new GestorRiego(dao, new ArchivoReporte("reportes"), "CTRL-NORTE");
            gestor.iniciar();                     // primera consulta a MySQL: carga sectores, válvulas y alertas
            new MonitorSector(gestor).iniciar();
        } catch (PersistenciaException e) {
            System.out.println("No se pudo iniciar el sistema: " + e.getMessage());
        }
    }
}
