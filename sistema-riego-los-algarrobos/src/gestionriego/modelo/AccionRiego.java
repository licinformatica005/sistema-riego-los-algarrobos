package gestionriego.modelo;

/** Resultado de comparar una lectura de humedad contra el umbral configurado de un sector. */
public enum AccionRiego {
    ACTIVAR,   // humedad por debajo del umbral mínimo
    MANTENER,  // humedad dentro del rango configurado
    DETENER    // humedad igual o superior al umbral máximo
}
