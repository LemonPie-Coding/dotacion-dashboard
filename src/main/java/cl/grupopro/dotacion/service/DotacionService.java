package cl.grupopro.dotacion.service;

import cl.grupopro.dotacion.config.TalanaProperties;
import cl.grupopro.dotacion.dto.ContratoDotacionDto;
import cl.grupopro.dotacion.dto.PagedResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dotacion ACTIVA a una fecha.
 *
 * UNA sola llamada: /contracts-resumed-paginated/?solo-activos=true (o
 * ?active_on=YYYYMMDD). Cada contrato trae "personaDetails" embebido
 * (sexo, nacionalidad). ~1.000 activos => ~1 pagina => unos segundos.
 *
 * ENCODING: se leen BYTES y se decodifican como UTF-8 explicito para evitar el
 * mojibake ("SelecciÃ³n") de interpretar el body como Latin-1.
 *
 * CACHE: la dotacion de hoy se guarda en memoria unos minutos (cache-minutos).
 * forzar=true (boton "Recargar datos") siempre consulta de nuevo.
 */
@Service
public class DotacionService {

    private static final Logger log = LoggerFactory.getLogger(DotacionService.class);

    private final TalanaProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    private volatile Dotacion cache;
    private volatile String cacheClave;     // "hoy" o "YYYY-MM-DD"
    private volatile Instant cacheEn;
    private long ultimaPeticionMs = 0;

    // Modo de operacion: NORMAL usa cache de disco para meses cerrados;
    // EDICION ignora el cache al leer pero lo actualiza con el valor fresco.
    private volatile boolean modoEdicion = false;

    private final java.io.File dirCache;

    public DotacionService(TalanaProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        // carpeta de cache en disco (datos/dotacion-cache/)
        this.dirCache = new java.io.File("datos/dotacion-cache");
        if (!dirCache.exists() && !dirCache.mkdirs()) {
            log.warn("No se pudo crear la carpeta de cache {}", dirCache.getAbsolutePath());
        }
    }

    public boolean isModoEdicion() { return modoEdicion; }
    public void setModoEdicion(boolean v) {
        this.modoEdicion = v;
        log.info("Modo cambiado a {}", v ? "EDICION (siempre fresco)" : "NORMAL (cache de disco)");
    }

    /**
     * Dotacion a una fecha, con cache persistente por mes.
     *
     * - Un mes YA CERRADO (su mes terminó) es historico: se guarda en disco la
     *   primera vez y de ahi en adelante se lee del disco (instantaneo), salvo en
     *   modo edicion. El mes EN CURSO siempre se consulta fresco.
     * - Modo EDICION: ignora el cache de disco al leer, pero guarda el resultado
     *   fresco (util si se corrigio un mes pasado en Talana).
     *
     * @param fecha  fecha de corte; null => hoy.
     * @param forzar fuerza consulta a Talana (boton "Recargar datos").
     */
    public synchronized Dotacion calcular(LocalDate fecha, boolean forzar) {
        LocalDate corte = (fecha == null) ? LocalDate.now() : fecha;
        boolean mesCerrado = esMesCerrado(corte);

        // En modo normal y mes cerrado, intenta leer del disco (a menos que se fuerce).
        if (!forzar && !modoEdicion && mesCerrado) {
            Dotacion enDisco = leerDeDisco(corte);
            if (enDisco != null) {
                log.info("Dotacion {} servida desde cache de disco", corte);
                return enDisco;
            }
        }

        Dotacion d = calcularDesdeApi(fecha);

        // Guarda en disco los meses cerrados (en normal la primera vez; en edicion
        // siempre, para actualizar correcciones de meses pasados).
        if (mesCerrado) guardarEnDisco(corte, d);
        return d;
    }

    /** Cálculo real contra la API de Talana (sin tocar el cache de disco). */
    private Dotacion calcularDesdeApi(LocalDate fecha) {
        String clave = (fecha == null) ? "hoy" : fecha.toString();
        long t0 = System.currentTimeMillis();
        LocalDate corte = (fecha == null) ? LocalDate.now() : fecha;
        List<ContratoDotacionDto> brutos = traerActivos(fecha);
        int leidos = brutos.size();

        // VALIDACION DE VIGENCIA (no confiar solo en el filtro de la API).
        // La API deja colar contratos cuya fecha de termino es ANTERIOR al corte
        // (active_on / type_status_search no son fiables para esto). Se revalida
        // con desde/hasta del propio contrato:
        //   activo a la fecha  <=>  desde <= corte  Y  (hasta vacio  O  hasta >= corte)
        List<ContratoDotacionDto> contratos = new ArrayList<>();
        int descartados = 0;
        for (ContratoDotacionDto c : brutos) {
            if (vigenteEn(c, corte)) contratos.add(c);
            else descartados++;
        }

        // Dedup por RUT: una persona con varios contratos vigentes cuenta una vez.
        // Desempate: gana el contrato que mejor representa la situacion actual, para
        // que el centro de costo / cargo / razon social mostrados sean los del
        // contrato VIGENTE y no los de uno anterior ya reemplazado.
        Map<String, ContratoDotacionDto> porRut = new LinkedHashMap<>();
        for (ContratoDotacionDto c : contratos) {
            String rut = c.rut();
            if (rut.isEmpty()) rut = "ID:" + c.id();
            ContratoDotacionDto actual = porRut.get(rut);
            if (actual == null || ganaContrato(c, actual)) {
                porRut.put(rut, c);
            }
        }
        List<ContratoDotacionDto> personas = new ArrayList<>(porRut.values());

        Dotacion d = new Dotacion();
        d.fecha = corte.toString();
        d.generadoEn = Instant.now().toString();
        d.total = personas.size();
        d.contratosLeidos = leidos;
        d.contratosDescartados = descartados;

        for (ContratoDotacionDto p : personas) {
            switch (p.sexo()) {
                case "M" -> d.masculino++;
                case "F" -> d.femenino++;
                default -> d.sexoSinDato++;
            }
            if (!p.tieneNacionalidad()) d.nacionalidadSinDato++;
            else if (p.esExtranjero()) d.extranjeros++;
            else d.chilenos++;

            inc(d.porRazonSocial, blanko(p.nombreRazonSocial()));
            inc(d.porCentroCosto, blanko(p.nombreCentroCosto()));
            inc(d.porCargo, blanko(p.nombreCargo()));

            Fila f = new Fila();
            f.rut = p.rut();
            f.nombre = p.nombrePersona();
            f.razonSocial = blanko(p.nombreRazonSocial());
            f.centroCosto = blanko(p.nombreCentroCosto());
            f.cargo = blanko(p.nombreCargo());
            f.sexo = p.sexo().isEmpty() ? "SD" : p.sexo();
            f.nacionalidad = p.nacionalidad().isEmpty() ? "SD" : p.nacionalidad();
            f.extranjero = p.esExtranjero();
            f.codigo = p.codigo() == null ? "" : p.codigo().trim();
            f.desde = p.desde() == null ? "" : p.desde().trim();
            f.hasta = p.hasta() == null ? "" : p.hasta().trim();
            d.filas.add(f);
        }

        d.segundos = (System.currentTimeMillis() - t0) / 1000;
        log.info("Dotacion {}: {} personas ({} contratos leidos, {} descartados por vigencia) en {}s. M={} F={} ext={}",
                clave, d.total, d.contratosLeidos, d.contratosDescartados, d.segundos,
                d.masculino, d.femenino, d.extranjeros);
        return d;
    }

    // ==================================================================
    //  Cache persistente en disco por fecha de corte
    // ==================================================================

    /** Un mes esta "cerrado" si ya termino (el corte no cae en el mes en curso). */
    private boolean esMesCerrado(LocalDate corte) {
        LocalDate hoy = LocalDate.now();
        return corte.getYear() < hoy.getYear()
                || (corte.getYear() == hoy.getYear() && corte.getMonthValue() < hoy.getMonthValue());
    }

    private java.io.File archivoDe(LocalDate corte) {
        return new java.io.File(dirCache, corte.toString() + ".json");
    }

    private Dotacion leerDeDisco(LocalDate corte) {
        java.io.File f = archivoDe(corte);
        if (!f.exists()) return null;
        try {
            return mapper.readValue(f, Dotacion.class);
        } catch (Exception e) {
            log.warn("No se pudo leer cache {}: {}", f.getName(), e.getMessage());
            return null;
        }
    }

    private void guardarEnDisco(LocalDate corte, Dotacion d) {
        try {
            mapper.writeValue(archivoDe(corte), d);
        } catch (Exception e) {
            log.warn("No se pudo guardar cache {}: {}", corte, e.getMessage());
        }
    }

    // ==================================================================

    private List<ContratoDotacionDto> traerActivos(LocalDate fecha) {
        String filtro = (fecha == null)
                ? "solo-activos=true"
                : "active_on=" + fecha.format(DateTimeFormatter.BASIC_ISO_DATE);
        String url = props.getBaseUrl() + "/contracts-resumed-paginated/?" + filtro + "&page_size=1000";

        List<ContratoDotacionDto> acumulado = new ArrayList<>();
        int pagina = 0;
        while (url != null && pagina < props.getMaxPaginas()) {
            String body = get(url);
            PagedResponse<ContratoDotacionDto> page;
            try {
                page = mapper.readValue(body, new TypeReference<PagedResponse<ContratoDotacionDto>>() {});
            } catch (Exception e) {
                throw new RuntimeException("No se pudo parsear la dotacion desde " + url, e);
            }
            acumulado.addAll(page.resultsOrEmpty());
            pagina++;
            url = page.next();
        }
        return acumulado;
    }

    /** GET con reintentos simples; lee bytes y decodifica UTF-8 explicito. */
    private String get(String url) {
        String token = props.getToken();
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Falta el token. Define la variable de entorno TALANA_TOKEN.");
        }
        respetarRateLimit();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token.trim())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        int intentos = 3;
        RuntimeException ultimo = null;
        for (int i = 1; i <= intentos; i++) {
            try {
                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                int sc = resp.statusCode();
                String body = new String(resp.body(), StandardCharsets.UTF_8);
                if (sc == 200) return body;
                if (sc == 502 || sc == 503 || sc == 504 || sc == 429) {
                    ultimo = new RuntimeException("HTTP " + sc + " transitorio de Talana");
                    dormir(2000L * i);
                    continue;
                }
                throw new RuntimeException("HTTP " + sc + " en " + url + " -> " + body.trim());
            } catch (java.io.IOException e) {
                ultimo = new RuntimeException("Error de red: " + e.getMessage(), e);
                dormir(2000L * i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Peticion interrumpida", e);
            }
        }
        throw (ultimo != null ? ultimo : new RuntimeException("Fallo la peticion a " + url));
    }

    // ==================================================================
    //  Diagnostico: prueba el token/red sin parsear, para explicar fallas.
    // ==================================================================
    public Map<String, Object> diagnostico() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("baseUrl", props.getBaseUrl());
        String token = props.getToken();
        r.put("tokenPresente", token != null && !token.isBlank());
        r.put("tokenLargo", token == null ? 0 : token.trim().length());

        if (token == null || token.isBlank()) {
            r.put("ok", false);
            r.put("detalle", "Falta TALANA_TOKEN en el entorno de la app.");
            return r;
        }

        String url = props.getBaseUrl() + "/contracts-resumed-paginated/?solo-activos=true&page_size=1";
        try {
            respetarRateLimit();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token " + token.trim())
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int sc = resp.statusCode();
            String body = new String(resp.body(), StandardCharsets.UTF_8);
            r.put("httpStatus", sc);
            r.put("ok", sc == 200);
            if (sc == 200) {
                r.put("detalle", "Conexion y token OK.");
                r.put("muestra", body.length() > 400 ? body.substring(0, 400) + "..." : body);
            } else {
                r.put("detalle", "Talana respondio " + sc + ": " + body.trim());
            }
        } catch (Exception e) {
            r.put("ok", false);
            r.put("detalle", "Error de red/llamada: " + e.getMessage());
        }
        return r;
    }

    private void respetarRateLimit() {
        long delay = props.getDelayEntrePeticionesMs();
        if (delay <= 0) return;
        long t = System.currentTimeMillis() - ultimaPeticionMs;
        if (t < delay) dormir(delay - t);
        ultimaPeticionMs = System.currentTimeMillis();
    }

    private static void dormir(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void inc(Map<String, Integer> m, String k) { m.merge(k, 1, Integer::sum); }
    private static String blanko(String s) { return (s == null || s.isBlank()) ? "(sin dato)" : s; }

    /**
     * Un contrato esta vigente a la fecha de corte si:
     *   desde <= corte   Y   (hasta vacio/indefinido  O  hasta >= corte)
     * Las fechas vienen en ISO (YYYY-MM-DD) desde la API. Si alguna no parsea,
     * se es permisivo con ese campo (no se descarta por un dato malformado).
     */
    static boolean vigenteEn(ContratoDotacionDto c, LocalDate corte) {
        LocalDate desde = parseIso(c.desde());
        LocalDate hasta = parseIso(c.hasta());
        if (desde != null && desde.isAfter(corte)) return false;       // aun no empezaba
        if (hasta != null && hasta.isBefore(corte)) return false;      // ya habia terminado
        return true;                                                   // hasta null = indefinido
    }

    /**
     * Desempate entre dos contratos vigentes del mismo RUT. Gana el que mejor
     * representa la situacion actual de la persona:
     *   1) el INDEFINIDO (hasta vacio) sobre uno con termino;
     *   2) si ambos igual, el de inicio (desde) mas reciente;
     *   3) si aun empatan, el id mayor (creado despues).
     * @return true si 'cand' debe reemplazar a 'actual'.
     */
    static boolean ganaContrato(ContratoDotacionDto cand, ContratoDotacionDto actual) {
        boolean candIndef = esVacio(cand.hasta());
        boolean actIndef   = esVacio(actual.hasta());
        if (candIndef != actIndef) return candIndef;   // el indefinido gana

        LocalDate dCand = parseIso(cand.desde());
        LocalDate dAct  = parseIso(actual.desde());
        if (dCand != null && dAct != null && !dCand.isEqual(dAct)) {
            return dCand.isAfter(dAct);                 // el de inicio mas reciente
        }
        long idCand = cand.id() == null ? 0 : cand.id();
        long idAct  = actual.id() == null ? 0 : actual.id();
        return idCand > idAct;                          // el creado despues
    }

    private static boolean esVacio(String s) { return s == null || s.isBlank(); }

    private static LocalDate parseIso(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // toma solo la parte YYYY-MM-DD por si viniera con hora
            return LocalDate.parse(s.trim().substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================================================================
    //  Modelo de salida (se serializa a JSON para el dashboard)
    // ==================================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dotacion {
        public String fecha;
        public String generadoEn;
        public long segundos;
        public int total;
        public int contratosLeidos;
        public int contratosDescartados;   // vencidos que la API dejo colar y se filtraron

        public int masculino;
        public int femenino;
        public int sexoSinDato;

        public int chilenos;
        public int extranjeros;
        public int nacionalidadSinDato;

        public Map<String, Integer> porRazonSocial = new TreeMap<>();
        public Map<String, Integer> porCentroCosto = new TreeMap<>();
        public Map<String, Integer> porCargo = new TreeMap<>();

        public List<Fila> filas = new ArrayList<>();

        /** % de extranjeros sobre el total CON nacionalidad conocida. */
        public double pctExtranjeros() {
            int base = chilenos + extranjeros;
            return base == 0 ? 0 : (extranjeros * 100.0) / base;
        }
    }

    public static class Fila {
        public String rut;
        public String nombre;
        public String razonSocial;
        public String centroCosto;
        public String cargo;
        public String sexo;          // M / F / SD
        public String nacionalidad;  // CL / VE / ... / SD
        public boolean extranjero;
        public String codigo;        // codigo del contrato
        public String desde;         // fecha ingreso (ISO)
        public String hasta;         // fecha termino (ISO) o "" si indefinido
    }
}
