# Sistema de monitoreo y gestión de riego – Los Algarrobos

Seminario de Práctica de Informática (UES21) – Trabajo Práctico N.º 4 (entrega final).

Prototipo en Java con interfaz de consola, organizado con el patrón **MVC** y con persistencia en **MySQL** mediante **JDBC**.

## Contenido del repositorio

| Ruta | Descripción |
|------|-------------|
| `schema_completo.sql` | Script MySQL: crea la base `riego_los_algarrobos`, las 9 tablas del modelo relacional y los datos de prueba |
| `conexion.properties` | URL, usuario y clave de la conexión (en XAMPP: usuario `root` sin clave) |
| `lib/` | Conector JDBC (ver `lib/LEEME.txt`) |
| `src/gestionriego/` | Código Java |
| `reportes/` | Carpeta donde el programa guarda los reportes de consumo en texto |

## Estructura del código (MVC)

| Paquete | Rol | Clases |
|---------|-----|--------|
| `vista` | Vista | `MonitorSector` (menú de consola) |
| `controlador` | Controlador | `GestorRiego` (reglas de riego y casos de uso) |
| `modelo` | Modelo (dominio) | `ControladorCampo`, `Sector`, `Valvula`, `Umbral`, `Sensor` (abstracta), `SensorHumedad`, `SensorClima`, `RegistroRiego`, `Alerta`, `AccionRiego`, `RiegoException` |
| `persistencia` | Modelo (datos) | `RiegoDAO` (interfaz), `RiegoDAOMySQL` (JDBC), `RiegoDAOMemoria` (pruebas), `ConexionBD`, `ArchivoReporte`, `PersistenciaException` |
| `pruebas` | Pruebas | `PruebasGestorRiego` |
| (raíz) | Principal | `GestionRiego` |

## Cómo ejecutarlo

1. **Iniciar MySQL** (por ejemplo, desde el panel de XAMPP).
2. **Importar `schema_completo.sql`** en phpMyAdmin (Importar) o MySQL Workbench. Crea la base y carga los datos.
3. **Crear el proyecto en el IDE** (NetBeans, IntelliJ o Eclipse) con el contenido de `src/` y agregar el conector JDBC de `lib/` como biblioteca. Pasos detallados en `lib/LEEME.txt`.
4. **Revisar `conexion.properties`** (usuario y clave de su MySQL; elegir la URL de la opción A o B).
5. **Ejecutar** la clase `gestionriego.GestionRiego`.

Por terminal (con el conector en `lib/`):

```bash
javac -encoding UTF-8 -d out -cp "lib/*" $(find src -name "*.java")
java -cp "out:lib/*" gestionriego.GestionRiego        # en Windows: -cp "out;lib/*"
```

Las pruebas de la lógica de riego no necesitan MySQL:

```bash
java -cp out gestionriego.pruebas.PruebasGestorRiego
```

## Funciones del menú

1. Agregar sector de cultivo · 2. Listar sectores (más secos primero) · 3. Consultar estado de un sector ·
4. Registrar lectura de humedad (riego automático) · 5. Recibir lecturas de sensores (simulado) ·
6. Activar riego manual · 7. Detener riego manual · 8. Consumo de agua por sector y período ·
9. Ver y atender alertas · 10. Estadísticas de humedad · 11. Verificar comunicación de sensores ·
12. Exportar reporte de consumo a archivo de texto · 13. Ver el último reporte exportado

## Alcance

El controlador de campo se simula (lecturas generadas por el programa o ingresadas por teclado). No incluye comunicación real por 4G/MQTT ni interfaz gráfica Swing.
