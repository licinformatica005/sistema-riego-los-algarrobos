package gestionriego.persistencia;

import gestionriego.modelo.RiegoException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Manejo de archivos de texto: guarda y recupera los reportes de consumo de agua.
 * Se usa File para ubicar el archivo y BufferedWriter / BufferedReader para escribir y leer línea por línea.
 * Toda IOException se traduce a RiegoException con un mensaje claro.
 */
public class ArchivoReporte {

    private static final String PREFIJO = "reporte_consumo_";
    private static final String EXTENSION = ".txt";

    private final File carpeta;

    public ArchivoReporte(String rutaCarpeta) {
        this.carpeta = new File(rutaCarpeta);
    }

    /** Escribe las líneas en un archivo nuevo dentro de la carpeta de reportes (se crea si no existe). */
    public File guardar(String sufijo, ArrayList<String> lineas) throws RiegoException {
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new RiegoException("No se pudo crear la carpeta de reportes: " + carpeta.getPath());
        }
        File archivo = new File(carpeta, PREFIJO + sufijo + EXTENSION);
        try (BufferedWriter escritor = new BufferedWriter(new FileWriter(archivo, StandardCharsets.UTF_8))) {
            for (String linea : lineas) {
                escritor.write(linea);
                escritor.newLine();
            }
        } catch (IOException e) {
            throw new RiegoException("No se pudo escribir el reporte " + archivo.getPath() + ": " + e.getMessage(), e);
        }
        return archivo;
    }

    /** Lee un archivo de texto completo y devuelve sus líneas. */
    public ArrayList<String> leer(File archivo) throws RiegoException {
        ArrayList<String> lineas = new ArrayList<>();
        try (BufferedReader lector = new BufferedReader(new FileReader(archivo, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                lineas.add(linea);
            }
        } catch (IOException e) {
            throw new RiegoException("No se pudo leer el reporte " + archivo.getPath() + ": " + e.getMessage(), e);
        }
        return lineas;
    }

    /** Devuelve el reporte más reciente (los nombres incluyen fecha y hora) o null si todavía no hay ninguno. */
    public File ultimoReporte() {
        File[] reportes = carpeta.listFiles((dir, nombre) -> nombre.startsWith(PREFIJO) && nombre.endsWith(EXTENSION));
        if (reportes == null || reportes.length == 0) {
            return null;
        }
        Arrays.sort(reportes);                       // orden alfabético = orden cronológico
        return reportes[reportes.length - 1];
    }
}
