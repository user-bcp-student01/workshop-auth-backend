# Plan de corrección: H-01, H-02 y H-03

## Aprobación humana registrada — 2026-10-19

El usuario aprobó explícitamente en esta conversación registrar la aprobación
e implementar H-01 y H-02 con pruebas primero; H-03 queda limitado a
caracterización, sin activar un token firmado ni modificar el contrato.
También aprobó PBKDF2-HMAC-SHA256 del JDK en lugar de BCrypt para preservar
contraseñas de más de 72 bytes y Unicode, sin nuevas dependencias.
El coste será configurable y medido; salt aleatorio individual y comparación
en tiempo constante. Esta decisión sustituye la opción BCrypt y su dependencia
propuesta en H-02, sin ampliar el alcance.

## Alcance y selección

Solo `workshop-auth-backend/`. Este paso crea únicamente este plan: no cambia
código, datos, dependencias, contratos ni estados de [HALLAZGOS.md](HALLAZGOS.md).

Se incluyen los IDs elegidos explícitamente en la consulta de alcance:

| ID | Hallazgo de la matriz | Estado consultado | Motivo |
|---|---|---|---|
| H-01 | SQL: concatenación de credenciales | Pendiente | Riesgo alto y corrección acotada mediante parámetros. |
| H-02 | Datos: contraseñas en texto plano | Pendiente | Exposición de credenciales; requiere hash y migración de persistencia. |
| H-03 | Token: Base64 con secreto embebido | Pendiente | Selección explícita del usuario; riesgo alto por falta de integridad y exposición del secreto. La sustitución del formato está bloqueada por el contrato actual. |

El motivo recibido mencionaba H-07, pero la selección de IDs fue H-01, H-02 y
H-03. H-07 no se incorpora como hallazgo independiente; la separación mínima
necesaria para H-02 respeta las reglas de capas. H-04, H-05 y H-06 también
quedan fuera del alcance, sin asumir que están resueltos.

Fuentes de convenciones: [docs/GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md) y
[REGLAS.md](REGLAS.md). No se presupone una biblioteca de seguridad instalada
ni se afirma un resultado de auditoría.

## Análisis del flujo actual

1. [AuthResource.login](src/main/java/com/bcp/workshop/auth/AuthResource.java#L24)
   valida petición, usuario y contraseña, en ese orden. `trim()` solo detecta
   vacíos; las credenciales originales llegan a persistencia. `client` no
   afecta la autenticación.
2. [LegacyDatabase](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L15)
   elige SQLite mediante `AUTH_DB_URL`, inicializa tabla y usuarios de
   laboratorio y consulta por ambas credenciales mediante SQL concatenado.
   Consulta sin coincidencia y error capturado retornan `null`.
3. El Resource interpreta `null` como HTTP 401; con un
   [UserRecord](src/main/java/com/bcp/workshop/auth/UserRecord.java#L3) genera el
   token con [LegacyTokenUtil.create](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L12).
   Este codifica usuario, rol, vencimiento y secreto con Base64 sin firma.
4. El Resource arma HTTP 200 con `token`, `expiresIn: 3600`, `user` y `debug`.
   Mapea el usuario sin contraseña ni ID y escapa `displayName` con Commons
   Text. Su `catch` responde HTTP 500 con mensaje y clase de la excepción.
5. [application.properties](src/main/resources/application.properties) configura
   HTTP, CORS y tolerancia a propiedades JSON desconocidas.
   [AuthResourceTest](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L13)
   solo comprueba un login válido, usuario y token no nulo.

## Contrato que debe permanecer intacto

[docs/ENDPOINTS.md](docs/ENDPOINTS.md) no se editará.

- Conservar `POST /auth/login`, JSON de entrada/salida, campos de petición,
  aceptación de propiedades desconocidas y `client` opcional sin efecto.
- Conservar orden de validación, credenciales sin normalizar y mensajes
  exactos de HTTP 400 y 401 para entradas legítimas.
- Conservar HTTP 200 y sus campos: `token`, `expiresIn: 3600`, `user` con
  `username`, `displayName` escapado y `role`, y
  `debug: authenticated-with-legacy-flow`. No filtrar hash, contraseña o ID.
- No aprovechar este trabajo para quitar `debug`, cambiar escape HTML,
  configurar CORS más restrictivo o modificar formatos de errores.
- El contrato también describe Base64 con cuatro segmentos, HTTP 500 con
  detalles internos y errores de consulta tratados como HTTP 401. No son
  comportamientos seguros, pero su modificación requiere autorización
  adicional. Este plan no autoriza resolver H-05 ni cambiar esos estados.
- H-01 sí corrige el tratamiento de entradas que alteraban la estructura SQL:
  no se pretende conservar una evasión de autenticación como comportamiento
  válido. Usuarios y contraseñas se tratarán como valores literales.

**Bloqueo de H-03:** emitir un token firmado no conserva el formato Base64
descrito explícitamente en el contrato. Mantener la misma propiedad `token`
no basta para demostrar compatibilidad. No se activará un emisor firmado,
no se agregará una salida alternativa ni se mantendrá un fallback inseguro
para aparentar que el hallazgo quedó corregido.

## Pruebas comunes antes de tocar producción

Ampliar primero [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
y agregar pruebas unitarias con JUnit ya disponible. Usar SQLite y archivos
temporales dentro de un directorio de pruebas de este repositorio; nunca la
base existente de `data/`. La variable `AUTH_DB_URL` debe fijarse antes de
cargar Quarkus/`LegacyDatabase`, porque hoy se lee en un campo estático.
No agregar una dependencia de mocks solo para crear dobles sencillos.

Caracterizar y hacer pasar en el código actual:

- Login de ambos usuarios de laboratorio; campos completos, tipos, valores
  de `expiresIn` y `debug`, ausencia de contraseña e ID.
- Petición nula mediante prueba directa del Resource; usuario y contraseña
  nulos, vacíos y con espacios; precedencia cuando faltan ambos.
- Usuario inexistente y contraseña incorrecta: HTTP 401 y mensaje exacto.
- Espacios en credenciales no se recortan para autenticar; `client` ausente,
  presente y campos JSON adicionales no cambian el resultado.
- `displayName` con caracteres HTML conserva el escape actual.
- Un doble de persistencia que lance una excepción verifica el HTTP 500
  actual, incluidos mensaje nulo y clase, sin usar información sensible.
  Un fallo controlado de consulta caracteriza por separado el HTTP 401 actual.

Distinguir esas pruebas de las regresiones de seguridad: una prueba nueva
que espera rechazo de SQL manipulado o hashes en almacenamiento puede fallar
en el código vulnerable. No presentar ese resultado como caracterización
que ya pasa ni ajustar expectativas para conservar la vulnerabilidad.

## H-01 — Parametrizar SQL de credenciales

### Hallazgo y problema

H-01 de [HALLAZGOS.md](HALLAZGOS.md), severidad alta, Pendiente.
[LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32)
concatena usuario y contraseña; la sentencia se ejecuta con `Statement`.
Las comillas u otros fragmentos recibidos pueden cambiar el filtro en lugar
de representar credenciales literales.

### Cambio propuesto

1. Como cambio revisable previo a H-02, conservar la firma de
   `findByCredentials` y reemplazar la consulta por
   `WHERE username = ? AND password = ?`, usando `PreparedStatement` y
   `setString` para ambos valores originales.
2. Conservar columnas, mapeo y manejo de recursos con `try-with-resources`.
   No usar escapes manuales, filtros de caracteres ni concatenación.
3. Parametrizar también los valores de inserción de usuarios de laboratorio.
   El DDL puede seguir siendo texto estático sin entrada del cliente.
4. Mantener un único mapeo de fila a `UserRecord`, sin duplicarlo entre la
   implementación transitoria y el repositorio de H-02.
5. No convertir aquí errores SQL en nuevos estados HTTP. Registrarlos con
   contexto y stacktrace en el servidor, sin credenciales ni tokens, y
   caracterizar el comportamiento legado. La corrección de la equivalencia
   entre error de consulta y credenciales inválidas pertenece a H-05.

### Archivos a tocar en la implementación futura

- [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java):
  consultas y parámetros; sin cambios de esquema en este paso.
- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  caracterización del endpoint y regresiones de entradas literales.
- Nuevo `src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java`:
  persistencia aislada y comprobación de parámetros.

No requiere dependencias nuevas: JDBC y `PreparedStatement` ya están disponibles.

### Prueba de caracterización previa

Antes de editar persistencia, comprobar usuario correcto, contraseña incorrecta,
usuario ausente, mapeo de los cuatro campos y cierre de recursos sobre SQLite
aislado. Caracterizar HTTP 200/401 y el error de consulta legado.

### Regresiones y criterio de corrección

Después del cambio, un usuario de prueba con apóstrofo en nombre o contraseña
debe autenticarse por igualdad literal. Entradas capaces de alterar el filtro
no deben autenticar a otro usuario. Verificar ambas posiciones de entrada y
ausencia de cambios en filas almacenadas. La forma HTTP y el token legado se
mantienen. No publicar payloads en documentación ni probar contra servicios
externos.

## H-02 — Hash lento con salt y migración de contraseñas

### Hallazgo y problema

H-02 de [HALLAZGOS.md](HALLAZGOS.md), severidad alta, Pendiente.
[LegacyDatabase.java:23](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L23)
inserta contraseñas literales y la consulta las compara directamente. El
contenido actual del archivo SQLite no fue inspeccionado; la migración debe
contemplar una base existente, no solo una instalación vacía.

### Cambio propuesto

1. Separar la mínima cadena necesaria:
   Resource → `AuthService` → `LegacyDatabase` como repositorio.
   El Resource conserva validación HTTP y mapeo de respuesta; el servicio
   autentica; el repositorio solo lee/escribe. El servicio usa un
   `PasswordHasher` para hash y verificación, sin implementar criptografía
   casera.
2. Sustituir la búsqueda por credenciales por una búsqueda parametrizada
   por nombre exacto. Recuperar un registro interno de credenciales con
   hash; verificar en el servicio y devolver únicamente `UserRecord`.
   No agregar el hash a ese record ni a la respuesta pública.
3. Usar BCrypt como opción concreta de las buenas prácticas. Propuesta:
   evaluar `io.quarkus:quarkus-elytron-security-common`, administrado por el
   BOM actual, para su utilidad BCrypt. Confirmar API, compatibilidad Java 21
   y disponibilidad antes de modificar [pom.xml](pom.xml). La dependencia
   se justifica porque el manifiesto leído no declara una implementación de
   hash lento con salt. No elegir versión independiente ni agregar varias
   bibliotecas para la misma responsabilidad.
4. Parametrizar el coste con un nombre claro y un valor validado de
   laboratorio; medir el tiempo de hash/verificación antes de fijarlo.
   BCrypt limita la entrada a 72 bytes: no truncar silenciosamente ni
   introducir un rechazo que cambie el contrato actual. Caracterizar
   contraseñas largas y Unicode; si hay que conservar entradas mayores,
   reevaluar la biblioteca y el algoritmo lento con salt antes de aprobar
   esta implementación. No resolverlo con un prehash improvisado.
5. Crear una migración explícita, transaccional e idempotente del esquema
   legado a una tabla equivalente con `password_hash TEXT NOT NULL`, sin
   columna de contraseña plana. Leer cada fila, generar un hash con salt
   individual y preservar ID, usuario, nombre visible y rol.
   Usar una tabla de reemplazo dentro de la misma transacción; verificar
   cantidad e identidades antes del reemplazo. Determinar el esquema mediante
   metadatos/versionado local, no adivinando si un valor tiene aspecto de hash.
6. Separar migración, creación de esquema y siembra en métodos distintos.
   Los usuarios de laboratorio nuevos se insertan con hashes; una base ya
   migrada no vuelve a hashear ni sobrescribe credenciales existentes.
   No mantener comparación con texto plano como fallback.
7. Ante fallo de migración, rollback y error explícito de inicio con logging
   sin credenciales; no arrancar aparentando éxito con una tabla incompleta.
   Esto impide reutilizar el `catch` actual que solo imprime y continúa.
   No cambia la forma de `POST /auth/login`: un inicio fallido no es un login
   con credenciales inválidas. La propagación de errores de consulta durante
   un login sigue separada del trabajo de H-05.

**Límite de datos:** quitar la columna no garantiza borrar restos en páginas,
WAL o copias antiguas. Para el laboratorio se debe prever una base nueva
verificada y retiro autorizado de archivos/copies antiguos dentro del ámbito;
no prometer borrado seguro mediante un simple `UPDATE`. No borrar, copiar ni
migrar la base real en esta fase de planificación.

### Archivos a tocar en la implementación futura

- [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java):
  consulta por usuario, esquema y siembra parametrizados.
- [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java):
  delegación al servicio, conservando la interfaz y respuesta.
- Nuevos en `src/main/java/com/bcp/workshop/auth/`:
  `AuthService.java`, `PasswordHasher.java`, `StoredCredentials.java` y
  `PasswordMigration.java`. Cada uno cubre una responsabilidad concreta.
- [pom.xml](pom.xml) y
  [application.properties](src/main/resources/application.properties):
  biblioteca BCrypt justificada y coste de hash, sin secretos reales.
- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
  y `LegacyDatabaseTest.java`; nuevas pruebas `AuthServiceTest.java`,
  `PasswordHasherTest.java` y `PasswordMigrationTest.java` en el mismo paquete
  de pruebas.
- [README.md](README.md): procedimiento de migración y configuración local.
  [UserRecord.java](src/main/java/com/bcp/workshop/auth/UserRecord.java) y
  [LoginRequest.java](src/main/java/com/bcp/workshop/auth/LoginRequest.java)
  no necesitan cambios de contrato.

### Prueba de caracterización previa

Antes de cambiar esquema o autenticación, crear una base legado temporal y
verificar siembra solo de usuarios ausentes, preservación de usuarios existentes,
login de ambos roles, rechazo de contraseña incorrecta y mapeo público.
Incluir contraseñas con espacios, Unicode y longitud superior a 72 bytes para
detectar incompatibilidades de la elección de hash. Las pruebas comunes
aseguran que cambiar el almacenamiento no cambia el contrato HTTP.

### Regresiones y criterio de corrección

- Hashes diferentes para la misma contraseña gracias a salts independientes;
  verificación correcta e incorrecta; coste configurado verificable.
- Base vacía creada con hashes; base legado migrada conservando identidades;
  segunda ejecución sin rehash; fallo inducido con rollback completo.
- Columna plana ausente en el esquema final y ninguna contraseña plana en
  filas activas, DTOs, respuestas o logs capturados.
- Login válido mantiene los campos y formato de token actuales.
- La solución sigue usando consultas parametrizadas de H-01.
- Eliminar literales de contraseñas de producción: las credenciales de siembra
  del laboratorio se obtienen de configuración explícita o fixtures aislados,
  se hashean antes de persistir y nunca se registran. No introducir secretos
  reales en configuración versionada.

## H-03 — Token firmado: propuesta bloqueada por compatibilidad

### Hallazgo y problema

H-03 de [HALLAZGOS.md](HALLAZGOS.md), severidad alta, Pendiente.
[LegacyTokenUtil.java:14](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L14)
incorpora el secreto al contenido reversible. El token carece de firma; la
fecha contenida no protege por sí misma la integridad del usuario o rol.

### Cambio propuesto y condición previa

El objetivo técnico es un token firmado con expiración y claims explícitos,
mediante biblioteca mantenida, no un formato casero. Sin embargo:

1. **Ahora:** escribir caracterización y documentar requisitos. Mantener el
   emisor legado al implementar H-01/H-02. H-03 permanece Pendiente.
2. **Antes de activar la corrección:** obtener autorización expresa para una
   migración contractual y coordinar con responsables de consumidores externos
   el formato, clave pública, algoritmo permitido, emisor, audiencia, claims
   y transición. Esta coordinación no permite leer ni editar sus componentes.
3. **Solo después de esa autorización:** sustituir el emisor por `TokenService`
   con firma asimétrica y claves externas. Propuesta de dependencia:
   `io.quarkus:quarkus-smallrye-jwt-build`, administrada por el BOM, sujeta a
   comprobar compatibilidad. Se justifica por la firma estándar y gestión de
   claims, ausentes en el manifiesto actual.
4. Usar una única duración nombrada de 3600 segundos para expiración y
   `expiresIn`. Claims propuestos: `sub` para usuario, un claim explícito para
   rol y `iss`, `aud`, `iat`, `exp`; los nombres y valores se acuerdan antes
   de activar. No asumir nombres exigidos por consumidores no inspeccionados.
5. Leer clave de firma y parámetros de entorno/configuración; material privado
   fuera del código, tokens y logs. Claves generadas solo para pruebas, aisladas.
   Error explícito si la configuración requerida falta o es inválida.
   No volver al token legado cuando falle la firma.

Conservar las demás claves HTTP no elimina el bloqueo de formato. No se
añade un modo firmado oculto, una segunda propiedad de token ni un verificador
al backend que no tiene rutas consumidoras de token en el flujo leído.

### Archivos a tocar en la implementación futura

**Compatible y previo a cualquier sustitución:**

- Nuevo `src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java`.
- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  caracterización de token y forma pública.

**Condicionados a autorización; no se modifican bajo este contrato:**

- [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java):
  retirada del emisor legado cuando exista transición acordada.
- Nuevo `src/main/java/com/bcp/workshop/auth/TokenService.java`.
- `AuthService.java` o [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java),
  según el punto de emisión tras H-02; mantener emisión en el servicio y
  ensamblado HTTP en Resource, sin lógica duplicada.
- [application.properties](src/main/resources/application.properties),
  [pom.xml](pom.xml) y [README.md](README.md): configuración y dependencia
  justificada, sin material privado.
- Nueva `src/test/java/com/bcp/workshop/auth/TokenServiceTest.java`.

[docs/ENDPOINTS.md](docs/ENDPOINTS.md) permanece intacto en esta fase.
Cualquier actualización contractual futura requiere otro alcance autorizado.

### Prueba de caracterización previa

Decodificar el token legado en una prueba aislada: exactamente cuatro
segmentos, usuario y rol esperados y vencimiento entre los instantes tomados
antes/después de generar más 3600 segundos. Comprobar el segmento del secreto
solo con valor de laboratorio sin imprimirlo. No fijar un token literal
completo ni exigir una fecha exacta basada en el reloj real.
Verificar HTTP 200, `expiresIn` y todos los campos públicos.

### Pruebas futuras de corrección, no de compatibilidad legado

Una vez autorizada la migración, usar reloj controlable y claves aisladas para
verificar firma con una biblioteca de validación, claims y duración exacta.
Rechazar token alterado, firma/clave incorrecta y token expirado; comprobar
configuración ausente y ausencia de material privado en payload y logs.
La validación en pruebas demuestra el emisor local, no que los consumidores
externos ya lo acepten. Esa aceptación debe confirmarse por sus responsables
antes de activar; no se declarará H-03 corregido solo por emitir una cadena.

## Orden de ejecución y Clean Code

1. Caracterización común y pruebas locales de persistencia/token.
2. H-01 como cambio revisable y acotado; ejecutar regresiones dirigidas.
3. H-02: abstraer verificación, migrar base de pruebas y delegar en servicio;
   conservar contrato y emisor legado.
4. H-03: solo caracterización y coordinación mientras exista el bloqueo.

Reglas aplicadas a cada cambio:

- Una responsabilidad por método: validar entrada HTTP, consultar usuario,
  mapear fila, verificar hash, migrar datos y emitir token son operaciones
  separadas. Resource no contiene SQL ni verificación de contraseñas.
- Sin duplicación: un mapeador de persistencia y una única fuente nombrada
  para duración; siembra reutiliza el mismo hasher que autenticación.
- Retornos tempranos para entradas inválidas y usuario ausente; no repetir
  la pirámide de condiciones del Resource.
- Nombres de propósito y tipos concretos; registros internos no se exponen
  como DTO HTTP. El mapeo público actual se conserva.
- SQL estático con parámetros; coste, duración y configuración tienen nombres
  explícitos, no números o secretos sueltos en métodos.
- No silenciar scanners ni JaCoCo. No resolver hallazgos fuera de selección
  ni introducir dependencias que no sean necesarias para hash/firma.

## Validación futura y cierre

Desde la raíz de este repositorio, con `AUTH_DB_URL` dirigido a la base aislada
antes del arranque, ejecutar primero JUnit/Quarkus para las clases afectadas
con `mvn "-Dtest=<clases afectadas>" test`. Tras cada cambio coherente ejecutar
`mvn verify` y revisar JaCoCo: cobertura del código modificado **85% o superior**,
sin presentar cobertura global como sustituto ni excluir código para lograrla.

Verificar las formas HTTP y los casos exactos descritos arriba, no solamente
que el token sea no nulo. Revisar diff para asegurar que el contrato no cambió.
El código actual no se ejecutó ni se midió en esta fase; los comandos y
pruebas son actividades propuestas, no resultados.

Actualizar únicamente [HALLAZGOS.md](HALLAZGOS.md) durante la implementación
posterior cuando exista evidencia de cada corrección. Este plan no mantiene
una segunda matriz de estados ni marca resuelto un hallazgo por anticipado.
