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
| H-03 | Token: Base64 con secreto embebido | Vulnerabilidad | El token sigue siendo reversible, sin firma y con secreto incluido. Solo se caracterizaron formato y vencimiento; la duración se centralizó sin cambiar su valor. El token firmado sigue bloqueado por el contrato y coordinación externa, según el plan aprobado. No se afirma cómo lo validan componentes fuera del ámbito. | [LegacyTokenUtil.java:9](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L9); [LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13); [LegacyTokenUtilTest.java](src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java); [diff de caracterización](docs/H-03.diff) | alta | Pendiente |
| H-04 | Configuración: secreto fijo y CORS permisivo | Vulnerabilidad | El secreto del token continúa fijo en Java y CORS permite cualquier origen. No se modificaron estas políticas. CORS ya reside en configuración; no se afirma que no sea sobrescribible ni que permita cookies con credenciales. | [LegacyTokenUtil.java:9](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L9); [application.properties:4](src/main/resources/application.properties#L4); [application.properties:5](src/main/resources/application.properties#L5) | alta | Pendiente |
| H-05 | Errores: respuesta expone detalles internos | Vulnerabilidad | HTTP 500 conserva mensaje y clase de la excepción para no cambiar el contrato. Ahora existe logging de servidor, pero no se corrigió la exposición al cliente. Los fallos SQL de consulta siguen registrándose y retornando `null`, que termina en HTTP 401. | [AuthResource.java:39](src/main/java/com/bcp/workshop/auth/AuthResource.java#L39); [LegacyDatabase.java:61](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L61) | media | Pendiente |
| H-06 | SCA: Quarkus `3.27.4` y Commons Text `1.9` presentes | Sugerencia | Las versiones siguen declaradas y se mantiene `escapeHtml4`. No se investigaron advisories ni se ejecutaron scanners, por lo que no se confirma un CVE. Severidad media de priorización de investigación, no calificación CVE. | [pom.xml:13](pom.xml#L13); [pom.xml:49](pom.xml#L49); [pom.xml:50](pom.xml#L50); [AuthResource.java:62](src/main/java/com/bcp/workshop/auth/AuthResource.java#L62) | media | Pendiente |
| H-07 | Clean code: Resource concentra responsabilidades | Refactor | La separación mínima necesaria para H-02 introdujo servicio, repositorio, hasher y migración; Resource conserva validación y mapeo HTTP con retornos tempranos, DTOs propios y duración compartida. No se cierra este ID fuera de la selección aprobada; queda pendiente su revisión específica, sin afirmar que el código mantiene intacta la concentración original. | [AuthResource.java:27](src/main/java/com/bcp/workshop/auth/AuthResource.java#L27); [AuthResource.java:60](src/main/java/com/bcp/workshop/auth/AuthResource.java#L60); [AuthService.java](src/main/java/com/bcp/workshop/auth/AuthService.java); [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) | baja | Pendiente |

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
   incorrecta y fallo de consulta conservan HTTP 401.
4. **Token:** el servicio delega al emisor legado. El formato sigue siendo
   Base64 de cuatro segmentos y el vencimiento sigue siendo 3600 segundos.
5. **Salida:** Resource mapea a DTOs públicos con `token`, `expiresIn`, `user`
   y `debug`; mantiene escape de HTML, mensajes y estados documentados.

## Resultados de implementación

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

No se ejecutaron scanners ni se inspeccionaron otros componentes. Las pruebas
usan SQLite dentro de `target/`; no se modificó la base existente en `data/`.
Los resultados no prueban que una base de despliegue ya esté migrada ni que
los consumidores externos acepten un token firmado.
