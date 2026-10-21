# Hallazgos del backend de autenticacion

Analisis y seguimiento de correcciones, limitados a este backend. Esta es la unica
matriz de seguimiento; `2-refactor.md` debe actualizar aqui cada hallazgo, sin
crear un registro paralelo. H-01 permanece resuelto. La revision del PR devuelve
H-02 a Pendiente: el hash funciona, pero el mantenimiento WAL no garantiza la
eliminacion del texto plano. H-03 tiene emision JWT integrada y pruebas locales
de interoperabilidad; queda pendiente de revision del PR. La representacion del
token cambia por el Issue #1 aprobado; las demas respuestas HTTP se conservan.

## Matriz de seguimiento

Una fila por caso de [RETO.md](RETO.md), ordenadas por severidad. Los siete casos
aparecen en las fuentes; ninguno requiere el estado "No encontrado". En SCA se
confirman las versiones declaradas, no la explotabilidad de un CVE concreto.

| id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|
| H-01 | SQL: concatenacion de credenciales | Vulnerabilidad | La concatenacion permitia alterar la autenticacion. Resuelto con PreparedStatement: primero se parametrizaron ambas credenciales y H-02 dejo una busqueda por username parametrizada. Caracterizacion: 10 tests verdes antes y 11 despues; regresiones con apostrofos y SQL conservadas. | [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:148](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L148); [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:149](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L149) | alta | Resuelto |
| H-02 | Datos: contrasenas en texto plano | Vulnerabilidad | Devuelto a Pendiente en revision: Argon2id y verificacion en servicio correctos, pero se ignora el resultado busy del checkpoint WAL. Con un lector concurrente, initialize y su repeticion retornan exito mientras el WAL conserva el password legacy. La limpieza solo se intenta si migrated=true; falta verificar/reintentar mantenimiento y una regresion WAL antes de cerrar el hallazgo. | [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:55](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L55); [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:56](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L56); [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:161](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L161); [src/main/java/com/bcp/workshop/auth/PasswordHasher.java:20](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L20) | alta | Pendiente |
| H-03 | Token: Base64 con secreto embebido, sin firma | Vulnerabilidad | Correccion local del Issue #1: login conectado a TokenService RS256 con clave externa, sub/groups/iss/aud/iat/exp y duracion conservada. Emisor legacy retirado sin fallback. Interoperabilidad HTTP Java a FastAPI para ambos usuarios y transporte Angular con JWT real aprobados localmente; mvn verify: 31 pruebas y 4 IT, cobertura de AuthService/TokenService 100% lineas y ramas. Pendiente revision humana y gates remotos; no se cierra automaticamente. | [src/main/java/com/bcp/workshop/auth/AuthService.java:32](src/main/java/com/bcp/workshop/auth/AuthService.java#L32); [src/main/java/com/bcp/workshop/auth/TokenService.java:66](src/main/java/com/bcp/workshop/auth/TokenService.java#L66); [src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:39](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L39) | alta | Pendiente |
| H-04 | Configuracion: secreto fijo y CORS permisivo | Vulnerabilidad | El Issue #1 retira el secreto fijo del emisor y su inclusion en el token. CORS sigue habilitado con una expresion que acepta cualquier origen; no se modifica ni se considera resuelto su alcance. No se afirma envio automatico de credenciales ni que CORS sustituya autenticacion; su politica puede sobreescribirse mediante configuracion Quarkus. | [src/main/resources/application.properties:4](src/main/resources/application.properties#L4); [src/main/resources/application.properties:5](src/main/resources/application.properties#L5); [src/main/resources/application.properties:9](src/main/resources/application.properties#L9) | alta | Pendiente |
| H-05 | Errores: respuesta expone detalles internos | Vulnerabilidad | Una excepcion que alcance el catch del Resource sigue devolviendo mensaje y nombre de clase. Los fallos de consulta siguen retornando null y generan 401. Se conservaron deliberadamente esas respuestas para no ampliar el alcance ni cambiar el contrato. | [src/main/java/com/bcp/workshop/auth/AuthResource.java:63](src/main/java/com/bcp/workshop/auth/AuthResource.java#L63); [src/main/java/com/bcp/workshop/auth/AuthResource.java:65](src/main/java/com/bcp/workshop/auth/AuthResource.java#L65); [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:154](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L154) | media | Pendiente |
| H-06 | SCA: Quarkus 3.27.4 y Commons Text 1.9 declarados | Sugerencia | Las versiones del reto no cambiaron. Falta investigar sus advisories y explotabilidad; las consultas OSV de nuevas dependencias no cierran este hallazgo. Commons Text sigue usado para escapeHtml4, ahora en el DTO publico, no para interpolacion. | [pom.xml:13](pom.xml#L13); [pom.xml:58](pom.xml#L58); [pom.xml:59](pom.xml#L59); [src/main/java/com/bcp/workshop/auth/UserResponse.java:7](src/main/java/com/bcp/workshop/auth/UserResponse.java#L7) | media | Pendiente |
| H-07 | Clean code: Resource concentra validacion, negocio, persistencia y respuesta | Refactor | El Resource originalmente concentraba esas responsabilidades. H-02 introdujo servicio, DTOs y guard clauses como apoyo necesario, pero PLAN.md no autoriza cerrar H-07 sin revision integral de separacion y duplicacion. Se conserva pendiente de esa revision, no se afirma que el metodo siga intacto. | [src/main/java/com/bcp/workshop/auth/AuthResource.java:27](src/main/java/com/bcp/workshop/auth/AuthResource.java#L27); [src/main/java/com/bcp/workshop/auth/AuthService.java:17](src/main/java/com/bcp/workshop/auth/AuthService.java#L17); [src/main/java/com/bcp/workshop/auth/LoginResponse.java:6](src/main/java/com/bcp/workshop/auth/LoginResponse.java#L6) | baja | Pendiente |

## Mapa del flujo de login previo a la correccion

Snapshot del analisis inicial. Sus referencias de linea corresponden a aquella
version; la evidencia actual de H-01/H-02/H-03 esta en la matriz anterior.

1. **Arranque y configuracion.** [README.md](README.md) indica JDK 21, Maven 3.9+ y `mvn quarkus:dev`. El servicio escucha en puerto 8080 y host local; el perfil prod cambia el host a todas las interfaces. CORS permite cualquier origen y Jackson ignora propiedades desconocidas ([src/main/resources/application.properties:1](src/main/resources/application.properties#L1), [src/main/resources/application.properties:3](src/main/resources/application.properties#L3), [src/main/resources/application.properties:8](src/main/resources/application.properties#L8)).
2. **Persistencia inicial.** El bean ApplicationScoped inicializa SQLite al construirse. La URL procede de `AUTH_DB_URL` o de `jdbc:sqlite:./data/workshop.db`; crea el directorio data, la tabla legacy_users y dos usuarios de laboratorio con INSERT OR IGNORE. Las excepciones solo imprimen stacktrace ([src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:13](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L13), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:15](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L15), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:17](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L17), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:22](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L22), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:27](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L27)).
3. **Entrada y validacion.** POST /auth/login consume y produce JSON. LoginRequest contiene username, password y client; client no interviene en login. Se comprueban request nulo, username nulo/vacio y password nulo/vacio, en ese orden. trim solo comprueba vacios; los valores originales llegan a la consulta ([src/main/java/com/bcp/workshop/auth/AuthResource.java:15](src/main/java/com/bcp/workshop/auth/AuthResource.java#L15), [src/main/java/com/bcp/workshop/auth/AuthResource.java:26](src/main/java/com/bcp/workshop/auth/AuthResource.java#L26), [src/main/java/com/bcp/workshop/auth/AuthResource.java:35](src/main/java/com/bcp/workshop/auth/AuthResource.java#L35), [src/main/java/com/bcp/workshop/auth/LoginRequest.java:4](src/main/java/com/bcp/workshop/auth/LoginRequest.java#L4), [src/main/java/com/bcp/workshop/auth/LoginRequest.java:6](src/main/java/com/bcp/workshop/auth/LoginRequest.java#L6)).
4. **Consulta y decision.** findByCredentials abre conexion y ejecuta SQL concatenado. Devuelve el primer resultado como UserRecord (id, username, displayName y role), o null si no hay coincidencia o sucede un error. El Resource transforma null en 401; el login no inserta ni actualiza usuarios ([src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:37](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L37), [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:42](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L42), [src/main/java/com/bcp/workshop/auth/UserRecord.java:3](src/main/java/com/bcp/workshop/auth/UserRecord.java#L3), [src/main/java/com/bcp/workshop/auth/AuthResource.java:37](src/main/java/com/bcp/workshop/auth/AuthResource.java#L37)).
5. **Token y salida.** Con usuario valido se codifica en Base64 UTF-8 la concatenacion de username, role, epoch de expiracion y secreto, separados por barras verticales. La respuesta 200 incluye token, expiresIn=3600, user y debug; displayName pasa por escapeHtml4 y no se devuelve id ni password ([src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13), [src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:15](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L15), [src/main/java/com/bcp/workshop/auth/AuthResource.java:41](src/main/java/com/bcp/workshop/auth/AuthResource.java#L41), [src/main/java/com/bcp/workshop/auth/AuthResource.java:45](src/main/java/com/bcp/workshop/auth/AuthResource.java#L45), [src/main/java/com/bcp/workshop/auth/AuthResource.java:48](src/main/java/com/bcp/workshop/auth/AuthResource.java#L48)).
6. **Errores y limite de verificacion.** El catch del Resource devuelve 500 con detalles de la excepcion. Los errores de deserializacion previos al metodo quedan a cargo de Quarkus y no se caracterizaron en ejecucion. La prueba existente comprueba solo status 200, username y presencia del token ([src/main/java/com/bcp/workshop/auth/AuthResource.java:54](src/main/java/com/bcp/workshop/auth/AuthResource.java#L54), [src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:13](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L13)). No se ejecutaron pruebas de explotacion ni se inspeccionaron consumidores externos.

El contrato actual queda documentado en [docs/ENDPOINTS.md](docs/ENDPOINTS.md).
Este analisis no propone cambiar rutas, entradas ni estructura de respuestas.

## Verificacion de la implementacion

Revision del PR (2026-10-06): los resultados siguientes documentan la
implementacion inicial, no certifican el cierre de H-02. Repro aislado en
target/ReviewWalProbe.java y target/review-wal-probe.log: arranque exitoso,
checkpoint busy=1, texto plano presente en el WAL y segundo arranque exitoso
sin eliminarlo. El fixture queda solo bajo target/test-databases; la base del
laboratorio no se toco. Antes del merge, el mantenimiento debe comprobar el
resultado del checkpoint y permitir recuperacion/reintento aunque el esquema
ya este migrado. La prueba actual inspecciona el archivo .db, no el .db-wal.

La revision volvio a ejecutar mvn verify con JaCoCo fresco: 24 tests verdes,
162/162 lineas (100%) y 56/58 ramas (96,55%). Esa cobertura no detecta el caso
WAL omitido. H-01 permanece Resuelto: la consulta por username usa
PreparedStatement y las regresiones de apostrofos/SQL siguen verdes. Los gates
versionados no cambiaron; checks alojados en GitHub no verificados localmente.

Observacion importante adicional: AuthService.authenticate corta antes de
verificar Argon2 cuando el usuario no existe
([src/main/java/com/bcp/workshop/auth/AuthService.java:19](src/main/java/com/bcp/workshop/auth/AuthService.java#L19)).
El repro local target/ReviewTimingProbe.java midio 0,014 ms para usuario ausente
frente a 149,43 ms para usuario existente con password incorrecto (cinco muestras
por caso). Esto demuestra la asimetria del servicio, no una medicion remota HTTP;
puede facilitar enumeracion de usuarios. Proponer verificacion contra un hash
dummy con el mismo coste para el caso ausente, manteniendo el contrato.

SCA de revision: OSV devolvio GHSA-599f-7c49-w659 / CVE-2022-42889 para
Commons Text 1.9. El uso visible es escapeHtml4 en UserResponse; no se demostro
explotabilidad de interpolacion en este flujo. H-06 permanece Pendiente.
La consulta no devolvio advisories para Quarkus core 3.27.4, Password4j 1.8.4
ni jose4j 0.9.6; no constituye una auditoria exhaustiva de transitivas.

- H-01: `mvn -Dtest=AuthResourceTest,LegacyDatabaseTest test` antes y `verify`
	despues. Evidencia generada: target/h01-before.log, target/h01-after.log y
	target/h01.diff. Cobertura al cerrar ese paso: 90,48% lineas y 100% ramas
	de LegacyDatabase.
- H-02: caracterizacion de base nueva/existente, passwords largos y Unicode
	antes del cambio. Despues: migracion con conservacion de IDs/metadatos,
	rollback, idempotencia, hash corrupto y salts distintos. Evidencia:
	target/h02-before.log, target/h02-contract-after.log, target/h02-after.log
	y target/h02.diff.
- H-03: caracterizacion previa en LegacyTokenUtilTest; pruebas del modulo en
	TokenServiceTest verifican firma, claims, issuer, expiracion y rechazo de
	algoritmo inesperado, firma alterada o clave incorrecta. Evidencia:
	target/h02-before.log y target/h03-module-after.log. Activacion, validacion
	al arranque y retiro del emisor legacy quedan pendientes del bloqueo externo;
	la configuracion del modulo se valida al construirlo, sin activar un bean.
- Verificacion final: `mvn verify`, 24 tests sin fallos ni errores. Se borro
	solo target/jacoco.exec antes para medir sin ejecuciones anteriores.
	JaCoCo: 100% lineas de todas las clases ejecutables medidas; LegacyDatabase
	90% ramas y las demas clases con ramas 100%. El informe esta en
	target/site/jacoco/index.html y target/site/jacoco/jacoco.xml.
- El SHA256 de docs/ENDPOINTS.md permanece igual. Las pruebas HTTP verifican
	forma JSON, validacion, mensajes, errores, escapes y credenciales locales.
	Los workflows CodeQL, Dependency Review y JaCoCo no fueron modificados;
	los checks alojados en GitHub no se ejecutaron desde este entorno local.
- Password4j 1.8.4: Apache-2.0, Java 8+, transitiva SLF4J 2.0.17 del BOM.
	Coste elegido: Argon2id con 19456 KiB, 2 iteraciones, paralelismo 1, hash
	de 32 bytes y salt de 16 bytes; media local medida entre 111 y 132 ms/hash.
	JWT: extension Quarkus 3.27.4 del BOM, SmallRye JWT 4.6.2 y jose4j 0.9.6.
	Consultas OSV del 2026-10-05 sin advisories devueltos para estas nuevas
	dependencias/versiones; esto no certifica ausencia de vulnerabilidades ni
	resuelve H-06. Fuentes: Maven Central, README/POM oficial de Password4j,
	guia Quarkus JWT Build, API SmallRye 4.6.2 y API OSV.
- Todas las bases y claves usadas por las pruebas estan bajo
	target/test-databases. No se ejecuto la aplicacion contra data/workshop.db
	ni se migraron o eliminaron copias de esa base. Antes de ejecutar con datos
	existentes: ventana sin escrituras, copia de recuperacion aislada y retiro
	posterior seguro de backups/journals antiguos, segun PLAN.md.
- Ajustes de ejecucion: aislamiento AUTH_DB_URL en Surefire y registro JDBC
	del cargador de tests para hacer las pruebas reproducibles; TokenPolicy se
	introdujo con H-02 para quitar 3600 duplicado sin cambiar su valor.
	H-04/H-05/H-06/H-07 siguen pendientes y no se ampliaron sus correcciones.