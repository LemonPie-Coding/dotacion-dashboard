package cl.grupopro.dotacion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Objeto "personaDetails" embebido en cada contrato de
 * /contracts-resumed-paginated/.
 *
 * Verificado contra respuesta real de produccion (2026-10-08):
 *   "personaDetails": {
 *       "rut": "19730420-0", "nombre": "EVELYN VALERIA",
 *       "apellidoPaterno": "AVELLO", "apellidoMaterno": "ROJAS",
 *       "sexo": "F",                  <- codigo corto "F" / "M"
 *       "fechaNacimiento": "1997-07-04",
 *       "nacionalidad": "CL",         <- codigo ISO de pais ("CL" = Chile)
 *       "email": "..."
 *   }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonaDetailsDto(
        Long id,
        String rut,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno,
        String sexo,
        String fechaNacimiento,
        String nacionalidad,
        String email
) {
    public String nombreCompleto() {
        StringBuilder sb = new StringBuilder();
        if (nombre != null) sb.append(nombre);
        if (apellidoPaterno != null && !apellidoPaterno.isBlank()) sb.append(" ").append(apellidoPaterno);
        if (apellidoMaterno != null && !apellidoMaterno.isBlank()) sb.append(" ").append(apellidoMaterno);
        return sb.toString().trim();
    }
}
