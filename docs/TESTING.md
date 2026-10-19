# Pruebas de los endpoints

## Implementación aprobada H-03 a H-07

Los resultados, comandos, salidas y diffs actuales están en
[items/RESULTADOS.md](items/RESULTADOS.md). Las secciones siguientes conservan
la caracterización histórica de 39 pruebas: sus snippets/resultados no
describen las correcciones posteriores de token, CORS y errores.
La fuente ejecutable actual es [el directorio de tests](../src/test/java/com/bcp/workshop/auth/).

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | JWT, claims y firma | feliz/borde/error | 200; firma RS256 verificable; verificador rechaza alteración, clave errónea y vencimiento |
| POST /auth/login | CORS local/origen ajeno/método/header | feliz/borde/error | permisos solo para origen y valores configurados |
| POST /auth/login | Sin Origin | feliz | 200 sin requisito nuevo de autenticación |
| POST /auth/login | SQL inaccesible/hash inválido/excepción sin mensaje | error | 500 `{"message":"Internal server error"}`; causa solo en servidor |
| POST /auth/login | Contraseña incorrecta/entrada inválida/rechazo framework | error | 401/400/405/406/415 previos intactos |
| POST /auth/login | Escape de &, <, >, comillas, Unicode y entidades previas | borde | salida HTML4 exacta conservada con las dependencias actualizadas |
| POST /auth/login (servicio) | Usuario ausente/clave errónea/identidad válida | feliz/borde/error | dobles confirman una consulta exacta; cero verificaciones si ausente y una con contraseña original si existe |

Última ejecución completa: **51 pruebas**, salida **0**, sin fallos/errores/
omitidas. JaCoCo **205/208 líneas (98,56%)**; clases modificadas/agregadas
de esta implementación: **100%**. Medición: contadores `LINE` de
[jacoco.xml](../target/site/jacoco/jacoco.xml), sin acumulación (`append=false`),
gate por clase >=85% en `mvn verify`.

## Evidencia histórica previa a esta implementación

Ejecución real: 2026-10-19. Contrato de referencia:
[ENDPOINTS.md](ENDPOINTS.md). Meta: [REGLAS.md](../REGLAS.md), cobertura
de código modificado de 85 % o superior.

En esta tarea solo se agregaron dos archivos de pruebas y este documento.
No se cambió código de producción, contrato, dependencias ni quality gates.
Las expectativas se fijaron a partir del contrato antes de ejecutar; no se
cambiaron para ocultar errores. Las caracterizaciones de defectos conocidos
no son pruebas de su corrección: se identifican explícitamente más abajo.

## Matriz de casos y trazabilidad

Todos los casos de esta tabla están ejecutados, excepto el estado de salud
fallido sin contrato, que está marcado pendiente. Una fila puede abarcar
varios valores en un mismo método JUnit; no representa necesariamente un test
independiente en el conteo de Surefire.

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | Usuarios `student` y `trainer`, contraseña válida; `logsInWithWorkshopUser`, `preservesCompleteSuccessAndOptionalClient` | Camino feliz | 200; objeto de cuatro campos, usuario de tres campos, `expiresIn: 3600`, debug literal e identidad correcta. |
| POST /auth/login | `client: null`, sin Authorization ni Accept; `acceptsNullClientAndDoesNotRequireAuthorizationOrAccept` | Borde | 200 JSON, usuario correcto y cuatro campos de salida. |
| POST /auth/login | `client` ausente y campo desconocido; `preservesCompleteSuccessAndOptionalClient` | Borde | 200; no altera la identidad ni los campos de respuesta. |
| POST /auth/login | Cadenas vacías, espacio y tabulador, ambos campos inválidos; `preservesValidationOrderAndMessages` | Borde / error | 400 `Username is required` primero; con usuario válido, 400 `Password is required`. |
| POST /auth/login | Cuerpo JSON `null` y cuerpo HTTP ausente; `rejectsNullAndAbsentHttpBodies` | Error | 400, cuerpo de un campo: `{"message":"Request is null"}`. |
| POST /auth/login | `{}` y `username: null`; `rejectsMissingAndExplicitNullFields` | Error | 400 `Username is required`. |
| POST /auth/login | Contraseña ausente o nula; `rejectsMissingAndExplicitNullFields` | Error | 400 `Password is required`. |
| POST /auth/login | Usuario ausente, contraseña incorrecta y espacios no recortados; `preservesInvalidCredentialsAndWhitespace` | Error / borde | 401 `Invalid user <usuario exacto> or password`; no autentica con espacios añadidos. |
| POST /auth/login | Contraseña Unicode de 160 bytes y nombre con HTML; `preservesEscapingLongPasswordsAndExactWhitespace` | Borde | 200 con contraseña completa y escape HTML; cambiar un carácter final produce 401. |
| POST /auth/login | Tabla inaccesible durante consulta; `characterizesKnownSqlFailureBugAsHttp401` | Error; defecto conocido | 401 por HTTP con mensaje de credenciales inválidas. Se reproduce el defecto, no se considera correcto distinguir así infraestructura y credenciales. |
| POST /auth/login | Hash de fixture inválido; `characterizesKnownInternalErrorDisclosureBugOverHttp` | Error; defecto conocido | 500 JSON de dos campos, mensaje `Unsupported password hash format` y clase `java.lang.IllegalArgumentException`. |
| POST /auth/login | Excepción del servicio con mensaje nulo; `preservesExceptionResponses` | Error; prueba directa del Resource | Estado 500, mensaje `Unknown error`, clase `java.lang.IllegalStateException`. No se afirma que se inyectó esta excepción por HTTP. |
| POST /auth/login | Excepción del servicio con mensaje no nulo; `preservesExceptionResponses` | Error; prueba directa del Resource | Estado 500, mensaje `test failure` y la misma clase. |
| POST /auth/login | JSON `{` malformado y raíz `[]`; `rejectsMalformedJsonAndArrayRootWithEmptyBodies` | Error | 400, cuerpo vacío en ambos casos. |
| POST /auth/login | `{"username":{},"password":"x"}`; `rejectsIncompatibleUsernameType` | Error | 400 JSON: `objectName: LoginRequest`, `attributeName: username`, `line: 1`, `column: 13`, `value: null`. |
| POST /auth/login | GET en lugar de POST; `rejectsWrongMethodWithEmptyBody` | Error | 405, cuerpo vacío. |
| POST /auth/login | `Accept: application/xml`; `rejectsUnacceptableResponseTypeWithEmptyBody` | Error | 406, cuerpo vacío. |
| POST /auth/login | `Content-Type: text/plain`, cuerpo `hello`; `rejectsUnsupportedRequestTypeWithEmptyBody` | Error | 415, cuerpo vacío. |
| GET /q/health | Sin autenticación, sin cuerpo; `reportsUpWithoutAuthenticationAndWithNoChecks` | Camino feliz / borde | 200 JSON exactamente con `status: UP` y `checks: []`. El array vacío es el borde documentado. |
| GET /q/health | `Accept: application/xml`; `acceptsXmlPreferenceButStillReturnsJson` | Borde | 200, sigue devolviendo JSON con dos campos. |
| GET /q/health | POST a la misma ruta; `characterizesDocumentedPostBehaviorWithoutInventing405` | Borde | 200 UP y checks vacíos, según la observación documentada; no se inventa otro endpoint ni un 405. |
| GET /q/health | Hash inválido provoca fallo de login; `characterizesKnownHealthCoverageGapDespiteLoginFailure` | Borde; limitación conocida | Login da 500 pero salud sigue UP con checks vacíos; no implica salud funcional del login. |
| GET /q/health | Servicio inaccesible; `unavailableServiceIsTransportFailureNotHttpError` | Error de transporte, no respuesta HTTP | `ConnectException`; no hay estado ni cuerpo HTTP. Se usa puerto loopback reservado sin listener, no se detiene una instancia compartida. |
| GET /q/health | Comprobación fallida de salud, sin código/cuerpo definidos | Error pendiente | No determinado por el contrato. No se inventa 503/DOWN ni se agrega un HealthCheck para fabricar ese resultado. |

### Archivos ejecutables

- [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  6 métodos existentes de login, validación, campos, bordes y excepciones.
- [EndpointContractTest.java](../src/test/java/com/bcp/workshop/auth/EndpointContractTest.java):
  10 métodos nuevos para errores HTTP y bordes que faltaban.
- [HealthResourceTest.java](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java):
  5 métodos nuevos para salud, negociación y transporte.
- [LegacyTokenUtilTest.java](../src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java):
  formato legado y vencimiento; no verifica una firma inexistente.
- [LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java):
  consulta, siembra idempotente y fallos de inicio/consulta.
- [AuthServiceTest.java](../src/test/java/com/bcp/workshop/auth/AuthServiceTest.java),
  [PasswordHasherTest.java](../src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java),
  [PasswordMigrationTest.java](../src/test/java/com/bcp/workshop/auth/PasswordMigrationTest.java),
  [PasswordStorageTest.java](../src/test/java/com/bcp/workshop/auth/PasswordStorageTest.java)
  y [SqlCorrectionTest.java](../src/test/java/com/bcp/workshop/auth/SqlCorrectionTest.java):
  regresiones ya existentes de negocio, persistencia y seguridad.

## Código de las pruebas añadidas

Los archivos enlazados arriba son las fuentes ejecutables. Se reproduce su
código para revisión; no es pseudocódigo ni una propuesta sin ejecutar.

### EndpointContractTest.java

```java
package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class EndpointContractTest {
    private static final String LOGIN = "/auth/login";

    @Test
    void rejectsNullAndAbsentHttpBodies() {
        given().contentType("application/json").body("null").post(LOGIN)
                .then().statusCode(400).body("$", aMapWithSize(1))
                .body("message", is("Request is null"));
        given().contentType("application/json").post(LOGIN)
                .then().statusCode(400).body("$", aMapWithSize(1))
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingAndExplicitNullFields() {
        for (String payload : new String[]{"{}", "{\"username\":null,\"password\":\"x\"}"}) {
            given().contentType("application/json").body(payload).post(LOGIN)
                    .then().statusCode(400).body("message", is("Username is required"));
        }
        for (String payload : new String[]{"{\"username\":\"student\"}",
                "{\"username\":\"student\",\"password\":null}"}) {
            given().contentType("application/json").body(payload).post(LOGIN)
                    .then().statusCode(400).body("message", is("Password is required"));
        }
    }

    @Test
    void acceptsNullClientAndDoesNotRequireAuthorizationOrAccept() {
        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":null}")
                .post(LOGIN).then().statusCode(200).contentType("application/json")
                .body("$", aMapWithSize(4)).body("user.username", is("student"));
    }

    @Test
    void rejectsMalformedJsonAndArrayRootWithEmptyBodies() {
        for (String payload : new String[]{"{", "[]"}) {
            given().contentType("application/json").accept("application/json").body(payload)
                    .post(LOGIN).then().statusCode(400).body(is(""));
        }
    }

    @Test
    void rejectsIncompatibleUsernameType() {
        given().contentType("application/json").accept("application/json")
                .body("{\"username\":{},\"password\":\"x\"}").post(LOGIN)
                .then().statusCode(400).contentType("application/json")
                .body("$", aMapWithSize(5))
                .body("objectName", is("LoginRequest"))
                .body("attributeName", is("username"))
                .body("line", is(1)).body("column", is(13)).body("value", is((Object) null));
    }

    @Test
    void rejectsWrongMethodWithEmptyBody() {
        given().get(LOGIN).then().statusCode(405).body(is(""));
    }

    @Test
    void rejectsUnacceptableResponseTypeWithEmptyBody() {
        given().contentType("application/json").accept("application/xml").body("{}")
                .post(LOGIN).then().statusCode(406).body(is(""));
    }

    @Test
    void rejectsUnsupportedRequestTypeWithEmptyBody() {
        given().contentType("text/plain").accept("application/json").body("hello")
                .post(LOGIN).then().statusCode(415).body(is(""));
    }

    @Test
    void characterizesKnownSqlFailureBugAsHttp401() throws Exception {
        try (var connection = TestDatabase.connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO endpoint_unavailable_users");
            try {
                given().contentType("application/json")
                        .body(Map.of("username", "student", "password", "Workshop123!"))
                        .post(LOGIN).then().statusCode(401).body("$", aMapWithSize(1))
                        .body("message", is("Invalid user student or password"));
            } finally {
                statement.executeUpdate("ALTER TABLE endpoint_unavailable_users RENAME TO legacy_users");
            }
        }
    }

    @Test
    void characterizesKnownInternalErrorDisclosureBugOverHttp() throws Exception {
        TestDatabase.insert("invalid-hash-user", "test-only", "Invalid hash", "STUDENT");
        try {
            try (var connection = TestDatabase.connection();
                 var statement = connection.prepareStatement(
                         "UPDATE legacy_users SET password_hash=? WHERE username=?")) {
                statement.setString(1, "invalid-test-hash");
                statement.setString(2, "invalid-hash-user");
                assertEquals(1, statement.executeUpdate());
            }
            given().contentType("application/json")
                    .body(Map.of("username", "invalid-hash-user", "password", "test-only"))
                    .post(LOGIN).then().statusCode(500).body("$", aMapWithSize(2))
                    .body("message", is("Unsupported password hash format"))
                    .body("exception", is("java.lang.IllegalArgumentException"));
        } finally {
            deleteFixture("invalid-hash-user");
        }
    }

    private void deleteFixture(String username) throws SQLException {
        try (var connection = TestDatabase.connection();
             var statement = connection.prepareStatement("DELETE FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }
}
```

### HealthResourceTest.java

```java
package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class HealthResourceTest {
    private static final String HEALTH = "/q/health";

    @Test
    void reportsUpWithoutAuthenticationAndWithNoChecks() {
        assertUp(given().accept("application/json").get(HEALTH));
    }

    @Test
    void acceptsXmlPreferenceButStillReturnsJson() {
        assertUp(given().accept("application/xml").get(HEALTH));
    }

    @Test
    void characterizesDocumentedPostBehaviorWithoutInventing405() {
        assertUp(given().contentType("application/json").body("{}").post(HEALTH));
    }

    @Test
    void characterizesKnownHealthCoverageGapDespiteLoginFailure() throws Exception {
        TestDatabase.insert("health-invalid-hash", "test-only", "Health fixture", "STUDENT");
        try {
            try (var connection = TestDatabase.connection();
                 var statement = connection.prepareStatement(
                         "UPDATE legacy_users SET password_hash=? WHERE username=?")) {
                statement.setString(1, "invalid-test-hash");
                statement.setString(2, "health-invalid-hash");
                statement.executeUpdate();
            }
            given().contentType("application/json")
                    .body(Map.of("username", "health-invalid-hash", "password", "test-only"))
                    .post("/auth/login").then().statusCode(500)
                    .body("message", is("Unsupported password hash format"));
            assertUp(given().get(HEALTH));
        } finally {
            try (var connection = TestDatabase.connection();
                 var statement = connection.prepareStatement("DELETE FROM legacy_users WHERE username=?")) {
                statement.setString(1, "health-invalid-hash");
                statement.executeUpdate();
            }
        }
    }

    @Test
    void unavailableServiceIsTransportFailureNotHttpError() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        // Reserve a port without a listener: no unrelated process can receive the request.
        try (Socket reserved = new Socket();
             HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            reserved.bind(new InetSocketAddress(loopback, 0));
            URI endpoint = URI.create("http://127.0.0.1:" + reserved.getLocalPort() + HEALTH);
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(2)).GET().build();
            assertThrows(java.net.ConnectException.class,
                    () -> client.send(request, HttpResponse.BodyHandlers.discarding()));
        }
    }

    private void assertUp(io.restassured.response.Response response) {
        response.then().statusCode(200).contentType("application/json")
                .body("$", aMapWithSize(2)).body("status", is("UP")).body("checks", empty());
    }
}
```

Las pruebas nuevas reutilizan
[TestDatabase.java](../src/test/java/com/bcp/workshop/auth/TestDatabase.java).
El caso 500 con mensaje nulo conserva la prueba directa existente
[preservesExceptionResponses](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L96);
no se añadió una ruta artificial ni un servicio alternativo de producción
para provocar esa excepción.

## Comandos y resultados realmente ejecutados

Directorio de trabajo de todos los comandos Maven:

```text
C:\Users\Usuario\Documents\T22925-jose-ventura\reqflow-workspace\workshop-auth-backend
```

Selección de JDK 21 para Maven, sin cambiar el entorno global:

```powershell
Set-Location 'C:\Users\Usuario\Documents\T22925-jose-ventura\reqflow-workspace\workshop-auth-backend'
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
```

En esta máquina `java` resuelve al JDK 21. En otra máquina, seleccionar
explícitamente su instalación de JDK 21; no asumir que el comando resuelve
siempre la misma versión.

Ejecución dirigida:

```powershell
mvn '-Dtest=EndpointContractTest,HealthResourceTest,AuthResourceTest' test *> target\endpoint-tests-targeted.log
$code = $LASTEXITCODE
```

- Código de salida: **0**.
- **21 pruebas, 0 fallos, 0 errores, 0 omitidas**.
- `EndpointContractTest`: 10; `HealthResourceTest`: 5; `AuthResourceTest`: 6.
- Resultado: **BUILD SUCCESS**; tiempo Maven **17,300 s**.
- [Log dirigido](../target/endpoint-tests-targeted.log).

Verificación completa y cobertura:

```powershell
mvn verify *> target\endpoint-tests-verify.log
$code = $LASTEXITCODE
```

- Código de salida: **0**.
- **39 pruebas, 0 fallos, 0 errores, 0 omitidas**.
- Resultado: **BUILD SUCCESS**; tiempo Maven **17,245 s**.
- JaCoCo: **All coverage checks have been met.**
- [Log completo](../target/endpoint-tests-verify.log).
- Los stacktraces de los fallos SQL y hashes inválidos son provocados
  deliberadamente por los tests y registrados por el servidor; no equivalen
  a errores JUnit cuando las respuestas esperadas se verifican correctamente.

El ejecutor de pruebas integrado del editor se intentó primero, pero respondió
`No tests found in the files`. No se contabiliza como suite ejecutada ni
como éxito; por eso se utilizó Maven.

### Aislamiento y reproducibilidad

[pom.xml](../pom.xml) fija en Surefire una base de pruebas en
`target/auth-test.db`, contraseñas ficticias y 10000 iteraciones solo para
agilizar tests. El benchmark del hasher sigue probando 600000 iteraciones.
No se usa la base de `data/` ni se llama a otros componentes del workspace.

Los fixtures de hash inválido se eliminan en `finally`; el cambio temporal de
nombre de tabla también se restaura en `finally`. El runner actual no ejecuta
estos tests en paralelo: comparten deliberadamente la base del perfil test.
No habilitar paralelismo sobre esta base sin aislar esos fixtures.

QuarkusTest arranca y detiene el servicio de pruebas. El caso de transporte
reserva un puerto loopback sin listener, usa timeout de dos segundos y cierra
cliente y socket. No termina procesos de otros usuarios ni depende de un
puerto supuestamente libre elegido a mano.

## Cobertura: método, reporte y umbral

No hay cambios de producción en esta tarea. Para comprobar que se conserva
la meta del laboratorio se midió todo el código de producción local y se
ejecutó el gate existente sobre cada clase refactorizada, no solo un promedio.

JaCoCo 0.8.12 utiliza el agente durante los tests y genera el reporte en
`verify`; `append=false` evita acumular cobertura de ejecuciones anteriores.
La medida es `LINE`, calculada como:

```text
cobertura = líneas cubiertas / (líneas cubiertas + líneas no cubiertas) × 100
```

No confundir líneas con cobertura de ramas, número de tests o garantía de
ausencia de bugs. No se atribuye cobertura de SmallRye Health a código propio:
la extensión se comprueba por HTTP y no forma parte de las clases de la app.
JaCoCo no mide cobertura de los propios tests como si fuera producción.

| Clase de producción | Líneas cubiertas / totales | Cobertura |
|---|---|---|
| `AuthResource` | 25 / 25 | 100 % |
| `AuthResource.LoginResponse` | 1 / 1 | 100 % |
| `AuthResource.UserResponse` | 1 / 1 | 100 % |
| `AuthService` | 9 / 9 | 100 % |
| `LegacyDatabase` | 43 / 43 | 100 % |
| `LegacyTokenUtil` | 3 / 3 | 100 % |
| `PasswordHasher` | 28 / 30 | 93,33 % |
| `PasswordMigration` | 58 / 59 | 98,31 % |
| `StoredCredentials` | 2 / 2 | 100 % |
| `LoginRequest` | 1 / 1 | 100 % |
| `UserRecord` | 1 / 1 | 100 % |
| Total local | 172 / 175 | 98,29 % |

El gate de [pom.xml](../pom.xml) exige **85 % de líneas por clase** para
Resource y DTOs, servicio, repositorio, token, hasher, migración y credenciales.
El menor valor medido es **93,33 %**, superior al umbral. No se cambiaron
exclusiones, reglas de CodeQL, Dependency Review ni JaCoCo.

Reportes realmente generados:
[HTML](../target/site/jacoco/index.html),
[XML](../target/site/jacoco/jacoco.xml),
[CSV](../target/site/jacoco/jacoco.csv) y
[resultados Surefire](../target/surefire-reports).
Los porcentajes de esta tabla se obtuvieron de los contadores `LINE` del XML
de la ejecución completa, no del reporte dirigido anterior.

## Fallos, defectos, pendientes y propuestas: separados

### Fallos de ejecución

- Ningún fallo ni error JUnit en las dos ejecuciones Maven.
- El descubrimiento integrado del editor no detectó los tests Java; se reporta
  como limitación de herramienta, no como prueba del comportamiento del backend.
- No se ejecutaron scanners y no se atribuyen resultados a ellos.

### Defectos conocidos reproducidos, no corregidos

1. **Fallo SQL presentado como credenciales inválidas**:
   `characterizesKnownSqlFailureBugAsHttp401` reproduce el 401 documentado.
   La suite no afirma que ese manejo sea deseable. Seguimiento existente:
   H-05 de [HALLAZGOS.md](../HALLAZGOS.md).
2. **Detalles internos en HTTP 500**:
   `characterizesKnownInternalErrorDisclosureBugOverHttp` reproduce mensaje
   y nombre de clase expuestos. Sigue siendo H-05, no un hallazgo resuelto.
3. **Salud funcional no cubierta**:
   `characterizesKnownHealthCoverageGapDespiteLoginFailure` demuestra que
   `/q/health` sigue UP con checks vacíos aunque una autenticación falle por
   datos inválidos. Es la limitación ya descrita en [ENDPOINTS.md](ENDPOINTS.md),
   no un nuevo contrato de readiness ni una garantía de salud integral.

No se introdujeron aserciones condicionales, skips ni alternativas de
respuesta para hacer pasar estos casos. Los nombres declaran que son
caracterizaciones. Corregir esos comportamientos requiere una decisión de
contrato separada; no se modifican en esta tarea de pruebas.
No se crea otra matriz de estados: el seguimiento sigue en HALLAZGOS.

### Pendientes

- **Salud fallida con respuesta HTTP:** ENDPOINTS no define código ni cuerpo y
  no hay HealthCheck fallido propio. No hay test de un 503/DOWN inventado.
  Por ese motivo no puede declararse cubierta una respuesta de error de salud
  inexistente en el contrato actual. Sí se probó la ausencia de respuesta HTTP
  por conexión rechazada.
- **Perfil de producción:** los detalles de errores Jackson estaban observados
  en desarrollo; ahora también pasan en perfil test. No se afirma que el
  perfil de producción haya sido ejecutado ni que exponga los mismos detalles.

### Pruebas propuestas, no ejecutadas

- Si se acuerda un contrato de salud negativa, agregar su test contra una
  comprobación real con fallo controlado y el código/cuerpo que se apruebe.
  No se propone un estado concreto como si ya estuviera acordado.
- Caracterizar negociación y deserialización en producción con un paquete
  ejecutable y base aislada, antes de cerrar la pregunta abierta de ENDPOINTS.

## Documentos consultados

- [ENDPOINTS.md](ENDPOINTS.md): endpoints, campos, errores y preguntas abiertas.
- [REGLAS.md](../REGLAS.md): cobertura y conservación del contrato.
- [pom.xml](../pom.xml): dependencias existentes, aislamiento Surefire y gate.

También se consultaron
[AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
[AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java),
[LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java)
y [TestDatabase.java](../src/test/java/com/bcp/workshop/auth/TestDatabase.java)
para reutilizar el montaje y no crear endpoints o mecanismos ajenos al código.
