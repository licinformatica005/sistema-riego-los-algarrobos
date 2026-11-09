package gestionriego.persistencia;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Administra los datos de conexión a MySQL (JDBC). Lee la URL, el usuario y la clave del archivo de texto
 * conexion.properties, de modo que las credenciales no queden escritas dentro del código.
 * Si el archivo no existe se usan los valores por defecto de XAMPP (usuario root, sin clave).
 */
public class ConexionBD {

    public static final String ARCHIVO_POR_DEFECTO = "conexion.properties";
    private static final String URL_POR_DEFECTO =
            "jdbc:mysql://localhost:3306/riego_los_algarrobos?useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=America/Argentina/Buenos_Aires";

    private final String url;
    private final String usuario;
    private final String clave;

    public ConexionBD() throws PersistenciaException {
        this(System.getProperty("riego.config", ARCHIVO_POR_DEFECTO));
    }

    public ConexionBD(String rutaArchivo) throws PersistenciaException {
        Properties propiedades = new Properties();
        File archivo = new File(rutaArchivo);
        if (archivo.exists()) {
            // try-with-resources: el lector se cierra solo, aunque ocurra un error
            try (FileReader lector = new FileReader(archivo, StandardCharsets.UTF_8)) {
                propiedades.load(lector);
            } catch (IOException e) {
                throw new PersistenciaException("No se pudo leer el archivo de configuración " + rutaArchivo
                        + ": " + e.getMessage(), e);
            }
        }
        this.url = propiedades.getProperty("url", URL_POR_DEFECTO);
        this.usuario = propiedades.getProperty("usuario", "root");
        this.clave = propiedades.getProperty("clave", "");
    }

    /** Abre una conexión nueva. Quien la recibe debe cerrarla (try-with-resources). */
    public Connection obtener() throws PersistenciaException {
        try {
            return DriverManager.getConnection(url, usuario, clave);
        } catch (SQLException e) {
            String ayuda = "";
            if (e.getMessage() != null && e.getMessage().contains("No suitable driver")) {
                ayuda = " Falta agregar el conector JDBC (mysql-connector-j) a las bibliotecas del proyecto.";
            }
            throw new PersistenciaException("No se pudo conectar a la base de datos (" + url + "): "
                    + e.getMessage() + "." + ayuda + " Verifique que MySQL esté iniciado y que se haya importado schema_completo.sql.", e);
        }
    }

    public String getUrl() { return url; }
}
