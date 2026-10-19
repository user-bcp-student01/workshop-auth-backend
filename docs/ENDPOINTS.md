# Contrato observado de autenticación

Documento generado a partir del código existente, sin modificarlo.
Describe el comportamiento actual, incluidas sus limitaciones; no propone
cambiar la interfaz. Los casos del Resource se distinguen de las respuestas
que pueda producir Quarkus antes de invocarlo.

## POST /auth/login

- URL local: `http://localhost:8080/auth/login`.
- Entrada y salida del Resource: `application/json`.
- Fuente: [AuthResource.java:15](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15).

### Petición

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

| Campo | Tipo declarado | Comportamiento del código |
|---|---|---|
| `username` | String | Obligatorio; no puede ser nulo, vacío ni quedar vacío tras `trim()`. El valor original, sin recortar, se usa en la consulta. |
| `password` | String | Obligatorio; mismas comprobaciones de vacío. El valor original se usa en la consulta. |
| `client` | String | Opcional; no se valida ni se utiliza para autenticar o generar el token. |

Fuentes: [LoginRequest.java:3](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3),
[AuthResource.java:26](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26)
y [AuthResource.java:35](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L35).
Las propiedades JSON desconocidas no se rechazan por configuración:
[application.properties:8](../src/main/resources/application.properties#L8).
El orden de validación es petición, usuario y contraseña.

### Respuesta satisfactoria: HTTP 200

```json
{
  "token": "<cadena generada por el backend>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

- `token`: cadena; actualmente Base64 estándar de los bytes UTF-8 de
  `username|role|expiry|SECRET`. `expiry` es el instante actual más 3600
  segundos, expresado como segundos desde epoch. No es un token firmado.
  No se reproduce el valor del secreto en este documento.
- `expiresIn`: número entero `3600`, en segundos.
- `user`: objeto con `username`, `displayName` y `role` de la fila encontrada.
  `displayName` se transforma con `StringEscapeUtils.escapeHtml4`.
  No incluye `id` ni contraseña.
- `debug`: cadena literal `authenticated-with-legacy-flow`.
- El orden de las propiedades JSON no está definido por el Resource.

Fuentes: [AuthResource.java:39](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39),
[AuthResource.java:41](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L41),
[AuthResource.java:48](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L48)
y [LegacyTokenUtil.java:12](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L12).

### Respuestas de error construidas por el Resource

| HTTP | Condición | Cuerpo JSON |
|---|---|---|
| 400 | El argumento `request` recibido es nulo | `{"message":"Request is null"}` |
| 400 | `username` nulo, vacío o compuesto solo por caracteres eliminados por `trim()` | `{"message":"Username is required"}` |
| 400 | Usuario válido para la validación anterior, pero `password` nulo o vacío según `trim()` | `{"message":"Password is required"}` |
| 401 | La consulta devuelve `null` | `{"message":"Invalid user <username> or password"}` |
| 500 | Una excepción llega al `catch` de `login` | `{"message":"<mensaje de la excepción>","exception":"<nombre completo de la clase>"}` |

En HTTP 401, `<username>` se sustituye por el usuario recibido, sin normalizar.
En HTTP 500, si el mensaje de la excepción es nulo se usa `Unknown error`.
La consulta también devuelve `null` cuando captura una excepción, por lo que
esos fallos de persistencia se presentan actualmente como HTTP 401.

Fuentes: [AuthResource.java:26](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26),
[AuthResource.java:37](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L37),
[AuthResource.java:54](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54)
y [LegacyDatabase.java:39](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L39).

No se deduce un cuerpo exacto para JSON malformado, tipos incompatibles,
cuerpo HTTP ausente, tipos de contenido no soportados u otros rechazos
previos al método: dependen del procesamiento de Quarkus/Jackson y no están
caracterizados por las pruebas existentes.

## Configuración relacionada

- `AUTH_DB_URL`: leído del entorno, con valor predeterminado
  `jdbc:sqlite:./data/workshop.db`.
- Al inicializar la base se crean, si no existen, los usuarios de laboratorio
  `student` y `trainer` mediante `INSERT OR IGNORE`; no se garantiza que una
  base preexistente conserve sus valores originales.
- Puerto `8080`; host `127.0.0.1`, y `0.0.0.0` en perfil de producción.
- CORS habilitado: orígenes `/.*/`, métodos `GET,POST,PUT,DELETE,OPTIONS` y
  cabeceras `accept,authorization,content-type,x-requested-with`.
- El secreto del token y su plazo no se leen actualmente de configuración.

Fuentes: [LegacyDatabase.java:15](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L15),
[LegacyDatabase.java:22](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L22),
[application.properties:1](../src/main/resources/application.properties#L1)
y [LegacyTokenUtil.java:8](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8).

Según [README.md](../README.md), se ejecuta desde la raíz del backend con
JDK 21, Maven 3.9+ y `mvn quarkus:dev`, únicamente en un ambiente local aislado.

## Evidencia y límites

La única prueba existente de login,
[AuthResourceTest.java:13](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L13),
comprueba HTTP 200, `user.username` igual a `student` y `token` no nulo.
El resto de esta descripción procede de análisis estático; no de ejecución.
No se inspeccionó ni se presupone el comportamiento de consumidores externos.
El seguimiento de problemas se mantiene solo en [HALLAZGOS.md](../HALLAZGOS.md).
