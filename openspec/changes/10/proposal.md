# Proposal

## Why

GitHub Issue [#10](https://github.com/user-bcp-student01/workshop-auth-backend/issues/10) busca que los tokens de login estén vinculados al cliente solicitado y que el tiempo de vida anunciado por la respuesta sea verificable contra `exp`. Esto permite a los consumidores identificar el destino previsto del token y evita depender de una duración declarada que no corresponda a su vencimiento real.

El Issue describe una configuración HMAC con `AUTH_TOKEN_SECRET`, mientras el contrato de Dia 1 documenta tokens RS256 y `AUTH_TOKEN_PRIVATE_KEY` obligatoria. Esta discrepancia debe resolverse antes de implementar; esta propuesta no decide ni cambia el algoritmo de firma ni sustituye la configuración del contrato.

## What Changes

- **BREAKING para solicitudes que omitan o envíen vacío `client`:** se exige un valor string no vacío tras `trim`, que se incorpora como claim `client` en el token emitido.
- El token emitido contiene `sub`, `client`, `iat` y `exp`, además de los claims de identidad, emisor y audiencia que ya forman parte del contrato.
- `expiresIn` representa el tiempo restante hasta `exp`; la equivalencia se verifica dentro de una tolerancia documentada.
- El flujo conserva los resultados existentes de autenticación: credenciales inválidas continúan respondiendo HTTP 401 y las solicitudes mal formadas continúan usando los errores HTTP documentados.
- No se registran tokens, secretos ni otros valores sensibles.

## Alcance

- Validación del `client` recibido en `POST /auth/login` y vinculación observable del valor aceptado con el token emitido.
- Claims requeridos y coherencia verificable entre el vencimiento del token y `expiresIn`.
- Comportamiento de login válido, credenciales inválidas y token expirado, así como protección de datos sensibles en los logs.
- Documentación de la configuración de firma vigente y del criterio de decisión pendiente sobre HMAC frente a una biblioteca JWT.

## Fuera de alcance

- Cambiar el contrato de rutas, métodos, formato JSON, identidad pública, roles, emisor, audiencia o algoritmo/configuración RS256 existente.
- Implementar autorización de tokens o cambiar el comportamiento de `/q/health`.
- Definir formatos de error de deserialización administrados por Quarkus que el contrato documenta como no estables.
- Introducir una nueva dependencia o elegir una biblioteca JWT.

## Comportamiento que debe preservarse

Según `docs/ENDPOINTS.md`, `POST /auth/login` no requiere autenticación previa; recibe `username`, `password` y un `client` opcional. Antes de este cambio, omitir `client` no altera el resultado y el campo no interviene en autenticación ni en el token. El login válido responde HTTP 200 con `token`, `expiresIn`, `user` y `debug`; el usuario solo expone `username`, `displayName` escapado y `role`. Se preservan los códigos y cuerpos documentados para cuerpo nulo, usuario o contraseña ausentes, credenciales inválidas y fallos internos. El token se firma RS256, conserva `iss=workshop-auth-backend`, audiencia que incluye `workshop-api`, y actualmente expira a `iat + 3600`.

El Issue cambia deliberadamente dos aspectos de ese baseline: `client`, antes opcional e ignorado, pasa a ser obligatorio, no vacío tras `trim` y reflejado en el token; y `expiresIn`, actualmente fijo en 3600, pasa a expresar el tiempo restante hasta `exp` con una tolerancia de un segundo por redondeo a segundos Unix. Los demás elementos anteriores se mantienen. El Issue también describe HMAC y `AUTH_TOKEN_SECRET`, en contradicción con el baseline que exige clave privada RSA externa; no se elige entre esas alternativas en esta propuesta.

## Capabilities

### New Capabilities

- `auth-token-issuance`: emisión de tokens vinculados a un cliente, con claims mínimos y una duración anunciada coherente con su expiración.

### Modified Capabilities

- Ninguna. El repositorio no tiene especificaciones base de capacidades; esta propuesta crea la capacidad indicada.

## Impact

- Contrato HTTP observable de `POST /auth/login`, particularmente la validación del campo `client` y `expiresIn`.
- Contenido verificable del JWT, logs de autenticación y documentación de configuración relacionada con la emisión de tokens.
- No se propone modificar la implementación ni elegir tecnologías en esta fase.
