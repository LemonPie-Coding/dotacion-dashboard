package cl.grupopro.dotacion.web;

import cl.grupopro.dotacion.service.DotacionService;
import cl.grupopro.dotacion.service.ComparacionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Dashboard de DOTACION ACTIVA.
 *
 *   GET /                              -> pagina HTML (tarjetas + filtros)
 *   GET /dotacion                      -> igual que "/"
 *   GET /api/dotacion                  -> dotacion de hoy (JSON)
 *   GET /api/dotacion?fecha=2026-10-08 -> dotacion a una fecha (JSON)
 *   GET /api/dotacion?forzar=true      -> ignora cache y reconsulta Talana
 *   GET /api/diagnostico               -> prueba token/red y explica fallas
 */
@RestController
public class DotacionController {

    private final DotacionService dotacion;
    private final ComparacionService comparacion;

    public DotacionController(DotacionService dotacion, ComparacionService comparacion) {
        this.dotacion = dotacion;
        this.comparacion = comparacion;
    }

    @GetMapping(value = {"/", "/dotacion"}, produces = MediaType.TEXT_HTML_VALUE)
    public String pagina() throws Exception {
        ClassPathResource r = new ClassPathResource("dotacion.html");
        return StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/comparar", produces = MediaType.TEXT_HTML_VALUE)
    public String paginaComparar() throws Exception {
        ClassPathResource r = new ClassPathResource("comparar.html");
        return StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/api/dotacion", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public DotacionService.Dotacion api(@RequestParam(required = false) String fecha,
                                        @RequestParam(required = false, defaultValue = "false") boolean forzar) {
        LocalDate f = null;
        if (fecha != null && !fecha.isBlank()) {
            String s = fecha.trim();
            f = s.contains("-") ? LocalDate.parse(s) : LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE);
        }
        return dotacion.calcular(f, forzar);
    }

    @GetMapping(value = "/api/diagnostico", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> diagnostico() {
        return dotacion.diagnostico();
    }

    /** Estado del modo actual (normal/edicion). */
    @GetMapping(value = "/api/modo", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> modo() {
        return Map.of("edicion", dotacion.isModoEdicion());
    }

    /** Cambia el modo. ?edicion=true => modo edicion (siempre fresco). */
    @org.springframework.web.bind.annotation.PostMapping(value = "/api/modo", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> setModo(@RequestParam boolean edicion) {
        dotacion.setModoEdicion(edicion);
        return Map.of("edicion", dotacion.isModoEdicion());
    }

    /** Compara la dotacion entre dos fechas. fechaA/fechaB en YYYY-MM-DD o YYYYMMDD. */
    @GetMapping(value = "/api/comparar", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ComparacionService.Comparacion comparar(@RequestParam String fechaA,
                                                   @RequestParam String fechaB) {
        return comparacion.comparar(parse(fechaA), parse(fechaB));
    }

    private static LocalDate parse(String s) {
        s = s.trim();
        return s.contains("-") ? LocalDate.parse(s) : LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE);
    }
}
