package cl.grupopro.dotacion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Envoltorio de las respuestas paginadas de Talana. Se pagina siguiendo "next". */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagedResponse<T>(
        Integer count,
        String next,
        String previous,
        List<T> results
) {
    public List<T> resultsOrEmpty() {
        return results == null ? List.of() : results;
    }
}
