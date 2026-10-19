package cl.grupopro.dotacion.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte cualquier excepcion de los endpoints /api/* en un JSON legible,
 * en vez de un 500 opaco. Asi el dashboard puede mostrar la causa real
 * (token invalido, sin permisos, error de red, parseo, etc.).
 */
@ControllerAdvice
public class ErrorApiHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorApiHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manejar(Exception e) {
        log.error("Error en endpoint: {}", e.getMessage(), e);

        // Busca el mensaje mas util en la cadena de causas.
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", true);
        m.put("tipo", e.getClass().getSimpleName());
        m.put("mensaje", e.getMessage());
        if (causa != e) {
            m.put("causa", causa.getClass().getSimpleName() + ": " + causa.getMessage());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(m);
    }
}
