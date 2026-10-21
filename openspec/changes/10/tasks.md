# Tasks

## 1. Caracterizar el comportamiento actual

- [x] 1.1 Añadir y ejecutar pruebas de caracterización de `POST /auth/login` con una base SQLite aislada; registrar status, content type, campos de respuesta, forma pública de `user`, `debug`, valor actual de `expiresIn`, formato Base64 actual del token y comportamiento actual de `client` ausente o inválido, sin convertir esas diferencias en expectativas del contrato final.
- [x] 1.2 Caracterizar con pruebas el orden actual de validación para cuerpo nulo, username y password, y la respuesta actual para credenciales incorrectas; verificar HTTP y cuerpo observados y anotar cualquier diferencia contra `docs/ENDPOINTS.md`.
- [x] 1.3 Caracterizar el fallo de consulta SQLite y los mensajes HTTP/logs resultantes usando únicamente una base temporal; verificar que las pruebas no leen ni modifican `data/workshop.db` y registrar la diferencia frente al 500 genérico documentado.

Evidencia de caracterización (2026-10-21):

- Comando: `$env:AUTH_DB_URL="jdbc:sqlite:./target/issue10-characterization-<GUID>.db"; mvn '-Dtest=AuthResourceTest' '-Dquarkus.http.test-port=0' test`.
- Resultado: 5 pruebas, 0 fallos, 0 errores, 0 omitidas. Baseline previo: 1 prueba, 0 fallos, 0 errores.
- Observado: login sin `client`, con `client` null/vacío/espacios responde 200; tipo objeto responde 400; credenciales inválidas responden 401; una consulta SQLite fallida responde 401 y escribe el error en stderr; una excepción que alcanza directamente el Resource produce entidad 500 con mensaje y clase.
- La prueba que altera el esquema solo se ejecuta si `AUTH_DB_URL` identifica un archivo bajo `target/`; no accede a `data/workshop.db`.

## 2. Exigir y propagar `client`

- [x] 2.1 Añadir pruebas de contrato para `client` ausente, null, vacío y solo espacios; verificar HTTP 400 de aplicación y que no se emite token, conservando la precedencia de errores de cuerpo, username y password.
- [x] 2.2 Añadir prueba para `client` de tipo no string; verificar HTTP 400 sin fijar el cuerpo generado por Jackson y confirmar que no se emite token.
- [x] 2.3 Implementar la validación de `client` después de username/password y antes de autenticar; ejecutar las pruebas de 2.1 y 2.2 y verificar que un valor no vacío tras `trim()` se conserva sin normalización para incluirlo en el token.

Evidencia 2.1–2.2 (2026-10-21):

- Comando: `$env:AUTH_DB_URL="jdbc:sqlite:./target/issue10-characterization-<GUID>.db"; mvn '-Dtest=AuthResourceTest' '-Dquarkus.http.test-port=0' test`.
- Resultado: 6 pruebas, 0 fallos, 0 errores, 0 omitidas. Las dos nuevas pruebas también se ejecutaron antes de implementar la validación y falló la expectativa de 400 para `client` ausente (observado 200), confirmando el comportamiento que debían cambiar.

## 3. Emitir JWT RS256 con duración coherente

- [x] 3.1 Añadir pruebas con una clave RSA de prueba que verifiquen que el JWT tiene tres segmentos Base64URL, `alg=RS256`, firma válida y claims `sub`, `client`, `iat`, `exp`, `role`, `iss` y `aud` con los valores del contrato; verificar que no aparecen secretos ni material privado en el token.
- [x] 3.2 Sustituir la generación Base64 de `LegacyTokenUtil` por emisión RS256 usando las APIs criptográficas del JDK y el JSON runtime existente, sin añadir dependencia ni fallback legado; ejecutar las pruebas de estructura, firma y claims de 3.1.
- [x] 3.3 Añadir pruebas para clave de firma ausente o inválida y luego exigir la configuración externa `AUTH_TOKEN_PRIVATE_KEY`; verificar que no se emite token, no se usa secreto predeterminado o token Base64 y no se exponen ruta ni material de clave en errores/logs.
- [x] 3.4 Añadir pruebas que verifiquen `iat` como instante de emisión, `exp = iat + 3600` y `expiresIn` entero positivo coherente con el tiempo restante, con diferencia máxima de un segundo por redondeo; derivar esos valores de un solo instante de emisión y ejecutar las pruebas.
- [x] 3.5 Añadir una prueba de frontera de expiración que compruebe que el claim `exp` está expresado en segundos Unix y que un verificador de prueba clasifica el token como expirado después de ese instante; no añadir un endpoint de validación al backend.

## 4. Preservar respuesta, errores y documentación

- [x] 4.1 Ampliar las pruebas de éxito para verificar HTTP 200, los campos superiores `token`, `expiresIn`, `user` y `debug`, y que `user` solo expone `username`, `displayName` escapado y `role`; ejecutar las pruebas y conservar el contrato público existente.
- [x] 4.2 Añadir pruebas para usuario inexistente y password incorrecto con `client` válido; verificar HTTP 401 y el cuerpo documentado, y que ninguna respuesta contiene token.
- [x] 4.3 Añadir pruebas para fallos internos de autenticación/emisión; ajustar solo el manejo directamente necesario para conservar la distinción entre credenciales inválidas y fallos operativos, verificar HTTP 500 genérico y confirmar que respuestas y logs no exponen password, JWT, secreto, clave privada, clase de excepción ni detalles internos.
- [x] 4.4 Actualizar únicamente las secciones de `docs/ENDPOINTS.md` afectadas: `client` obligatorio y validado, claim `client`, semántica de `expiresIn` y configuración RS256 real con `AUTH_TOKEN_PRIVATE_KEY`; verificar que las demás rutas y el resto del contrato no cambian.

## 5. Verificación integrada

- [x] 5.1 Ejecutar la clase de pruebas de autenticación y corregir cualquier regresión en los escenarios del Issue; verificar éxito de login, credenciales inválidas, validación de `client`, firma/claims, expiración, coherencia de `expiresIn` y redacción de datos sensibles.
- [x] 5.2 Ejecutar la suite Maven completa y verificar que pasa sin modificar ramas, usar credenciales reales ni acceder a la base SQLite de datos de desarrollo.

Evidencia de implementación y verificación (2026-10-21):

- Pruebas enfocadas: `$env:AUTH_DB_URL="jdbc:sqlite:./target/issue10-characterization-<GUID>.db"; mvn '-Dtest=AuthResourceTest,TokenServiceTest' '-Dquarkus.http.test-port=0' test` — 10 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Suite y gates: mismo `AUTH_DB_URL`, `mvn '-Dquarkus.http.test-port=0' verify` — BUILD SUCCESS; 10 pruebas, 0 fallos, 0 errores, 0 omitidas; JaCoCo generó reporte sobre 6 clases.
- Cobertura de líneas JaCoCo en clases de producción modificadas: `TokenService` 42/47 (89.36%), `AuthResource` 27/27 (100%) y `LegacyDatabase` 19/21 (90.48%); cada una supera el mínimo local de 85%.
- La suite usa una SQLite desechable bajo `target/`; las claves RSA de prueba se generan bajo `target/test-keys` y se eliminan al cerrar el recurso. No se accedió a `data/workshop.db` ni a otros repositorios.
- RS256 fue confirmado como decisión del usuario; `spec.md` ya no deja pendiente la elección HMAC/RSA.
