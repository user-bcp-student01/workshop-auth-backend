# Hallazgos del backend de autenticación

Matriz única de seguimiento de los siete casos de [RETO.md](RETO.md).
Ordenada por severidad y con IDs estables. Ningún caso fue omitido como
**No encontrado**. La detección inicial fue estática; la actualización
2026-10-19 incorpora la implementación aprobada en [PLAN.md](PLAN.md) y
pruebas ejecutadas, sin extender el trabajo a otros componentes.
La presencia de una versión declarada no demuestra un CVE ni explotabilidad.

| id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|
| H-01 | SQL: concatenación de credenciales | Vulnerabilidad | Originalmente ambas credenciales se concatenaban al SQL, permitiendo alterar el filtro. Resuelto con consultas e inserciones parametrizadas; la búsqueda actual usa el usuario literal y la contraseña se verifica fuera de SQL. Las tres regresiones fallaban antes y pasan después. | [LegacyDatabase.java:21](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L21); [LegacyDatabase.java:52](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L52); [LegacyDatabase.java:80](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L80); [SqlCorrectionTest.java](src/test/java/com/bcp/workshop/auth/SqlCorrectionTest.java); [diff intermedio](docs/H-01.diff) | alta | Resuelto |
| H-02 | Datos: contraseñas en texto plano | Vulnerabilidad | Originalmente se insertaban y comparaban contraseñas planas. Resuelto en código mediante PBKDF2-HMAC-SHA256 con salt aleatorio individual, coste configurable y migración transaccional/idempotente a `password_hash`, sin fallback plano. Conserva identidades y no expone hashes en respuesta. Se verificó solo con bases aisladas: la base real no se migró durante este trabajo y sus restos/copias requieren manejo autorizado. | [PasswordHasher.java:33](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L33); [PasswordHasher.java:40](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L40); [PasswordMigration.java:29](src/main/java/com/bcp/workshop/auth/PasswordMigration.java#L29); [AuthService.java:17](src/main/java/com/bcp/workshop/auth/AuthService.java#L17); [PasswordStorageTest.java](src/test/java/com/bcp/workshop/auth/PasswordStorageTest.java); [PasswordMigrationTest.java](src/test/java/com/bcp/workshop/auth/PasswordMigrationTest.java) | alta | Resuelto |
| H-03 | Token: Base64 con secreto embebido | Vulnerabilidad | Resuelto con JWT RS256, claims acordados, expiración 3600 s y clave RSA externa; transición confirmada por el usuario. `TokenContractTest` falla antes y pasa después; `TokenServiceTest` verifica firma, alteración, expiración y configuración. | [TokenService.java](src/main/java/com/bcp/workshop/auth/TokenService.java); [TokenServiceTest.java](src/test/java/com/bcp/workshop/auth/TokenServiceTest.java); [diff H-03](docs/items/H-03.diff) | alta | Resuelto |
| H-04 | Configuración: secreto fijo y CORS permisivo | Vulnerabilidad | Resuelto: H-03 retiró el secreto legado; CORS ahora limita orígenes externos explícitos, GET/POST y headers necesarios, con validación al iniciar. `CorsConfigurationTest` pasa tras 2 fallos previos; `CorsValidationTest` rechaza configuración inválida. | [CorsConfiguration.java](src/main/java/com/bcp/workshop/auth/CorsConfiguration.java); [application.properties](src/main/resources/application.properties); [diff H-04](docs/items/H-04.diff) | alta | Resuelto |
| H-05 | Errores: respuesta expone detalles internos | Vulnerabilidad | Resuelto: excepción de persistencia con causa y un único log en Resource; SQL y fallos internos responden 500 genérico sin clase ni detalles. Las pruebas de Resource, repositorio, HTTP y salud pasan tras 5 fallos esperados antes del cambio. | [AuthenticationPersistenceException.java](src/main/java/com/bcp/workshop/auth/AuthenticationPersistenceException.java); [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java); [diff H-05](docs/items/H-05.diff) | media | Resuelto |
| H-06 | SCA: Quarkus `3.27.4` y Commons Text `1.9` presentes | Sugerencia | Resuelto el análisis y actualización: BOM/plugin 3.27.6.1 y Commons Text 1.10.0, con advisories oficiales y comparación de coordenadas antes/después. Escape y contratos pasan; no se afirma explotación ni scanner completo: Dependency Review externo está bloqueado por configuración del repositorio. | [pom.xml](pom.xml); [evidencia y límites H-06](docs/items/RESULTADOS.md#h-06); [diff H-06](docs/items/H-06.diff) | media | Resuelto |
| H-07 | Clean code: Resource concentra responsabilidades | Refactor | Resuelto por revisión de la separación ya introducida: API/DTOs → servicio → repositorio, hashing/migración y firma especializados, guard clauses y duración única. Tres pruebas con dobles en `AuthServiceTest` confirman delegación exacta, ausencia sin hash e identidad sin credenciales; no hizo falta refactor cosmético de producción. | [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java); [AuthServiceTest.java](src/test/java/com/bcp/workshop/auth/AuthServiceTest.java); [diff H-07](docs/items/H-07.diff) | baja | Resuelto |

## Flujo actual

1. **Inicio:** `LegacyDatabase` se inicializa al arrancar Quarkus. Lee la
   configuración externa, migra el esquema mediante `PasswordMigration` y
   siembra solo usuarios ausentes usando contraseñas configuradas y hasheadas.
   Un fallo de inicio se registra y se propaga, sin aparentar éxito.
2. **Entrada:** Resource conserva petición, orden de validación y mensajes.
   Las credenciales no se recortan ni normalizan.
3. **Autenticación:** `AuthService` busca el usuario exacto mediante parámetros
   y verifica PBKDF2. El repositorio devuelve un registro interno de hash y
   usuario; el servicio solo devuelve `UserRecord`. Ausencia, contraseña
   incorrecta producen HTTP 401; un fallo SQL propaga la causa y produce
   HTTP 500 genérico, registrado una vez en Resource.
4. **Token:** el servicio delega a `TokenService` con clave RSA externa,
   firma RS256 y claims explícitos acordados. `exp = iat + 3600`.
5. **Salida:** Resource mapea a DTOs públicos con `token`, `expiresIn`, `user`
   y `debug`; mantiene escape HTML4 y el contrato actualizado por autorización.
   CORS limita orígenes configurados explícitamente, métodos y cabeceras.

## Resultados actuales H-03 a H-07

Cada fila se actualizó tras verificar su item antes del siguiente.
Ver [resultados por item](docs/items/RESULTADOS.md) y diffs independientes
en [docs/items/](docs/items/). Final: `mvn verify`, salida 0, **51 pruebas,
0 fallos, 0 errores, 0 omitidas**. JaCoCo: **205/208 líneas, 98,56%**;
clases de producción modificadas/agregadas en esta tanda: **100%**.
El mínimo de todas las clases sigue siendo 93,33% en `PasswordHasher`.
No se alteraron workflows, umbrales o datos de `data/`.

Dependency Review requiere habilitación externa y reejecución; no se afirma
que la investigación focalizada sustituya un scanner global ni ese gate.
Los consumidores externos se coordinaron por confirmación humana, sin
inspeccionar sus repositorios ni ejecutar integraciones fuera del ámbito.

## Resultados históricos de implementación H-01/H-02

La aprobación humana y la sustitución autorizada de BCrypt por PBKDF2 figuran
en [PLAN.md](PLAN.md). No se agregaron dependencias. Las instrucciones de
migración, configuración, diffs y secuencia antes/después están en
[README.md](README.md#validación-y-diffs).

- Antes de producción: **9 pruebas de caracterización pasan**.
- H-01: **3 regresiones fallan antes**; después **12 pruebas pasan**, incluidas
  las 9 de caracterización y las 3 regresiones.
- H-02: la prueba de esquema **falla antes** por ausencia de `password_hash`;
  después pasa. Se añadieron pruebas de salt, coste, Unicode, longitud,
  migración, idempotencia, rollback y errores explícitos de inicialización.
- Final: `mvn verify` con JDK 21, **24 pruebas, 0 fallos, 0 errores, 0 omitidas**.
  JaCoCo exige 85 % de líneas por clase modificada/agregada. El mínimo medido
  es **93,33 %** (`PasswordHasher`); `PasswordMigration`: **98,31 %**; Resource,
  DTOs, servicio, repositorio, token y credenciales internas: **100 %**.
  No se excluyeron esas clases ni se redujeron quality gates.
- [docs/ENDPOINTS.md](docs/ENDPOINTS.md) permanece byte a byte intacto:
  SHA-256 `E75F3F208D31144B4B792D76836ED40C21B74A6FABEB22986E8C3CA3F8E6F617`.

En aquella etapa no se ejecutaron scanners ni se inspeccionaron otros componentes. Las pruebas
usan SQLite dentro de `target/`; no se modificó la base existente en `data/`.
Los resultados no prueban que una base de despliegue ya esté migrada ni que
los consumidores externos acepten un token firmado.
