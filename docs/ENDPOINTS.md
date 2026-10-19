# Integración con el backend de autenticación

Contrato del código actual, verificado el 2026-10-19. Este documento distingue
respuestas implementadas por la aplicación de respuestas gestionadas por
Quarkus. No presupone el comportamiento de otros componentes.

## Preparación para integrar

- Base URL local predeterminada: `http://localhost:8080`.
- Requisitos: JDK 21, Maven 3.9+ y directorio padre de la base SQLite existente.
- Para crear una base nueva se deben configurar
  `AUTH_SEED_STUDENT_PASSWORD` y `AUTH_SEED_TRAINER_PASSWORD`. Los ejemplos de
  login de este documento suponen los valores de laboratorio
  `Workshop123!` y `Trainer123!`, respectivamente.
- Si los usuarios ya existen, la inicialización conserva sus contraseñas,
  nombres y roles; no asumir que una base preexistente acepta los ejemplos.
- `AUTH_DB_URL` selecciona la base; por defecto,
  `jdbc:sqlite:./data/workshop.db`. Una tabla legado se migra a hashes al
  arrancar. Fallos de migración, esquema desconocido o contraseñas de siembra
  ausentes para usuarios nuevos pueden impedir que el servicio arranque:
  no son respuestas HTTP de login.
- Ejecutar con `mvn quarkus:dev`. **Limitación comprobada:** con el manifiesto
  actual, el plugin puede omitir el arranque por no tener configurado el goal
  `build`. La verificación de este documento utilizó
  `mvn quarkus:dev "-Dquarkus.enforceBuildGoal=false"` sin modificar el código.

Fuentes: [README.md](../README.md),
[pom.xml](../pom.xml#L66),
[application.properties](../src/main/resources/application.properties#L1) y
[LegacyDatabase.initialize](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L41).

## POST /auth/login

### 1. Qué hace

Autentica a un usuario del laboratorio con sus credenciales y entrega su
identidad y un token para continuar el flujo de la aplicación.

Fuente: [AuthResource.login](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L27)
y [AuthService.authenticate](../src/main/java/com/bcp/workshop/auth/AuthService.java#L17).

### 2. Autenticación y headers

No requiere autenticación previa. No exige ni valida `Authorization`; enviar
un Bearer no sustituye el usuario y contraseña del cuerpo.

```http
POST /auth/login HTTP/1.1
Content-Type: application/json
Accept: application/json
```

`Content-Type: application/json` corresponde al cuerpo JSON. `Accept:
application/json` permite recibir el formato que produce el Resource; también
se puede omitir `Accept`. No hay cookies ni cabecera de sesión añadidas por
el código del Resource.

Fuentes: [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15)
y [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3).

### 3. Request

Objeto JSON:

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| `username` | string | Sí | Usuario exacto a buscar. No admite `null`, cadena vacía ni solo caracteres que elimina `String.trim()`. No se recorta ni normaliza antes de autenticar. |
| `password` | string | Sí | Contraseña exacta. Misma comprobación de vacío que el usuario; espacios significativos se conservan. Se verifica contra el hash almacenado, no mediante SQL. El código no impone el límite de 72 bytes de BCrypt. |
| `client` | string o `null` | No | Identificador del cliente. Se recibe pero no se valida ni se usa para autenticar o generar el token. |

Ejemplo completo para una base sembrada con las credenciales indicadas:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

Se aceptan propiedades JSON adicionales por configuración. El orden de
validación es cuerpo nulo, usuario y contraseña; si fallan ambos campos se
responde primero por el usuario. Omitir `client` no cambia el resultado.
Las entradas de tipo incorrecto quedan sujetas a Jackson: no se garantiza
conversión de números, booleanos, arrays u objetos a string.

Fuentes: [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3),
[AuthResource.validate](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L47),
[application.properties:8](../src/main/resources/application.properties#L8)
y [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L36).

### 4. Response exitosa

**HTTP 200**, `Content-Type: application/json` (puede incluir charset UTF-8).

| campo | tipo | descripción |
|---|---|---|
| `token` | string | Token legado Base64 estándar. No es JWT ni está firmado. Su contenido es UTF-8 de `username\|role\|expiry\|secret`, donde `expiry` es tiempo Unix en segundos. No tratar la codificación como cifrado. |
| `expiresIn` | integer | Siempre `3600`, en segundos. Coincide con el plazo usado por el emisor. No implica que este backend valide tokens después. |
| `user` | object | Identidad pública. Solo contiene los tres campos siguientes; no contiene ID, contraseña ni hash. |
| `user.username` | string | Nombre de usuario recuperado de la base. |
| `user.displayName` | string | Nombre visible transformado mediante `StringEscapeUtils.escapeHtml4`. Por ejemplo, `<b>A & B</b>` se devuelve como `&lt;b&gt;A &amp; B&lt;/b&gt;`. |
| `user.role` | string | Rol almacenado. La siembra usa `STUDENT` y `TRAINER`, pero el código no impone un enum para toda base existente. |
| `debug` | string | Valor literal `authenticated-with-legacy-flow`, conservado en la interfaz actual. |

Ejemplo real completo capturado con el código actual y una base aislada:

```json
{
  "token": "c3R1ZGVudHxTVFVERU5UfDE3OTI0NTAxNTR8V09SS1NIT1BfU0VDUkVUXzIwMjY=",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El token del ejemplo es exclusivamente de laboratorio: contiene el vencimiento
`1792450154`, no debe reutilizarse ni esperarse como valor fijo. Cada emisión
calcula `expiry = Instant.now().plusSeconds(3600).getEpochSecond()`.
El orden de propiedades JSON no forma parte del contrato.

Fuentes: [AuthResource.success](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L60),
[DTOs de respuesta](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L66),
[LegacyTokenUtil.create](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13)
y [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L36).

### 5. Respuestas de error

#### Errores implementados por la aplicación

Las respuestas siguientes son JSON. No hay un objeto `error` envolvente.

| código | cuándo ocurre | ejemplo completo de cuerpo |
|---|---|---|
| 400 | El argumento de petición es nulo. También se observó con JSON `null` y cuerpo HTTP ausente. | `{"message":"Request is null"}` |
| 400 | `username` ausente, `null`, vacío o solo caracteres eliminados por `trim()`. Incluye `{}`. | `{"message":"Username is required"}` |
| 400 | El usuario pasa la validación, pero `password` falta, es `null` o está vacío según `trim()`. | `{"message":"Password is required"}` |
| 401 | Usuario inexistente o contraseña incorrecta. El nombre se interpola literalmente en el mensaje. | `{"message":"Invalid user student or password"}` |
| 401 | La consulta SQLite captura un `SQLException` y devuelve `null`. El cliente recibe la misma respuesta que para credenciales inválidas; no puede distinguir las causas. | `{"message":"Invalid user student or password"}` |
| 500 | Una excepción llega al `catch` del Resource. Ejemplo real: hash almacenado con formato inválido en una base de prueba. | `{"message":"Unsupported password hash format","exception":"java.lang.IllegalArgumentException"}` |
| 500 | La excepción capturada no tiene mensaje; se usa el literal `Unknown error`. Ejemplo verificado mediante el doble de servicio de la prueba existente. | `{"message":"Unknown error","exception":"java.lang.IllegalStateException"}` |

Para HTTP 500, `message` es el mensaje real de la excepción y `exception`
su nombre completo de clase; no son constantes ni una lista cerrada de clases.
La aplicación registra la excepción en el servidor.

Fuentes: [AuthResource.login](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L27),
[LegacyDatabase.findByUsername](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L52),
[PasswordHasher.verify](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L40),
[prueba de fallo SQL](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java#L26)
y [pruebas de HTTP 500](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L96).

#### Rechazos gestionados por Quarkus/Jackson

Resultados observados en perfil de desarrollo con las dependencias actuales;
no confundirlos con los mensajes anteriores:

| código | cuándo ocurre y petición verificada | ejemplo de cuerpo observado |
|---|---|---|
| 400 | JSON malformado: cuerpo `{`. | Cuerpo vacío: cero bytes, no `{}` ni `null`. |
| 400 | Tipo de raíz incompatible: cuerpo `[]`. | Cuerpo vacío: cero bytes. |
| 400 | Tipo de campo incompatible: `{"username":{},"password":"x"}`. | `{"objectName":"LoginRequest","attributeName":"username","line":1,"column":13,"value":null}` |
| 405 | Método no implementado en esta ruta; se comprobó `GET /auth/login`. | Cuerpo vacío: cero bytes. |
| 406 | `Accept: application/xml`, incompatible con la salida declarada, con un POST JSON. | Cuerpo vacío: cero bytes. |
| 415 | `Content-Type: text/plain` con cuerpo `hello`. | Cuerpo vacío: cero bytes. |

Los errores vacíos observados no incluían `Content-Type`. El detalle de
deserialización con `objectName`, `attributeName`, `line`, `column` y `value`
lo genera el framework: las posiciones corresponden exactamente al ejemplo,
no son valores constantes.

**Pregunta abierta:** ¿qué detalles de deserialización y cuerpos de error se
deben garantizar en producción? No existe un `ExceptionMapper` propio en este
repositorio que los estabilice; la verificación HTTP se realizó en desarrollo.
No asumir que el mismo detalle está habilitado en otros perfiles o versiones.
Los errores adicionales de proxy, límites HTTP o infraestructura no están
definidos por el código de esta aplicación y no se inventan aquí.

### 6. Notas de seguridad

- La refactorización **no convirtió el token en uno firmado**. Continúa siendo
  Base64 reversible e incluye un secreto fijo. No hay verificación de firma,
  emisor o audiencia en este backend.
- El emisor añade una fecha de expiración, pero este backend no contiene una
  ruta o filtro que valide la vigencia de tokens recibidos. No afirmar que
  `expiresIn` demuestra una expiración efectivamente impuesta por consumidores.
- Las contraseñas ahora se almacenan y verifican con PBKDF2-HMAC-SHA256,
  salt aleatorio individual, clave de 32 bytes y comparación
  `MessageDigest.isEqual`. Coste por defecto: 600000 iteraciones, configurable
  mediante `AUTH_PASSWORD_ITERATIONS`; cada hash conserva su coste.
- La búsqueda de usuario y las inserciones usan parámetros JDBC.
- HTTP 500 aún expone clase y mensaje internos; no mostrar indiscriminadamente
  esos detalles a usuarios finales. El 401 ante fallo SQL puede ocultar una
  incidencia de infraestructura.
- CORS sigue habilitado para cualquier origen (`/.*/`), con métodos
  `GET,POST,PUT,DELETE,OPTIONS` y cabeceras
  `accept,authorization,content-type,x-requested-with`.
- Ejecución exclusivamente local y aislada; no enviar credenciales reales.
  La configuración predeterminada no proporciona HTTPS. El host normal es
  `127.0.0.1`; el perfil de producción configura `0.0.0.0`.

Fuentes: [PasswordHasher.java](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L17),
[LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L52),
[LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8)
y [application.properties](../src/main/resources/application.properties#L1).

## GET /q/health

### 1. Qué hace

Permite a una integración comprobar que el servicio responde a su mecanismo
de salud; no certifica que las credenciales de los usuarios ni sus dependencias
funcionen correctamente.

La ruta la proporciona la extensión
[`quarkus-smallrye-health`](../pom.xml#L40), no un Resource de la aplicación.
No se encontraron implementaciones locales de `HealthCheck`, readiness o
liveness. En la ejecución actual el conjunto de comprobaciones está vacío.

### 2. Autenticación y headers

No requiere autenticación ni `Authorization`. Se verificó sin ese header.

```http
GET /q/health HTTP/1.1
Accept: application/json
```

No requiere `Content-Type`, porque no se envía cuerpo. La respuesta observada
es JSON incluso al solicitar `Accept: application/xml`; no aplicar por
analogía las reglas de negociación del Resource de login.

Fuente local: [dependencia de salud](../pom.xml#L40) y
[configuración HTTP](../src/main/resources/application.properties#L1);
no se configuran políticas de autenticación HTTP para esta ruta.

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| Cuerpo | No aplica | No | No se envía cuerpo JSON. |
| Parámetros de ruta | No aplica | No | No hay campos variables en `/q/health`. |
| Parámetros de consulta | No aplica | No | Ninguno requerido por la configuración local. |

**Ejemplo completo:** `GET http://localhost:8080/q/health`, sin cuerpo.
No existe un ejemplo JSON de request aplicable: enviar `{}` o `null`
inventaría un cuerpo requerido que este GET no tiene.

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json

```

### 4. Response exitosa

**HTTP 200**, `Content-Type: application/json; charset=UTF-8`, observado:

| campo | tipo | descripción |
|---|---|---|
| `status` | string | Estado agregado informado por SmallRye Health. El valor observado en esta aplicación es `UP`. |
| `checks` | array | Comprobaciones registradas. Actualmente se observó `[]`; no hay un esquema de elementos propio implementado por la aplicación. |

Ejemplo real completo:

```json
{
  "status": "UP",
  "checks": []
}
```

No hay envolvente adicional, campos de usuario ni token.
Durante la verificación, un hash deliberadamente inválido en la base aislada
produjo HTTP 500 en login mientras salud siguió devolviendo este mismo
HTTP 200 con `checks: []`.

### 5. Respuestas de error

El código de aplicación no construye respuestas de error para `/q/health`,
ni registra comprobaciones que permitan reproducir un estado de salud fallido.
En las peticiones comprobadas no se obtuvo ningún error de este endpoint.
Por tanto, no se presenta un cuerpo de error ficticio como si estuviera
implementado o verificado.

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| No determinado | Posible error generado por SmallRye Health si en el futuro se registra una comprobación fallida; no hay un caso local actual que lo produzca. | No verificado; pregunta abierta, sin ejemplo inventado. |
| Sin respuesta HTTP | El proceso no arranca o no es accesible; por ejemplo, falla la inicialización de SQLite. Es un error de conexión del cliente, no una respuesta 500/503 de `/q/health`. | No existe cuerpo HTTP. |

**Pregunta abierta:** ¿se quiere incorporar y caracterizar un estado no
saludable, incluido su código HTTP y cuerpo? No se documenta HTTP 503 con
`DOWN` como comportamiento confirmado del código local sin una comprobación
que lo demuestre. Esa ampliación requeriría otra tarea, no esta edición.
Tampoco se presupone HTTP 405 para otros métodos: en la versión comprobada,
un POST a esta ruta devolvió el mismo HTTP 200 de salud.

Fuente del fallo de arranque:
[LegacyDatabase.initialize](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L41).
Los demás comportamientos de salud proceden de la extensión declarada en
[pom.xml](../pom.xml#L40), no de un handler propio.

### 6. Notas de seguridad

- Ruta pública. No envía ni necesita credenciales; no pasar tokens por query.
- `UP` con `checks: []` no prueba acceso a SQLite, validez de hashes ni
  autenticación exitosa. No usarlo como garantía integral del login.
- No muestra contraseñas ni tokens en el cuerpo observado.
- Aplican las mismas restricciones de red, CORS y uso aislado descritas para
  login. El perfil de producción escucha en todas las interfaces según la
  configuración local; no asumir que este endpoint queda protegido por ello.

## Evidencia de verificación y documentos consultados

Verificación HTTP local con el código actual en perfil de desarrollo, puerto
temporal 18089, SQLite independiente bajo `target/` y credenciales ficticias.
No se modificaron fuentes, dependencias ni la base de `data/`. El proceso
temporal se detuvo al terminar las peticiones. Observaciones completas:
[target/endpoint-docs-observations.json](../target/endpoint-docs-observations.json).
Ese archivo es evidencia de esta ejecución, no una dependencia de integración.

Documentos consultados:

- Versión anterior de este documento, como referencia del contrato legado.
- [README.md](../README.md): ejecución, configuración, migración y límites.
- [pom.xml](../pom.xml): versiones, extensión de salud y configuración del build.

Código y pruebas contrastados:

- [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java),
  [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java)
  y [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  campos, DTOs, validación, mensajes y casos de error.
- [AuthService.java](../src/main/java/com/bcp/workshop/auth/AuthService.java),
  [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java)
  y [PasswordHasher.java](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java):
  autenticación, persistencia, hashing y propagación de errores.
- [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java)
  y [application.properties](../src/main/resources/application.properties):
  token real, duración, red, CORS y configuración.

Las referencias explican la procedencia del contrato; no es necesario leerlas
para construir las peticiones y manejar las respuestas descritas aquí.
