# Spec Delta

## Purpose

Define the observable contract for issuing login tokens bound to a validated client and with a lifetime consistent with the token's expiration.

## ADDED Requirements

### Requirement: Login tokens identify the requesting client

El sistema SHALL exigir en la solicitud de login un `client` string no vacío tras aplicar `trim` y el token emitido para un login exitoso SHALL incluir un claim `client` con el mismo valor aceptado, sin normalizarlo.

#### Scenario: Login válido vincula el token con el cliente solicitado

- **WHEN** se envían credenciales válidas y un `client` string no vacío tras `trim` a `POST /auth/login`
- **THEN** la respuesta es HTTP 200 con la estructura de éxito documentada
- **AND** el JWT contiene el claim `client` con el mismo valor exacto recibido
- **AND** el JWT conserva los claims existentes `role`, `iss`, `aud`, además de `sub`, `iat` y `exp`

#### Scenario: Cliente ausente, nulo, vacío o compuesto por espacios

- **WHEN** se omite `client` o se envía como `null`, cadena vacía o cadena de caracteres que `String.trim()` elimina por completo
- **THEN** la solicitud se rechaza con HTTP 400 y respuesta JSON de error de aplicación
- **AND** no se emite un token

#### Scenario: Tipo de cliente incompatible

- **WHEN** el JSON presenta `client` con un tipo que no es string
- **THEN** la solicitud se rechaza como JSON de tipo incompatible con HTTP 400
- **AND** no se emite un token
- **AND** la forma exacta del cuerpo de error generado por el framework no se considera estable

### Requirement: Token duration is represented consistently

El sistema SHALL emitir tokens con claims `iat` y `exp` y responder `expiresIn` como el tiempo restante hasta `exp`, en segundos, con una tolerancia de un segundo por redondeo a segundos Unix.

#### Scenario: Login exitoso informa el tiempo de vida restante

- **WHEN** se completa un login exitoso
- **THEN** `expiresIn` es un entero positivo
- **AND** difiere como máximo en un segundo del tiempo restante entre la emisión y el `exp` del JWT
- **AND** el token conserva la expiración de una hora del contrato vigente, expresada como `exp = iat + 3600`

#### Scenario: Token emitido se considera expirado después de `exp`

- **WHEN** un consumidor verifica un token emitido después de su claim `exp`
- **THEN** el token se clasifica como expirado y no se acepta como vigente
- **AND** este escenario no añade validación de tokens al backend, cuya responsabilidad documentada es emitirlos

### Requirement: Login authentication behavior remains compatible

El sistema SHALL preservar el contrato documentado de `POST /auth/login`, salvo los cambios explícitos de exigencia/vinculación de `client` y representación de `expiresIn` definidos aquí.

#### Scenario: Credenciales inválidas conservan la respuesta documentada

- **WHEN** se envía un `client` válido con un usuario inexistente o una contraseña incorrecta
- **THEN** la respuesta es HTTP 401 con el cuerpo JSON de error documentado
- **AND** no se emite un token

#### Scenario: Respuesta exitosa conserva la identidad pública

- **WHEN** se completa un login exitoso
- **THEN** la respuesta conserva HTTP 200 y los campos superiores `token`, `expiresIn`, `user` y `debug`
- **AND** `user` solo contiene `username`, `displayName` escapado y `role`
- **AND** `user` no incluye identificadores internos, contraseñas ni hashes

### Requirement: Sensitive authentication material is not logged

El sistema SHALL evitar registrar tokens, material de firma, contraseñas u otros valores sensibles de autenticación.

#### Scenario: Los eventos de autenticación no exponen valores sensibles

- **WHEN** se procesan logins exitosos, credenciales inválidas o fallos de emisión de tokens
- **THEN** los registros no contienen el JWT, secretos o claves privadas de firma, ni contraseñas
- **AND** un error HTTP 500 conserva el mensaje genérico documentado sin revelar detalles internos

### Requirement: Token signing configuration remains explicit

El sistema SHALL documentar explícitamente la configuración de firma que admite y SHALL NOT depender de material de firma predeterminado y no documentado fuera del sandbox.

#### Scenario: La configuración no usa un fallback de firma no documentado

- **WHEN** se documenta o ejecuta el servicio fuera del sandbox
- **THEN** la configuración necesaria para firmar tokens está descrita explícitamente
- **AND** no se usa silenciosamente un valor predeterminado de firma
- **AND** la discrepancia del Issue entre HMAC/`AUTH_TOKEN_SECRET` y el baseline RSA/`AUTH_TOKEN_PRIVATE_KEY` queda pendiente de decisión técnica, no aprobada como cambio de algoritmo por esta especificación
