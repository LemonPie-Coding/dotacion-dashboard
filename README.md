# Dashboard de Dotación Activa · Grupo Pro

Dashboard informativo de la **dotación activa** a partir de la API de Talana.
Proyecto **independiente**: solo lee contratos para mostrar la dotación. No maneja
documentos, firmas ni anomalías.

## Qué muestra

- **Tarjetas KPI**: dotación activa (personas únicas), masculino, femenino (con %),
  % de extranjeros y chilenos.
- **Filtros en vivo**: razón social, centro de costo, cargo, sexo y nacionalidad
  (chilena / extranjera). Recalculan tarjetas, gráficos y tabla sin recargar.
- **Gráficos**: dona por sexo y por nacionalidad; barras por razón social,
  top 15 centros de costo y top 15 cargos.
- **Selector de fecha**: consulta la dotación vigente a una fecha (`active_on`).
- **Tabla** ordenable con el detalle filtrado.

## De dónde salen los datos

Una sola llamada a `GET /contracts-resumed-paginated/`:

- Hoy: `?solo-activos=true`
- A una fecha: `?active_on=YYYYMMDD`

Cada contrato trae `personaDetails` embebido, de donde salen:

- `personaDetails.sexo` → `"M"` / `"F"`
- `personaDetails.nacionalidad` → código ISO (`"CL"` = chilena; cualquier otro = extranjera)

**Dedup por RUT**: si una persona tiene más de un contrato activo, cuenta una sola vez.
**% extranjeros**: calculado sobre quienes tienen nacionalidad conocida (los "sin dato"
se cuentan aparte, no inflan el porcentaje).

## Cómo correrlo

El token se lee de la variable de entorno `TALANA_TOKEN` (nunca se escribe en el repo).

```powershell
$env:TALANA_TOKEN   = "<tu-token-de-produccion>"
$env:TALANA_BASE_URL = "https://talana.com/es/api"   # opcional; es el valor por defecto
mvn spring-boot:run
```

Abre: **http://localhost:8081/**

> Corre en el puerto **8081** para no chocar con el otro proyecto (8080).

### Endpoints

| Ruta | Descripción |
|---|---|
| `GET /` o `/dotacion` | Página del dashboard |
| `GET /api/dotacion` | Dotación de hoy (JSON) |
| `GET /api/dotacion?fecha=2026-10-08` | Dotación a una fecha |
| `GET /api/dotacion?forzar=true` | Ignora el caché y reconsulta Talana |

## Configuración (`application.yml` / variables de entorno)

| Propiedad | Variable | Default |
|---|---|---|
| `talana.base-url` | `TALANA_BASE_URL` | `https://talana.com/es/api` |
| `talana.token` | `TALANA_TOKEN` | (vacío) |
| `talana.delay-entre-peticiones-ms` | `TALANA_DELAY_MS` | `2000` |
| `talana.cache-minutos` | `DOTACION_CACHE_MINUTOS` | `10` |
| `server.port` | — | `8081` |

## Nota de seguridad

No guardes el token en archivos versionados (ni en `launch.json`). Úsalo como
variable de entorno. Si alguna vez se filtró a un repo, conviene rotarlo con el
SAC de Talana.
