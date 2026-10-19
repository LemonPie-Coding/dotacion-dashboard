package cl.grupopro.dotacion;

import cl.grupopro.dotacion.config.TalanaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Dashboard informativo de DOTACION ACTIVA de Grupo Pro.
 *
 * Proyecto independiente: solo consulta /contracts-resumed-paginated/ de Talana
 * para mostrar la dotacion (sexo M/F, nacionalidad, razon social, centro de costo,
 * cargo). NO maneja documentos, firmas ni anomalias.
 */
@SpringBootApplication
@EnableConfigurationProperties(TalanaProperties.class)
public class DotacionDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DotacionDashboardApplication.class, args);
    }
}
