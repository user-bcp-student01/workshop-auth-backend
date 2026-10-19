# Implementación secuencial H-03 a H-07

Directorio de todos los comandos: raíz de `workshop-auth-backend`.
JDK 21 seleccionado para Maven mediante `JAVA_HOME`. Bases y claves ficticias
aisladas en `target/`; no se ejecutan pruebas contra otros componentes.

## H-03

- Caracterización antes: `mvn verify`, salida 0, 39 pruebas verdes.
- Corrección antes: `mvn "-Dtest=TokenContractTest" test`, salida 1,
  1 fallo esperado: el token legado no tiene tres segmentos JWT.
- Después: `mvn verify`, salida 0, 41 pruebas verdes, gate JaCoCo satisfecho.
  `TokenServiceTest` se añadió junto al servicio nuevo; no se atribuye su
  ejecución anterior a clases que no existían.
- Se conservan campos/errores previos salvo la transición de token autorizada.
  Las pruebas de formato legado se retiraron intencionalmente; el log previo
  conserva su resultado. No se aceptan ambos formatos como alternativa.
- Prueba local de verificación RS256, claims, expiración, clave errónea,
  alteración y claves ausentes/inválidas. Coordinación externa confirmada
  por el usuario; no es ejecución externa por el agente.
- La primera validación detectó un `.class` residual del emisor eliminado;
  se eliminó solo ese bytecode obsoleto y su test compilado, sin excluir
  clases del gate ni modificar umbrales.
- Evidencia: [antes](../../target/items/H-03-characterization-before.log),
  [rojo](../../target/items/H-03-correction-before.log),
  [verde](../../target/items/H-03-after.log), [diff](H-03.diff).

## H-04

- Caracterización: `mvn "-Dtest=TokenContractTest,EndpointContractTest,HealthResourceTest" test`,
  salida 0 (16 pruebas). Corrección antes:
  `mvn "-Dtest=CorsConfigurationTest" test`, salida 1: 4 pruebas, 2 fallos
  por permiso a origen ajeno y método DELETE.
- Después: `mvn verify`, salida 0, 47 pruebas sin fallos; gate JaCoCo >=85%
  por clase satisfecho, incluida `CorsConfiguration`.
- Validación aislada de listas vacías, wildcard, regex y URLs inválidas;
  los mismos validadores se ejecutan al iniciar mediante `@Startup`.
  No se afirma haber iniciado un proceso por cada configuración inválida.
- No se reintrodujo el secreto legado retirado en H-03.
- [Caracterización](../../target/items/H-04-characterization-before.log),
  [rojo](../../target/items/H-04-correction-before.log),
  [verde](../../target/items/H-04-after.log), [diff](H-04.diff).

## H-05

- Antes: `mvn "-Dtest=EndpointContractTest,AuthResourceTest,LegacyDatabaseTest,HealthResourceTest" test`,
  salida 0, 26 pruebas.
- Corrección antes: mismo comando con expectativas aprobadas, salida 1,
  26 pruebas y 5 fallos esperados (SQL 401 y detalles internos).
- Después: `mvn verify`, salida 0, 47 pruebas verdes y gate JaCoCo >=85%
  por clase, incluida la excepción nueva.
- `AuthResourceTest` captura un único registro con causa, con/sin mensaje,
  sin contraseña del request; `LegacyDatabaseTest` exige causa SQLException.
  Las pruebas HTTP comprueban cuerpo exacto de un campo y los fixtures se
  restauran en `finally`. Salud y errores de framework permanecen intactos.
- [Antes](../../target/items/H-05-characterization-before.log),
  [rojo](../../target/items/H-05-correction-before.log),
  [verde](../../target/items/H-05-after.log), [diff](H-05.diff).

## H-06

### Investigación y decisión

Fuentes oficiales consultadas el 2026-10-19:

- [Apache Commons Text: CVE-2022-42889](https://commons.apache.org/proper/commons-text/security.html):
  versiones anteriores a 1.10.0; requiere `StringSubstitutor` con entrada
  no confiable y sin saneamiento. Este backend solo llama `escapeHtml4`;
  no se ha demostrado explotación ni RCE en esta aplicación. Actualizar
  1.9 a la mínima corrección 1.10.0 elimina esa coordenada afectada.
- [GHSA-xrmg-9xc7-83fp / CVE-2026-16308](https://github.com/quarkusio/quarkus/security/advisories/GHSA-xrmg-9xc7-83fp):
  RESTEasy Reactive presente en 3.27.4; parche de la línea 3.27 en 3.27.5.1.
  Requiere endpoint multipart, que no existe en el contrato JSON de este
  backend. Se corrige la dependencia preventivamente, sin afirmar DoS probado.
- [GHSA-qcxp-gm7m-4j5v / CVE-2026-50559](https://github.com/quarkusio/quarkus/security/advisories/GHSA-qcxp-gm7m-4j5v):
  `quarkus-vertx-http` presente, normalización de rutas con políticas HTTP.
  El advisory enumera 3.27.4.1/3.27.5 entre versiones corregidas. Este backend
  no configura políticas por ruta y sus dos endpoints son públicos; no se
  afirma bypass de autenticación demostrado.
- Se contrastaron también los otros avisos del JSON oficial consultado:
  Qute, Spring Web, OIDC y WebSockets Next no aparecen como extensiones usadas;
  los de worker starvation (CVE-2025-66560) y contexto duplicado
  (CVE-2025-49574) ya tienen correcciones anteriores a 3.27.4 en esta línea.
  No se presenta esta consulta acotada como inventario exhaustivo de CVEs.
- [Release oficial Quarkus 3.27.6.1](https://github.com/quarkusio/quarkus/releases/tag/3.27.6.1)
  y [metadata de Central](https://repo.maven.apache.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml):
  seleccionado el último parche publicado de la misma línea 3.27 LTS,
  no salto de versión menor/mayor ni override aislado de transitivas.
  BOM y plugin comparten la propiedad. No se infiere un changelog de la
  página de blog 3.27.6.1 (no existe: HTTP 404); se verificó el release.

### Comandos y resultados reales

1. `mvn dependency:tree "-DoutputFile=target\items\H-06-tree-before.txt"`:
   salida 0, Commons Text 1.9, Quarkus HTTP/REST 3.27.4.
2. `mvn "-Dtest=AuthResourceTest,EndpointContractTest,HealthResourceTest,TokenContractTest,CorsConfigurationTest" test`:
   salida 0, 27 pruebas con dependencias anteriores, incluido nuevo caso
   exacto de escape. Apostrofe permanece literal y entidades previas se
   vuelven a escapar; no se corrige ese comportamiento como efecto lateral.
3. `python target\items\verify-dependencies.py target\items\H-06-tree-before.txt`:
   salida 1, no cumple coordenadas corregidas. Esta prueba compara versiones
   resueltas y alineación del manifiesto; no ejecuta un exploit ni un scanner.
4. Después del manifiesto: `mvn verify`, salida 0, 48 pruebas sin fallos,
   mismo contrato y gate JaCoCo >=85% por clase.
5. `mvn dependency:tree "-DoutputFile=target\items\H-06-tree-after.txt"`:
   salida 0; repetir script sobre ese árbol: salida 0, Commons Text 1.10.0,
   HTTP/REST 3.27.6.1 y BOM/plugin alineados. Se volvió a consultar el JSON
   de advisories oficiales. Rangos y versiones se interpretan por línea LTS,
   no como comparación textual de todos los rangos agregados por GitHub.
6. `gh run list --limit 5 --json databaseId,name,conclusion,status`:
   consulta real de ejecuciones ya existentes, no de estos cambios locales.
   CodeQL figura completado con éxito; esto no demuestra ausencia de alertas.
   `gh run view 39294998103 --log-failed` confirma Dependency Review:
   "Dependency review is not supported on this repository. Please ensure
   that Dependency graph is enabled". Requiere al administrador externo
   habilitarlo y reejecutar. No se deshabilitó ni se atribuye éxito al gate.

Evidencia local: [árbol antes](../../target/items/H-06-tree-before.txt),
[árbol después](../../target/items/H-06-tree-after.txt),
[caracterización](../../target/items/H-06-characterization-before.log),
[comparación roja](../../target/items/H-06-correction-before.log),
[comparación verde](../../target/items/H-06-correction-after.log),
[verify](../../target/items/H-06-after.log),
[avisos oficiales](../../target/items/H-06-quarkus-advisories.json),
[avisos reconsultados](../../target/items/H-06-quarkus-advisories-after.json),
[Dependency Review](../../target/items/H-06-dependency-review.log),
[diff](H-06.diff).

**Límite:** la investigación solicitada queda atendida, pero la comprobación
global de seguridad en CI sigue pendiente de capacidad externa. No se
ejecutó CodeQL local, un scanner SCA completo ni un test contra servicios
externos; el árbol Maven no sustituye esos controles.

## H-07

- Baseline: `mvn "-Dtest=AuthServiceTest,AuthResourceTest,LegacyDatabaseTest,EndpointContractTest,HealthResourceTest" test`,
  salida 0, 28 pruebas.
- Corrección estructural antes de cualquier refactor:
  `mvn "-Dtest=AuthServiceTest" test`, salida 0, 4 pruebas (3 nuevas con dobles).
  **No hay rojo fingido:** la separación ya había sido introducida en H-02.
  El plan autoriza cierre por evidencia sin refactor cosmético.
- Después de la revisión, sin cambios de producción: `mvn verify`, salida 0,
  51 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Resource solo valida/ensambla HTTP y DTOs; servicio consulta y verifica;
  repositorio parametriza y mapea; migración tiene transacción propia;
  hashing y firma están especializados. Métodos con responsabilidad definida,
  retornos tempranos, helpers existentes sin duplicar hash/SQL y una fuente
  de duración (`TokenService.EXPIRATION_SECONDS`). No se crearon capas vacías.
- [Baseline](../../target/items/H-07-characterization-before.log),
  [corrección antes, verde](../../target/items/H-07-correction-before.log),
  [después](../../target/items/H-07-after.log), [diff](H-07.diff).

## Cobertura y límites finales

`mvn verify`, JDK 21, raíz del repositorio. JaCoCo 0.8.12, `append=false`,
contador LINE y umbral 0.85 **por clase**, también para clases nuevas.
XML/HTML: [jacoco.xml](../../target/site/jacoco/jacoco.xml) /
[informe navegable](../../target/site/jacoco/index.html).

| clase | líneas cubiertas/total | porcentaje |
|---|---|---|
| AuthResource | 24/24 | 100% |
| AuthService | 10/10 | 100% |
| LegacyDatabase | 42/42 | 100% |
| TokenService | 25/25 | 100% |
| CorsConfiguration | 10/10 | 100% |
| AuthenticationPersistenceException | 2/2 | 100% |
| PasswordHasher (sin modificar) | 28/30 | 93,33% |
| PasswordMigration (sin modificar) | 58/59 | 98,31% |
| DTOs/registros restantes | 6/6 | 100% |
| Total producción | 205/208 | 98,56% |

No se modificó código fuera de PLAN. Cambios contractuales limitados a JWT,
CORS y errores autorizados; salud y otros campos permanecen. H-04 no
duplica el secreto retirado y H-07 no exige modificaciones cosméticas.
Dependency Review sigue pendiente de habilitación externa; no se bajaron
CodeQL, Dependency Review ni JaCoCo. No se migró `data/`, no se usaron
secretos reales y no se ejecutaron consumidores fuera del ámbito.

Documentos consultados: [PLAN](../../PLAN.md),
[HALLAZGOS](../../HALLAZGOS.md), [REGLAS](../../REGLAS.md),
[GOOD_PRACTICES](../GOOD_PRACTICES.md), [ENDPOINTS](../ENDPOINTS.md),
[TESTING](../TESTING.md), [README](../../README.md), más fuentes públicas
oficiales citadas en H-06.
