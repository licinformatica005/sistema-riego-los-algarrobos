package gestionriego.pruebas;

import gestionriego.controlador.GestorRiego;
import gestionriego.modelo.AccionRiego;
import gestionriego.modelo.RegistroRiego;
import gestionriego.modelo.RiegoException;
import gestionriego.modelo.Sector;
import gestionriego.modelo.Umbral;
import gestionriego.persistencia.ArchivoReporte;
import gestionriego.persistencia.RiegoDAOMemoria;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;

/**
 * Pruebas de la lógica de riego (CP001 y CP002 del plan de pruebas). Se ejecutan sobre RiegoDAOMemoria, por lo que
 * no necesitan MySQL. Muestra OK o FALLA por cada caso y termina con código 1 si alguno falla.
 */
public class PruebasGestorRiego {

    private static int aprobadas = 0;
    private static int total = 0;

    private static void verificar(String nombre, boolean correcto) {
        total++;
        if (correcto) aprobadas++;
        System.out.printf("  %-62s %s%n", nombre, correcto ? "OK" : "FALLA");
    }

    public static void main(String[] args) throws Exception {
        GestorRiego gestor = new GestorRiego(new RiegoDAOMemoria(), new ArchivoReporte(
                Files.createTempDirectory("reportes").toString()), "CTRL-NORTE");
        gestor.iniciar();

        System.out.println("CP001 - verificarUmbral() con mínimo = 30 y máximo = 60");
        Umbral umbral = new Umbral(30, 60);
        double[] humedades = {0, 29, 30, 31, 45, 59, 60, 61, 100};
        AccionRiego[] esperado = {AccionRiego.ACTIVAR, AccionRiego.ACTIVAR, AccionRiego.MANTENER, AccionRiego.MANTENER,
                AccionRiego.MANTENER, AccionRiego.MANTENER, AccionRiego.DETENER, AccionRiego.DETENER, AccionRiego.DETENER};
        for (int i = 0; i < humedades.length; i++) {
            verificar("humedad " + (int) humedades[i] + " % -> " + esperado[i],
                    gestor.verificarUmbral(umbral, humedades[i]) == esperado[i]);
        }
        try {
            gestor.verificarUmbral(umbral, 120);
            verificar("humedad 120 % lanza RiegoException", false);
        } catch (RiegoException e) {
            verificar("humedad 120 % lanza RiegoException", true);
        }

        System.out.println("CP002 - riego automático sobre el sector Olivar B (umbral 25-55 %, 10 L/min)");
        Sector olivar = gestor.buscarSector(2);
        gestor.procesarLectura(olivar, 40, 0);
        verificar("CP002-01 40 % con válvula detenida: no se activa", !olivar.getValvula().estaActiva());
        gestor.procesarLectura(olivar, 20, 0);
        verificar("CP002-02 20 % con válvula detenida: se activa en modo Automático",
                olivar.getValvula().estaActiva()
                && RegistroRiego.AUTOMATICO.equals(olivar.getValvula().getRegistroEnCurso().getTipoActivacion()));
        int registroAbierto = olivar.getValvula().getRegistroEnCurso().getRegistroId();
        gestor.procesarLectura(olivar, 40, 0);
        verificar("CP002-03 40 % con válvula activa: continúa activa", olivar.getValvula().estaActiva());
        gestor.procesarLectura(olivar, 20, 0);
        verificar("CP002-04 20 % con válvula activa: sin segundo registro",
                olivar.getValvula().getRegistroEnCurso().getRegistroId() == registroAbierto);
        gestor.procesarLectura(olivar, 56, 30);
        verificar("CP002-05 56 % con válvula activa: se detiene (300 L)",
                !olivar.getValvula().estaActiva() && gestor.getControlador().getVolumenAguaTotal() == 300.0);
        gestor.procesarLectura(olivar, 56, 0);
        verificar("CP002-06 56 % con válvula detenida: no cambia nada", !olivar.getValvula().estaActiva());
        try {
            gestor.procesarLectura(olivar, -5, 0);
            verificar("CP002-07 -5 % lanza RiegoException", false);
        } catch (RiegoException e) {
            verificar("CP002-07 -5 % lanza RiegoException", !olivar.getValvula().estaActiva());
        }
        try {
            gestor.buscarSector(9);
            verificar("CP002-09 sector inexistente lanza RiegoException", false);
        } catch (RiegoException e) {
            verificar("CP002-09 sector inexistente lanza RiegoException", true);
        }
        Sector hortalizas = gestor.buscarSector(3);
        gestor.activarManual(hortalizas);
        try {
            gestor.activarManual(hortalizas);
            verificar("CP002-10 activación manual doble lanza RiegoException", false);
        } catch (RiegoException e) {
            verificar("CP002-10 activación manual doble lanza RiegoException", true);
        }
        gestor.detenerManual(hortalizas, 15);

        System.out.println("Arreglos, ArrayList y archivos");
        double[][] consumo = gestor.consumo(null, null);
        verificar("matriz de consumo: Olivar B = 300 manual + 300 automático", consumo[1][0] == 300.0 && consumo[1][1] == 300.0);
        verificar("matriz de consumo: Hortalizas C manual = 120 L", consumo[2][1] == 120.0);
        double[] estadistica = gestor.estadisticasHumedad(olivar, 10);
        verificar("estadísticas de humedad: mínimo <= mediana <= máximo", estadistica[1] <= estadistica[3] && estadistica[3] <= estadistica[4]);
        ArrayList<Sector> orden = gestor.sectoresOrdenadosPorHumedad();
        verificar("ordenación por inserción: el sector más seco va primero", orden.get(0).getSectorId() == 2 || orden.get(0).getSectorId() == 1);
        File reporte = gestor.exportarReporteConsumo(LocalDate.of(2026, 9, 1), null);
        verificar("archivo de texto: se crea el reporte y se puede leer", reporte.exists() && !gestor.leerReporte(reporte).isEmpty());
        verificar("comunicación: se generan alertas por falta de lecturas", gestor.verificarComunicacion().size() == 3);

        System.out.println();
        System.out.println("Casos aprobados: " + aprobadas + " de " + total);
        if (aprobadas != total) {
            System.exit(1);
        }
    }
}
