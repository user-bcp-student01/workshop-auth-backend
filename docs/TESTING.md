# Pruebas del contrato HTTP

Contrato de referencia: [ENDPOINTS.md](ENDPOINTS.md). Verificado el 2026-10-06
con JDK 21 y Quarkus 3.27.4. Solo se prueban los endpoints documentados.
Los nombres de metodo de la tabla corresponden al codigo reproducido abajo.
Cada metodo con un bucle ejecuta todas las variantes indicadas.

## Matriz

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | `preservesSuccessfulResponseForBothWorkshopUsers`: student y trainer con credenciales validas, sin client ni Authorization | Camino feliz | 200 JSON; token no vacio, expiresIn=3600, identidad y rol almacenados, debug literal; solo token/expiresIn/user/debug, sin password, hash, id ni client. |
| POST /auth/login | `escapesDisplayNameAndPreservesLongUnicodePassword`: password de mas de 100 caracteres con Unicode y nombre con HTML | Borde | 200; password exacto aceptado y displayName `&lt;Student &amp; Trainer&gt;`. No se inventa una longitud maxima. |
| POST /auth/login | `ignoresClientAndUnknownFields`: client y campo desconocido | Borde | 200; no afectan la autenticacion. |
| POST /auth/login | `preservesValidationOrderAndMessages`: username null, vacio, espacios o tabulacion/CR/LF, con password null | Borde / Error | 400 JSON exacto: message=`Username is required`; username tiene prioridad sobre password. |
| POST /auth/login | `preservesValidationOrderAndMessages`: password null, vacio, espacios o tabulacion/CR/LF con username valido | Borde / Error | 400 JSON exacto: message=`Password is required`. |
| POST /auth/login | `rejectsNullAndEmptyBodiesOverHttp`: JSON null y cuerpo vacio; `preservesValidationOrderAndMessages` tambien invoca el Resource con null | Borde / Error | 400 JSON exacto: message=`Request is null`. |
| POST /auth/login | `rejectsMissingRequiredFieldsInValidationOrder`: ambos campos omitidos o solo username omitido | Error | 400 JSON exacto: message=`Username is required`. |
| POST /auth/login | `rejectsMissingRequiredFieldsInValidationOrder`: password omitido con username valido | Error | 400 JSON exacto: message=`Password is required`. |
| POST /auth/login | `rejectsInvalidCredentialsWithoutTrimmingThem`: usuario inexistente o password incorrecto | Error | 401 JSON exacto: message=`Invalid user <username enviado> or password`. |
| POST /auth/login | `rejectsInvalidCredentialsWithoutTrimmingThem`: espacio inicial en username o final en password | Borde / Error | 401; trim no modifica las credenciales ni el username incluido en message. |
| POST /auth/login | `distinguishesCredentialCase`: Student o workshop123! | Borde / Error | 401; la comparacion distingue mayusculas de minusculas. |
| POST /auth/login | `rejectsInvalidStoredHash`: hash invalido de un usuario exclusivo de pruebas | Error | 401 con el mismo esquema y mensaje de credenciales invalidas; el fixture se elimina en finally. |
| POST /auth/login | `preservesUnauthorizedResponseForCaughtDatabaseFailure`: tabla temporalmente no disponible | Error | 401 con message=`Invalid user student or password`; no se espera 500 porque el contrato documenta este defecto. La tabla se restaura en finally. |
| POST /auth/login | `preservesInternalErrorResponse`: doble del servicio lanza IllegalStateException con mensaje | Error | 500; entidad exacta con message=`test failure` y exception=`java.lang.IllegalStateException`. |
| POST /auth/login | `preservesInternalErrorResponse`: la misma excepcion sin mensaje | Borde / Error | 500; entidad exacta con message=`Unknown error` y la misma clase. |
| POST /auth/login | `rejectsMalformedJsonAndIncompatibleFieldTypes`: JSON `{` o username como objeto | Error | 400 del framework en prod; cero bytes de cuerpo y sin Content-Type. |
| POST /auth/login | `rejectsUnsupportedMethod`: GET en lugar de POST | Error | 405 del framework en prod; cero bytes de cuerpo y sin Content-Type. |
| POST /auth/login | `rejectsIncompatibleAcceptHeader`: Accept text/plain | Error | 406 del framework en prod; cero bytes de cuerpo y sin Content-Type. |
| POST /auth/login | `rejectsUnsupportedContentType`: Content-Type text/plain | Error | 415 del framework en prod; cero bytes de cuerpo y sin Content-Type. |
| GET /q/health | `reportsHealthWithoutAuthentication`: GET sin cuerpo ni Authorization, Accept application/json | Camino feliz / Borde | 200 JSON exacto: status=`UP`, checks=[]; el array vacio es el borde observado, no una prueba de SQLite. |
| GET /q/health | `ignoresInvalidAuthorizationForHealth`: Bearer invalido | Borde | 200, mismo JSON; el endpoint no valida tokens. |
| GET /q/health | `preservesHealthWithPlainTextAcceptAndAdditionalQuery`: Accept text/plain o query client=test-only | Borde | 200 application/json, mismo JSON; no se extrapola el 406 del login ni se inventan filtros. |
| GET /q/health | Sin respuesta de error de aplicacion definida en el contrato | Error: no aplica | No se inventan tests de 401/403/405/503, DOWN ni cuerpos de error. Una conexion rechazada tampoco es una respuesta HTTP. |

## Codigo de los tests

### Resource y salud

Fuente ejecutable: [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
`@QuarkusTest` ejecuta HTTP real para login y salud. El 500 se comprueba
directamente en el Resource con un doble de servicio: el contrato no identifica
un payload HTTP que provoque esa excepcion. Se verifica su entidad, no una
serializacion HTTP de ese doble.

```java
package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuthResourceTest {
    @BeforeEach
    void prepareFixtures() throws Exception {
        new LegacyDatabase().initialize();
        LegacyDatabaseTest.insertUser("html-user", "long-password-" + "x".repeat(90) + "\u00e9", "<Student & Trainer>", "STUDENT");
    }

    @Test
    void preservesSuccessfulResponseForBothWorkshopUsers() {
        assertSuccess("student", "Workshop123!", "Workshop Student", "STUDENT");
        assertSuccess("trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    @Test
    void escapesDisplayNameAndPreservesLongUnicodePassword() {
        assertSuccess("html-user", "long-password-" + "x".repeat(90) + "\u00e9", "&lt;Student &amp; Trainer&gt;", "STUDENT");
    }

    @Test
    void ignoresClientAndUnknownFields() {
        Map<String, Object> body = new HashMap<>(credentials("student", "Workshop123!"));
        body.put("client", "angular-workshop");
        body.put("unknown", true);
        given().contentType("application/json").body(body).post("/auth/login").then().statusCode(200);
    }

    @Test
    void preservesValidationOrderAndMessages() {
        for (String username : new String[]{null, "", "   ", "\t\r\n"}) {
            assertError(credentials(username, null), 400, "Username is required");
        }
        for (String password : new String[]{null, "", "   ", "\t\r\n"}) {
            assertError(credentials("student", password), 400, "Password is required");
        }
        try (Response response = new AuthResource().login(null)) {
            assertEquals(400, response.getStatus());
            assertEquals(Map.of("message", "Request is null"), response.getEntity());
        }
    }

    @Test
    void rejectsNullAndEmptyBodiesOverHttp() {
        for (String body : new String[]{"null", ""}) {
            var response = given().contentType("application/json").body(body).post("/auth/login")
                    .then().statusCode(400).contentType("application/json").extract().jsonPath();
            assertEquals(Map.of("message", "Request is null"), response.getMap(""));
        }
    }

    @Test
    void rejectsMissingRequiredFieldsInValidationOrder() {
        assertError(Map.of(), 400, "Username is required");
        assertError(Map.of("password", "Workshop123!"), 400, "Username is required");
        assertError(Map.of("username", "student"), 400, "Password is required");
    }

    @Test
    void rejectsInvalidCredentialsWithoutTrimmingThem() {
        assertError(credentials("missing", "Workshop123!"), 401, "Invalid user missing or password");
        assertError(credentials("student", "wrong"), 401, "Invalid user student or password");
        assertError(credentials(" student", "Workshop123!"), 401, "Invalid user  student or password");
        assertError(credentials("student", "Workshop123! "), 401, "Invalid user student or password");
    }

    @Test
    void distinguishesCredentialCase() {
        assertError(credentials("Student", "Workshop123!"), 401, "Invalid user Student or password");
        assertError(credentials("student", "workshop123!"), 401, "Invalid user student or password");
    }

    @Test
    void rejectsInvalidStoredHash() throws Exception {
        LegacyDatabaseTest.insertUser("rejected-hash-user", "test-password", "Test User", "STUDENT");
        try {
            LegacyDatabaseTest.execute("UPDATE legacy_users SET password_hash='invalid-hash' WHERE username='rejected-hash-user'");
            assertError(credentials("rejected-hash-user", "test-password"), 401,
                    "Invalid user rejected-hash-user or password");
        } finally {
            LegacyDatabaseTest.execute("DELETE FROM legacy_users WHERE username='rejected-hash-user'");
        }
    }

    @Test
    void preservesInternalErrorResponse() {
        for (String message : new String[]{"test failure", null}) {
            AuthResource resource = new AuthResource();
            resource.service = new AuthService(null, null) {
                @Override
                public LoginResponse login(String username, String password) {
                    throw new IllegalStateException(message);
                }
            };
            try (Response response = resource.login(request("student", "Workshop123!"))) {
                assertEquals(500, response.getStatus());
                assertEquals(Map.of("message", message == null ? "Unknown error" : message,
                        "exception", IllegalStateException.class.getName()), response.getEntity());
            }
        }
    }

    @Test
    void preservesUnauthorizedResponseForCaughtDatabaseFailure() throws Exception {
        LegacyDatabaseTest.withMissingTable(() ->
                assertError(credentials("student", "Workshop123!"), 401, "Invalid user student or password"));
    }

    @Test
    void reportsHealthWithoutAuthentication() {
        assertHealth(given().accept("application/json").get("/q/health").then());
    }

    @Test
    void ignoresInvalidAuthorizationForHealth() {
        assertHealth(given().accept("application/json")
                .header("Authorization", "Bearer test-only-not-a-token").get("/q/health").then());
    }

    @Test
    void preservesHealthWithPlainTextAcceptAndAdditionalQuery() {
        assertHealth(given().accept("text/plain").get("/q/health").then());
        assertHealth(given().accept("application/json").queryParam("client", "test-only")
                .get("/q/health").then());
    }

    private void assertHealth(ValidatableResponse response) {
        assertEquals(Map.of("status", "UP", "checks", java.util.List.of()),
                response.statusCode(200).contentType("application/json").extract().jsonPath().getMap(""));
    }

    private void assertSuccess(String username, String password, String displayName, String role) {
        var response = given().contentType("application/json").body(credentials(username, password))
                .post("/auth/login").then().statusCode(200).contentType("application/json").extract().jsonPath();
        Map<String, Object> body = response.getMap("");
        assertEquals(Set.of("token", "expiresIn", "user", "debug"), body.keySet());
        assertFalse(((String) body.get("token")).isBlank());
        assertEquals(3600, body.get("expiresIn"));
        assertEquals("authenticated-with-legacy-flow", body.get("debug"));
        assertEquals(Map.of("username", username, "displayName", displayName, "role", role), body.get("user"));
    }

    private void assertError(Map<String, Object> body, int status, String message) {
        var response = given().contentType("application/json").body(body).post("/auth/login")
                .then().statusCode(status).contentType("application/json").extract().jsonPath();
        assertEquals(Map.of("message", message), response.getMap(""));
    }

    private Map<String, Object> credentials(String username, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return body;
    }

    private LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.username = username;
        request.password = password;
        return request;
    }
}
```

### Errores del framework en prod

Fuente ejecutable: [AuthFrameworkIT.java](../src/test/java/com/bcp/workshop/auth/AuthFrameworkIT.java).
`@QuarkusIntegrationTest` inicia el artefacto empaquetado en perfil prod.
En test se observo un cuerpo de 90 bytes para el 400 por tipo incompatible;
el contrato solo fija el cuerpo observado en prod. No se relaja la asercion:
los cuatro errores se verifican en el perfil documentado.

```java
package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusIntegrationTest
class AuthFrameworkIT {
    @Test
    void rejectsMalformedJsonAndIncompatibleFieldTypes() {
        for (String body : new String[]{"{", "{\"username\":{},\"password\":\"Workshop123!\"}"}) {
            assertEmptyFrameworkError(given().contentType("application/json").body(body)
                    .post("/auth/login").then(), 400);
        }
    }

    @Test
    void rejectsUnsupportedMethod() {
        assertEmptyFrameworkError(given().get("/auth/login").then(), 405);
    }

    @Test
    void rejectsIncompatibleAcceptHeader() {
        assertEmptyFrameworkError(given().contentType("application/json").accept("text/plain")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .post("/auth/login").then(), 406);
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertEmptyFrameworkError(given().contentType("text/plain").accept("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .post("/auth/login").then(), 415);
    }

    private void assertEmptyFrameworkError(ValidatableResponse response, int status) {
        var error = response.statusCode(status).extract().response();
        assertEquals(0, error.asByteArray().length);
        assertNull(error.getHeader("Content-Type"));
    }
}
```

## Ejecucion y aislamiento

Desde la raiz de este backend, con JDK 21 y Maven 3.9+:

```powershell
mvn '-Dtest=AuthResourceTest' test
mvn '-Dtest=AuthResourceTest' '-Dit.test=AuthFrameworkIT' verify
```

Validacion completa con cobertura nueva, sin borrar otras evidencias de target:

```powershell
Remove-Item -Path target/jacoco.exec -ErrorAction SilentlyContinue
mvn verify
```

[pom.xml](../pom.xml) configura Surefire para las pruebas normales, el build
de Quarkus en package y Failsafe para ejecutar y verificar las `*IT`.
`mvn test` no ejecuta la integracion prod; para todos los errores se necesita
`mvn verify`, que tambien es el comando de [CI](../.github/workflows/ci.yml).
No se cambiaron workflows ni se desactivo JaCoCo.

Surefire usa `AUTH_DB_URL=jdbc:sqlite:./target/test-databases/characterization.db`;
Failsafe usa `jdbc:sqlite:./target/test-databases/http-contract-prod.db` y
`QUARKUS_HTTP_HOST=127.0.0.1`. Las credenciales son exclusivamente de laboratorio.
Se reutilizan los helpers de [LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java)
para insertar usuarios y restaurar la tabla tras el fallo inducido.
No se usa ni se modifica data/workshop.db. La integracion requiere el puerto
de pruebas 8081 libre; Quarkus inicia y detiene sus procesos automaticamente.

## Resultado y cobertura

Ultima ejecucion: `mvn verify`, 2026-10-06, BUILD SUCCESS.

- Surefire: 31 tests, cero fallos, errores y omitidos; 14 son AuthResourceTest.
- Failsafe: 4 tests, cero fallos, errores y omitidos; todos son AuthFrameworkIT.
- Total: 35 tests verdes, incluidos 18 metodos de contrato de endpoints.
- JaCoCo fresco: 162/162 lineas (100%), 56/58 ramas (96,55%) y 720/720
  instrucciones (100%). AuthResource: 22/22 lineas; AuthService: 12/12.
- Meta de [REGLAS.md](../REGLAS.md): 85% o superior. No se modifico codigo de
  produccion; las metricas anteriores corresponden al codigo de aplicacion,
  no a los tests, Markdown o XML, que JaCoCo no instrumenta en este proyecto.

Informes generados: [JaCoCo HTML](../target/site/jacoco/index.html),
[JaCoCo XML](../target/site/jacoco/jacoco.xml),
[Surefire](../target/surefire-reports/com.bcp.workshop.auth.AuthResourceTest.txt)
y [Failsafe](../target/failsafe-reports/com.bcp.workshop.auth.AuthFrameworkIT.txt).
El proceso prod de integracion no se instrumenta; los errores previos al
Resource verifican el contrato HTTP, no aumentan la cobertura de codigo local.

## Hallazgos separados de las expectativas

No se detectaron nuevas discrepancias respecto del contrato en su perfil
documentado. La diferencia del 400 entre test y prod esta contemplada por
ENDPOINTS.md y no se presenta como un bug ni se oculta en una asercion mas laxa.

Los defectos ya registrados siguen abiertos en [HALLAZGOS.md](../HALLAZGOS.md):
el WAL puede conservar passwords legacy (H-02), el token no esta firmado (H-03)
y los errores revelan detalles internos o convierten fallos de consulta en 401
(H-05). Estas pruebas caracterizan el contrato actual; no corrigen ni certifican
el cierre de esos hallazgos. Cobertura alta y salud UP no prueban seguridad ni
limpieza de WAL. No se ejecutaron los checks alojados de CodeQL/Dependency Review.

## Documentos consultados

- [ENDPOINTS.md](ENDPOINTS.md): rutas, payloads, respuestas y limites por perfil.
- [REGLAS.md](../REGLAS.md): conservacion del contrato y meta de cobertura.
- [README.md](../README.md): requisitos y aislamiento del laboratorio.
- [HALLAZGOS.md](../HALLAZGOS.md): defectos existentes y limites de verificacion.
- [CI](../.github/workflows/ci.yml): gate local equivalente, `mvn verify`.
- [pom.xml](../pom.xml) y [application.properties](../src/main/resources/application.properties):
  versiones, runners, cobertura y configuracion HTTP.

Codigo consultado: [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
[AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
y [LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java).