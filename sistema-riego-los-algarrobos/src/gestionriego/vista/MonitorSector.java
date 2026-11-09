package gestionriego.vista;

import gestionriego.controlador.GestorRiego;
import gestionriego.modelo.Alerta;
import gestionriego.modelo.RegistroRiego;
import gestionriego.modelo.RiegoException;
import gestionriego.modelo.Sector;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * VISTA del patrón MVC (clase de interfaz del análisis). Muestra el menú, pide los datos por teclado y presenta los
 * resultados. No contiene reglas de negocio ni SQL: delega todo en GestorRiego (Controlador).
 */
public class MonitorSector {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA_ENTRADA = DateTimeFormatter.ofPattern("d/M/uuuu");

    /** Opciones del menú guardadas en un ARREGLO: el número que ve el usuario es la posición + 1. */
    private static final String[] OPCIONES = {
            "Agregar sector de cultivo",
            "Listar sectores (más secos primero)",
            "Consultar estado de un sector",
            "Registrar lectura de humedad (riego automático)",
            "Recibir lecturas de sensores (simulado)",
            "Activar riego manual",
            "Detener riego manual",
            "Consumo de agua por sector y período",
            "Ver y atender alertas",
            "Estadísticas de humedad de un sector",
            "Verificar comunicación de sensores",
            "Exportar reporte de consumo a archivo de texto",
            "Ver el último reporte exportado"
    };

    private final Scanner scanner = new Scanner(System.in);
    private final GestorRiego gestor;

    public MonitorSector(GestorRiego gestor) {
        this.gestor = gestor;
    }

    /** Bucle principal: se repite hasta que el usuario elige «Salir». */
    public void iniciar() {
        for (String aviso : gestor.getAdvertencias()) {
            System.out.println("[Aviso] " + aviso);
        }
        boolean salir = false;
        while (!salir) {
            mostrarMenu();
            int opcion = leerEntero("Elige una opción: ");
            try {
                switch (opcion) {
                    case 1: agregarSector(); break;
                    case 2: listarSectores(); break;
                    case 3: consultarEstado(); break;
                    case 4: registrarLecturaManual(); break;
                    case 5: recibirLecturas(); break;
                    case 6: activarRiegoManual(); break;
                    case 7: detenerRiegoManual(); break;
                    case 8: mostrarConsumo(); break;
                    case 9: verAlertas(); break;
                    case 10: mostrarEstadisticas(); break;
                    case 11: verificarComunicacion(); break;
                    case 12: exportarReporte(); break;
                    case 13: verUltimoReporte(); break;
                    case 0:
                        salir = true;
                        System.out.println("Saliendo del sistema. ¡Hasta luego!");
                        break;
                    default:
                        System.out.println("Opción inexistente. Elija un número entre 0 y " + OPCIONES.length + ".");
                }
            } catch (RiegoException e) {
                // reglas del negocio y errores de la base de datos (PersistenciaException es una RiegoException)
                System.out.println("No se pudo completar la operación: " + e.getMessage());
            }
        }
    }

    private void mostrarMenu() {
        System.out.println();
        System.out.println("==============================================");
        System.out.println(" MENÚ PRINCIPAL - Riego Los Algarrobos");
        System.out.println("==============================================");
        for (int i = 0; i < OPCIONES.length; i++) {
            System.out.printf("%2d. %s%n", i + 1, OPCIONES[i]);
        }
        System.out.println(" 0. Salir");
        System.out.println("----------------------------------------------");
    }

    // ---------------------------------------------------------------- opciones del menú

    private void agregarSector() throws RiegoException {
        System.out.print("Nombre del sector: ");
        String nombre = scanner.nextLine();
        double superficie = leerDecimal("Superficie (hectáreas): ");
        System.out.print("Tipo de cultivo: ");
        String cultivo = scanner.nextLine();
        double caudal = leerDecimal("Caudal de la válvula (litros/minuto): ");
        double minimo = leerDecimal("Umbral mínimo de humedad (%): ");
        double maximo = leerDecimal("Umbral máximo de humedad (%): ");
        Sector sector = gestor.agregarSector(nombre, superficie, cultivo, caudal, minimo, maximo);
        System.out.println("Sector \"" + sector.getNombre() + "\" guardado en la base de datos con el identificador "
                + sector.getSectorId() + ".");
    }

    private void listarSectores() {
        ArrayList<Sector> orden = gestor.sectoresOrdenadosPorHumedad();
        if (orden.isEmpty()) {
            System.out.println("No hay sectores cargados.");
            return;
        }
        System.out.println();
        System.out.printf("%-4s %-22s %-12s %-10s %-9s%n", "ID", "Sector", "Cultivo", "Humedad", "Válvula");
        for (Sector s : orden) {
            System.out.printf("%-4d %-22s %-12s %-10s %-9s%n", s.getSectorId(), s.getNombre(), s.getTipoCultivo(),
                    formatearHumedad(s.getHumedadActual()), s.getValvula().getEstado());
        }
    }

    private void consultarEstado() throws RiegoException {
        Sector s = buscarSector();
        System.out.println();
        System.out.println("Sector " + s.getSectorId() + ": " + s.getNombre() + " (" + s.getTipoCultivo() + ", " + s.getSuperficie() + " ha)");
        System.out.println("  Humedad actual : " + formatearHumedad(s.getHumedadActual()));
        if (s.getFechaUltimaLectura() != null) {
            System.out.println("  Última lectura : " + s.getFechaUltimaLectura().format(FECHA_HORA));
        }
        System.out.println("  Umbrales       : mínimo " + s.getUmbral().getHumedadMinima() + " % / máximo " + s.getUmbral().getHumedadMaxima() + " %");
        System.out.println("  Válvula        : " + s.getValvula().getEstado() + " (caudal " + s.getValvula().getCaudal() + " L/min)");
    }

    private void registrarLecturaManual() throws RiegoException {
        Sector s = buscarSector();
        double humedad = leerDecimal("Humedad leída (%): ");
        int minutos = 0;
        if (gestor.cortaraRiego(s, humedad)) {
            minutos = leerEntero("Minutos que estuvo regando (simulado): ");
        }
        System.out.println(gestor.procesarLectura(s, humedad, minutos));
    }

    private void recibirLecturas() throws RiegoException {
        Sector s = buscarSector();
        System.out.println("Lecturas recibidas del sector " + s.getNombre() + ":");
        for (String linea : gestor.recibirLecturas(s)) {
            System.out.println("  " + linea);
        }
    }

    private void activarRiegoManual() throws RiegoException {
        Sector s = buscarSector();
        gestor.activarManual(s);
        System.out.println("Riego manual ACTIVADO en " + s.getNombre() + " (registrado en la base de datos).");
    }

    private void detenerRiegoManual() throws RiegoException {
        Sector s = buscarSector();
        if (!s.getValvula().estaActiva()) {
            throw new RiegoException("La válvula del sector " + s.getNombre() + " ya está detenida.");
        }
        int minutos = leerEntero("Minutos que estuvo regando (simulado): ");
        RegistroRiego r = gestor.detenerManual(s, minutos);
        System.out.printf("Riego DETENIDO en %s. Volumen suministrado: %.1f L.%n", s.getNombre(), r.getVolumenLitros());
    }

    private void mostrarConsumo() throws RiegoException {
        LocalDate desde = leerFechaOpcional("Desde (dd/mm/aaaa, Enter = sin límite): ");
        LocalDate hasta = leerFechaOpcional("Hasta (dd/mm/aaaa, Enter = sin límite): ");
        double[][] matriz = gestor.consumo(desde, hasta);
        ArrayList<Sector> sectores = gestor.getControlador().getSectores();
        System.out.println();
        System.out.printf("%-4s %-22s %15s %12s %12s%n", "ID", "Sector", "Automático (L)", "Manual (L)", "Total (L)");
        double total = 0;
        for (int i = 0; i < sectores.size(); i++) {
            double suma = matriz[i][0] + matriz[i][1];
            System.out.printf("%-4d %-22s %15.1f %12.1f %12.1f%n", sectores.get(i).getSectorId(),
                    sectores.get(i).getNombre(), matriz[i][0], matriz[i][1], suma);
            total += suma;
        }
        System.out.printf("Total del período: %.1f L%n", total);
        System.out.printf("Volumen acumulado por el controlador %s desde la puesta en marcha: %.1f L%n",
                gestor.getControlador().getIdentificador(), gestor.getControlador().getVolumenAguaTotal());
    }

    private void verAlertas() throws RiegoException {
        ArrayList<Alerta> alertas = gestor.getControlador().getAlertas();
        if (alertas.isEmpty()) {
            System.out.println("No hay alertas registradas.");
            return;
        }
        for (Alerta a : alertas) {
            System.out.printf("#%d [%s] %s - %s: %s (%s)%n", a.getAlertaId(), a.getFecha().format(FECHA_HORA),
                    a.getSector().getNombre(), a.getTipo(), a.getDescripcion(), a.isAtendida() ? "atendida" : "pendiente");
        }
        int id = leerEntero("Número de alerta a marcar como atendida (0 = volver): ");
        if (id != 0) {
            gestor.atenderAlerta(id);
            System.out.println("Alerta " + id + " marcada como atendida en la base de datos.");
        }
    }

    private void mostrarEstadisticas() throws RiegoException {
        Sector s = buscarSector();
        double[] e = gestor.estadisticasHumedad(s, 10);
        System.out.printf("Últimas %d lecturas de humedad de %s:%n", (int) e[0], s.getNombre());
        System.out.printf("  Mínima: %.1f %% | Promedio: %.1f %% | Mediana: %.1f %% | Máxima: %.1f %%%n", e[1], e[2], e[3], e[4]);
    }

    private void verificarComunicacion() throws RiegoException {
        System.out.println("Se considera falla si no hubo lecturas en las últimas " + GestorRiego.HORAS_SIN_LECTURA_PARA_ALERTA + " horas:");
        for (String linea : gestor.verificarComunicacion()) {
            System.out.println("  " + linea);
        }
    }

    private void exportarReporte() throws RiegoException {
        LocalDate desde = leerFechaOpcional("Desde (dd/mm/aaaa, Enter = sin límite): ");
        LocalDate hasta = leerFechaOpcional("Hasta (dd/mm/aaaa, Enter = sin límite): ");
        File archivo = gestor.exportarReporteConsumo(desde, hasta);
        System.out.println("Reporte guardado en: " + archivo.getPath());
    }

    private void verUltimoReporte() throws RiegoException {
        File archivo = gestor.ultimoReporte();
        if (archivo == null) {
            System.out.println("Todavía no se exportó ningún reporte (use la opción 12).");
            return;
        }
        System.out.println("Contenido de " + archivo.getName() + ":");
        for (String linea : gestor.leerReporte(archivo)) {
            System.out.println("  " + linea);
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private Sector buscarSector() throws RiegoException {
        return gestor.buscarSector(leerEntero("Identificador del sector: "));
    }

    private String formatearHumedad(Double humedad) {
        return (humedad == null) ? "sin datos" : String.format("%.1f %%", humedad);
    }

    /**
     * Lee un entero. Si el usuario escribe algo que no es un número, nextInt() lanza InputMismatchException: se la
     * captura, se descarta lo escrito y se vuelve a pedir el dato, de modo que el programa no se cae.
     */
    private int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                int numero = scanner.nextInt();
                scanner.nextLine();
                return numero;
            } catch (InputMismatchException e) {
                scanner.nextLine();
                System.out.println("Valor no válido: debe ingresar un número entero.");
            }
        }
    }

    /** Lee un decimal aceptando coma o punto (12,5 o 12.5). */
    private double leerDecimal(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String texto = scanner.nextLine().trim().replace(',', '.');
            try {
                return Double.parseDouble(texto);
            } catch (NumberFormatException e) {
                System.out.println("Valor no válido: debe ingresar un número (por ejemplo 12,5).");
            }
        }
    }

    /** Lee una fecha dd/mm/aaaa; si el usuario solo presiona Enter devuelve null (sin límite). */
    private LocalDate leerFechaOpcional(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String texto = scanner.nextLine().trim();
            if (texto.isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(texto, FECHA_ENTRADA);
            } catch (DateTimeParseException e) {
                System.out.println("Fecha no válida: use el formato dd/mm/aaaa (por ejemplo 20/09/2026).");
            }
        }
    }
}
