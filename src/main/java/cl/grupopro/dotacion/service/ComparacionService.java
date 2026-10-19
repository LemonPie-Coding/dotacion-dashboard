package cl.grupopro.dotacion.service;

import cl.grupopro.dotacion.service.DotacionService.Dotacion;
import cl.grupopro.dotacion.service.DotacionService.Fila;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Compara la dotacion entre dos fechas y arma, por cada dimension
 * (razon social, centro de costo, cargo), los conteos de A y B
 * segmentados por total / sexo / nacionalidad, mas el delta B - A.
 *
 * Reutiliza DotacionService.calcular() para cada fecha.
 */
@Service
public class ComparacionService {

    private final DotacionService dotacion;

    public ComparacionService(DotacionService dotacion) {
        this.dotacion = dotacion;
    }

    public Comparacion comparar(LocalDate fechaA, LocalDate fechaB) {
        Dotacion a = dotacion.calcular(fechaA, false);
        Dotacion b = dotacion.calcular(fechaB, false);

        Comparacion c = new Comparacion();
        c.fechaA = a.fecha;
        c.fechaB = b.fecha;
        c.totalA = a.total;
        c.totalB = b.total;

        c.razonSocial = construir(a.filas, b.filas, f -> f.razonSocial);
        c.centroCosto = construir(a.filas, b.filas, f -> f.centroCosto);
        c.cargo       = construir(a.filas, b.filas, f -> f.cargo);

        // Totales por segmento (para las tarjetas resumen de la pagina)
        c.resumen = new Resumen();
        c.resumen.sexoA = Map.of("M", a.masculino, "F", a.femenino, "SD", a.sexoSinDato);
        c.resumen.sexoB = Map.of("M", b.masculino, "F", b.femenino, "SD", b.sexoSinDato);
        c.resumen.nacA = Map.of("CL", a.chilenos, "EXT", a.extranjeros, "SD", a.nacionalidadSinDato);
        c.resumen.nacB = Map.of("CL", b.chilenos, "EXT", b.extranjeros, "SD", b.nacionalidadSinDato);
        return c;
    }

    // ------------------------------------------------------------------

    private interface Clave { String de(Fila f); }

    private List<Grupo> construir(List<Fila> filasA, List<Fila> filasB, Clave clave) {
        // acumula por categoria
        Map<String, Conteo> mapa = new TreeMap<>();
        acumular(mapa, filasA, clave, true);
        acumular(mapa, filasB, clave, false);

        List<Grupo> salida = new ArrayList<>();
        for (Map.Entry<String, Conteo> e : mapa.entrySet()) {
            Conteo ct = e.getValue();
            Grupo g = new Grupo();
            g.categoria = e.getKey();

            g.totalA = ct.totalA;  g.totalB = ct.totalB;
            g.mA = ct.mA; g.fA = ct.fA; g.sdSexoA = ct.sdSexoA;
            g.mB = ct.mB; g.fB = ct.fB; g.sdSexoB = ct.sdSexoB;
            g.clA = ct.clA; g.extA = ct.extA; g.sdNacA = ct.sdNacA;
            g.clB = ct.clB; g.extB = ct.extB; g.sdNacB = ct.sdNacB;

            g.deltaTotal = ct.totalB - ct.totalA;
            salida.add(g);
        }
        // ordena por magnitud del cambio (desc) para que lo mas relevante quede arriba
        salida.sort((x, y) -> Integer.compare(Math.abs(y.deltaTotal), Math.abs(x.deltaTotal)));
        return salida;
    }

    private void acumular(Map<String, Conteo> mapa, List<Fila> filas, Clave clave, boolean esA) {
        for (Fila f : filas) {
            String k = clave.de(f);
            if (k == null || k.isBlank()) k = "(sin dato)";
            Conteo ct = mapa.computeIfAbsent(k, x -> new Conteo());
            if (esA) {
                ct.totalA++;
                if ("M".equals(f.sexo)) ct.mA++; else if ("F".equals(f.sexo)) ct.fA++; else ct.sdSexoA++;
                if (f.extranjero) ct.extA++; else if ("SD".equals(f.nacionalidad)) ct.sdNacA++; else ct.clA++;
            } else {
                ct.totalB++;
                if ("M".equals(f.sexo)) ct.mB++; else if ("F".equals(f.sexo)) ct.fB++; else ct.sdSexoB++;
                if (f.extranjero) ct.extB++; else if ("SD".equals(f.nacionalidad)) ct.sdNacB++; else ct.clB++;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Modelos de salida (JSON)
    // ------------------------------------------------------------------

    private static class Conteo {
        int totalA, totalB;
        int mA, fA, sdSexoA, mB, fB, sdSexoB;
        int clA, extA, sdNacA, clB, extB, sdNacB;
    }

    public static class Comparacion {
        public String fechaA, fechaB;
        public int totalA, totalB;
        public List<Grupo> razonSocial;
        public List<Grupo> centroCosto;
        public List<Grupo> cargo;
        public Resumen resumen;
    }

    public static class Resumen {
        public Map<String, Integer> sexoA, sexoB;
        public Map<String, Integer> nacA, nacB;
    }

    public static class Grupo {
        public String categoria;
        public int totalA, totalB, deltaTotal;
        // sexo
        public int mA, fA, sdSexoA, mB, fB, sdSexoB;
        // nacionalidad
        public int clA, extA, sdNacA, clB, extB, sdNacB;
    }
}
