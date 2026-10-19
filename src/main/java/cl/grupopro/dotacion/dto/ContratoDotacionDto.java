package cl.grupopro.dotacion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Vista de un contrato de /contracts-resumed-paginated/ enfocada en la DOTACION.
 * Mapea "personaDetails", de donde salen sexo y nacionalidad.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContratoDotacionDto(
        Long id,
        @JsonProperty("persona_rut") String personaRut,
        Long empleado,
        String codigo,
        String cargo,
        String desde,
        String hasta,
        Boolean finiquitado,
        Boolean activo,
        EmpleadorRazonSocial empleadorRazonSocial,
        CentroCosto centroCosto,
        PersonaDetailsDto personaDetails
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmpleadorRazonSocial(Long id, String rut, String razonSocial) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CentroCosto(Long id, String codigo, String nombre) {}

    private static String nvl(String s) { return s == null ? "" : s.trim(); }

    public String rut() {
        String r = nvl(personaRut);
        if (!r.isEmpty()) return r.toUpperCase();
        return personaDetails == null ? "" : nvl(personaDetails.rut()).toUpperCase();
    }

    public String nombrePersona() {
        return personaDetails == null ? "" : personaDetails.nombreCompleto();
    }

    /** "M", "F" o "" (sin dato). Normalizado a mayuscula. */
    public String sexo() {
        if (personaDetails == null || personaDetails.sexo() == null) return "";
        return personaDetails.sexo().trim().toUpperCase();
    }

    /** Codigo de nacionalidad tal cual viene ("CL", "VE", ...). "" si falta. */
    public String nacionalidad() {
        if (personaDetails == null || personaDetails.nacionalidad() == null) return "";
        return personaDetails.nacionalidad().trim().toUpperCase();
    }

    /** true si la nacionalidad existe y NO es chilena. "" (sin dato) no cuenta como extranjero. */
    public boolean esExtranjero() {
        String n = nacionalidad();
        return !n.isEmpty() && !n.equals("CL");
    }

    public boolean tieneNacionalidad() {
        return !nacionalidad().isEmpty();
    }

    public String nombreRazonSocial() {
        return empleadorRazonSocial == null ? "" : nvl(empleadorRazonSocial.razonSocial());
    }

    public String nombreCentroCosto() {
        return centroCosto == null ? "" : nvl(centroCosto.nombre());
    }

    public String codigoCentroCosto() {
        return centroCosto == null ? "" : nvl(centroCosto.codigo());
    }

    public String nombreCargo() {
        return nvl(cargo);
    }
}
