package gestionriego.modelo;

import java.util.ArrayList;

/**
 * Controlador de campo (tabla controladores). Agrupa los sectores y las alertas que se cargan desde la base de datos.
 * Se llamaba Controlador en el TP3; se renombró para no confundirlo con el CONTROLADOR del patrón MVC (GestorRiego).
 */
public class ControladorCampo {

    private final int controladorId;
    private final String identificador;
    private double volumenAguaTotal;
    private final ArrayList<Sector> sectores = new ArrayList<>();   // ordenada por sectorId
    private final ArrayList<Alerta> alertas = new ArrayList<>();

    public ControladorCampo(int controladorId, String identificador, double volumenAguaTotal) {
        this.controladorId = controladorId;
        this.identificador = identificador;
        this.volumenAguaTotal = volumenAguaTotal;
    }

    /** Agrega un sector respetando el orden creciente por identificador (necesario para la búsqueda binaria). */
    public void agregarSector(Sector sector) throws RiegoException {
        if (!sectores.isEmpty() && sector.getSectorId() <= sectores.get(sectores.size() - 1).getSectorId()) {
            throw new RiegoException("Ya existe un sector con el identificador " + sector.getSectorId() + ".");
        }
        sectores.add(sector);
    }

    /** Búsqueda BINARIA por identificador (la lista está ordenada por sectorId). */
    public Sector buscarSectorPorId(int id) throws RiegoException {
        int bajo = 0;
        int alto = sectores.size() - 1;
        while (bajo <= alto) {
            int medio = (bajo + alto) / 2;
            int idMedio = sectores.get(medio).getSectorId();
            if (idMedio == id) {
                return sectores.get(medio);
            } else if (idMedio < id) {
                bajo = medio + 1;
            } else {
                alto = medio - 1;
            }
        }
        throw new RiegoException("No existe un sector con el identificador " + id + ".");
    }

    /**
     * Devuelve una copia de la lista ordenada por humedad actual, de menor a mayor (los sectores más secos primero),
     * con ordenación por INSERCIÓN. Los sectores sin lectura quedan al final.
     */
    public ArrayList<Sector> sectoresOrdenadosPorHumedad() {
        ArrayList<Sector> orden = new ArrayList<>(sectores);
        for (int i = 1; i < orden.size(); i++) {
            Sector actual = orden.get(i);
            int j = i - 1;
            while (j >= 0 && esMasSecoQue(actual, orden.get(j))) {
                orden.set(j + 1, orden.get(j));
                j--;
            }
            orden.set(j + 1, actual);
        }
        return orden;
    }

    private boolean esMasSecoQue(Sector a, Sector b) {
        Double ha = a.getHumedadActual();
        Double hb = b.getHumedadActual();
        if (ha == null) return false;
        if (hb == null) return true;
        return ha < hb;
    }

    public void registrarConsumo(double litros) { volumenAguaTotal += litros; }

    public void agregarAlerta(Alerta alerta) { alertas.add(alerta); }

    public int getControladorId() { return controladorId; }
    public String getIdentificador() { return identificador; }
    public double getVolumenAguaTotal() { return volumenAguaTotal; }
    public ArrayList<Sector> getSectores() { return sectores; }
    public ArrayList<Alerta> getAlertas() { return alertas; }
}
