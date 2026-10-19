# Propuesta de corrección: items 3, 4, 5, 6 y 7

## Autorización de implementación registrada — 2026-10-19

El usuario aprobó implementar secuencialmente H-03 a H-07 y confirmó
explícitamente: JWT RS256, `sub`, `role`, `iss=workshop-auth-backend`,
`aud=workshop-api`, `iat`, `exp`, duración 3600 segundos; responsables
externos acordaron formato, distribución de clave pública y transición.
Autorizó CORS limitado al origen local `http://localhost:4200`, GET/POST
y cabeceras necesarias, y HTTP 500 genérico `{"message":"Internal server error"}`
para errores internos/SQL sin campo `exception`. Esta autorización levanta
los bloqueos contractuales que se describen históricamente a continuación.
No autoriza leer o modificar componentes externos ni alterar otros campos.

## Alcance y estado de aprobación

Los números solicitados corresponden a **H-03, H-04, H-05, H-06 y H-07** de
[HALLAZGOS.md](HALLAZGOS.md), todos Pendientes al consultar la matriz.
Este documento sustituye el plan anterior; no modifica código, dependencias,
contratos, datos ni estados de hallazgos. Cada item se revisará por separado.

La aprobación histórica del 2026-10-19 solo cubrió la implementación de
H-01/H-02 y la caracterización de H-03. Incluyó PBKDF2-HMAC-SHA256 del JDK
en vez de BCrypt para conservar contraseñas largas y Unicode sin dependencias
nuevas. Esa aprobación **no autoriza las correcciones nuevas de este plan**.
H-01/H-02 ya resueltos no se reabren.

Referencias: [docs/ENDPOINTS.md](docs/ENDPOINTS.md),
[docs/GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md),
[REGLAS.md](REGLAS.md) y [docs/TESTING.md](docs/TESTING.md).
Se contrastaron Resource, servicio, repositorio, token, configuración y
manifiesto actuales. No se ejecutaron pruebas ni scanners para redactar esta
propuesta; los resultados de TESTING son evidencia previa, no una ejecución nueva.

### Regla de compatibilidad

La matriz describe problemas; no contiene una autorización explícita para
modificar el contrato. En particular, token Base64, CORS abierto, HTTP 500
con detalles y HTTP 401 ante error SQL están documentados actualmente.
Los items correspondientes proponen su corrección objetivo, pero señalan
el bloqueo antes de activarla. No basta conservar el nombre de un campo
para afirmar que su semántica es compatible.

No se editará ENDPOINTS durante esta planificación. En una implementación
futura, solo una autorización explícita de cambio contractual permitirá
actualizar sus apartados afectados. No se modificarán `/q/health`, requisitos
de autenticación, campos de entrada, escape de `displayName`, `debug` ni
estados ajenos al item aprobado.

## Item: H-03 — Token Base64 con secreto embebido

### Hallazgo y alcance propio

[H-03](HALLAZGOS.md) sigue Pendiente: el
[emisor legado](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13)
codifica usuario, rol, vencimiento y secreto sin firma. Este item es dueño
del **formato criptográfico, claims y emisión del token**, no de la política
CORS ni de respuestas de error HTTP.

### Cambio propuesto

1. Mantener primero la caracterización de formato actual y de la respuesta
   completa del login. No marcar esas pruebas como corrección de seguridad.
2. Tras autorización contractual, sustituir el emisor legado por un
   `TokenService` con una biblioteca mantenida de firma JWT. Evaluar
   `io.quarkus:quarkus-smallrye-jwt-build` administrada por el BOM; justificar
   su necesidad y comprobar compatibilidad antes de tocar el manifiesto.
   No implementar algoritmos criptográficos propios.
3. Proponer firma asimétrica y claims `sub`, rol explícito, `iss`, `aud`,
   `iat` y `exp`. Acordar con responsables de consumidores el algoritmo,
   nombre del claim de rol, emisor, audiencia, distribución de clave pública
   y transición. No asumir cómo consumen tokens componentes no inspeccionados.
4. Conservar duración de 3600 segundos y consistencia con `expiresIn`,
   mediante una única fuente nombrada y reloj controlable para pruebas.
   Usuario y rol deben proceder de la autenticación, no del request libre.
5. Leer la clave privada desde configuración externa, nunca incluirla en
   claims o logs. Ausencia o invalidez de configuración debe fallar
   explícitamente; no emitir Base64 como fallback. No añadir verificación
   HTTP a rutas que hoy son públicas ni un segundo endpoint de token.

**Bloqueo:** ENDPOINTS especifica cuatro segmentos Base64. Un JWT firmado
es incompatible con ese formato aunque siga siendo un string `token`.
Sin autorización y acuerdo de consumidores, solo proceden caracterización
y preparación de decisiones, no activación ni cierre de H-03.
La futura clave de firma pertenece a este item; H-04 no duplica su implementación.

### Archivos previstos

- [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java):
  retirar el emisor solo cuando la transición esté autorizada.
- Nuevo `src/main/java/com/bcp/workshop/auth/TokenService.java`;
  [AuthService.java](src/main/java/com/bcp/workshop/auth/AuthService.java):
  delegar emisión por inyección.
- [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java):
  únicamente ajustar la fuente de duración si se retira la clase legado;
  conservar DTOs y forma pública del resto de campos.
- [pom.xml](pom.xml) y
  [application.properties](src/main/resources/application.properties):
  dependencia justificada y configuración de firma/claims.
- [LegacyTokenUtilTest.java](src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java),
  [AuthServiceTest.java](src/test/java/com/bcp/workshop/auth/AuthServiceTest.java),
  [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java)
  y nuevo `src/test/java/com/bcp/workshop/auth/TokenServiceTest.java`.
- [README.md](README.md), [docs/TESTING.md](docs/TESTING.md) y
  [HALLAZGOS.md](HALLAZGOS.md), para instrucciones y evidencia del item.
  ENDPOINTS solo cambiaría con autorización explícita del formato nuevo.

### Prueba antes de tocar producción y prueba de corrección

Ejecutar primero la caracterización existente: decodificación Base64 con
cuatro segmentos, usuario/rol, vencimiento entre instantes antes/después
más 3600 y forma completa HTTP 200. No fijar un token dependiente del reloj.

Después de aprobar el formato, escribir primero pruebas de firma verificable
con clave pública de laboratorio, claims y duración exacta con reloj fijo;
token alterado, clave incorrecta, vencido y configuración inválida. La
verificación de rechazo corresponde al verificador de pruebas, no a un filtro
inexistente del backend. Las expectativas del formato firmado deben fallar
antes de cambiar el emisor; el legado se conserva como evidencia histórica,
no se debilita el test para aceptar ambos formatos indefinidamente.
Solo cerrar H-03 al verificar emisión y aceptación coordinada, no por token no nulo.

## Item: H-04 — Secreto fijo y CORS permisivo

### Hallazgo y alcance propio

[H-04](HALLAZGOS.md) sigue Pendiente: hay un secreto en
[LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L9)
y CORS acepta cualquier origen en
[application.properties](src/main/resources/application.properties#L4).
Este item es dueño de **externalización de configuración y restricción CORS**.
No cambia formato ni claims del token, que pertenecen a H-03.

### Cambio propuesto

1. Si se aborda antes de H-03, reemplazar la constante del secreto legado
   por configuración obligatoria inyectada, manteniendo exactamente el
   formato de cuatro segmentos. Probar sin registrar valores sensibles.
   No usar un secreto real: el formato legado revela cualquier valor que se
   configure. Esta externalización es parcial, no elimina la vulnerabilidad
   criptográfica de H-03.
2. Para compatibilidad temporal, el valor de laboratorio debe coincidir
   explícitamente con el esperado por consumidores, sin default secreto en
   Java. No rotarlo unilateralmente ni afirmar que cualquier valor nuevo
   conserva el contrato. Si H-03 ya retiró el emisor, no crear otro proveedor
   de secretos legado; verificar la externalización que H-03 introdujo.
3. Sustituir la expresión de todos los orígenes por una lista permitida externa,
   con origen local explícito `http://localhost:4200` para el laboratorio.
   Proponer métodos POST y GET y cabeceras estrictamente necesarias para
   login y salud; el preflight debe ser atendido por el middleware.
   No quitar cabeceras usadas sin comprobar la integración.
4. Validar orígenes y configuración al iniciar; no degradar silenciosamente
   a `/.*/` por un error. Documentar variables, perfil y valores ficticios
   para pruebas; no agregar bibliotecas para leer configuración ya soportada.

**Bloqueo:** la política CORS amplia y el secreto fijo forman parte de la
descripción actual. La configuración externa con el mismo valor puede
conservar la salida, pero restringir orígenes/métodos o rotar el secreto cambia
comportamiento documentado. Requiere autorización explícita de esa política.
No atribuir una incompatibilidad del token firmado a este item.

### Archivos previstos

- [application.properties](src/main/resources/application.properties):
  propiedades externas de CORS y, si aún aplica, secreto legado.
- [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java)
  y [AuthService.java](src/main/java/com/bcp/workshop/auth/AuthService.java):
  inyección del secreto solo si el emisor sigue existiendo, sin cambiar formato.
- [pom.xml](pom.xml): únicamente variables ficticias del runner si son
  necesarias; no dependencias nuevas.
- [LegacyTokenUtilTest.java](src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java)
  y nuevo `src/test/java/com/bcp/workshop/auth/CorsConfigurationTest.java`,
  usando perfiles de test separados para políticas distintas.
- [README.md](README.md), [docs/TESTING.md](docs/TESTING.md) y
  [HALLAZGOS.md](HALLAZGOS.md); ENDPOINTS únicamente tras autorización CORS
  o de la condición de secreto configurado.

### Prueba antes de tocar producción y prueba de corrección

Caracterizar antes: peticiones simples y preflight con origen local y otro
origen ficticio, headers y métodos hoy permitidos; comprobar respuesta de
login sin Origin. Si sigue el emisor legado, comprobar cuatro segmentos y
valor ficticio esperado sin imprimirlo.

Escribir después las expectativas aprobadas de CORS antes de cambiar la
política: origen permitido recibe los headers correctos; no permitido no
obtiene permiso CORS; preflight de header/método no permitido no se autoriza.
No asumir que denegar CORS equivale a HTTP 403: comprobar la respuesta real
del middleware y la ausencia del permiso de navegador.
Probar configuración ausente/inválida con fallo explícito, y variación del
valor de laboratorio solo en emisor aislado. Una rotación real requerirá
aceptación de consumidores coordinada, fuera de las pruebas locales.

## Item: H-05 — Errores que exponen detalles internos

### Hallazgo y alcance propio

[H-05](HALLAZGOS.md) sigue Pendiente:
[AuthResource.login](src/main/java/com/bcp/workshop/auth/AuthResource.java#L39)
envía mensaje y clase de excepción; el
[repositorio](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L52)
convierte un fallo SQL en usuario ausente. Este item es dueño de
**semántica de fallos de infraestructura, respuesta genérica y logging**.
No modifica autenticación válida, hashing, firma, CORS o salud.

### Cambio propuesto

1. Tras autorización contractual, propagar errores de consulta mediante una
   excepción interna de persistencia que conserve la causa. Solo una consulta
   sin coincidencia devuelve ausencia de usuario; no confundirla con una
   base inaccesible.
2. Construir un único error público genérico HTTP 500:
   `{"message":"Internal server error"}` como propuesta a aprobar. Omitir
   `exception` y el mensaje interno; no reemplazar detalles por un string
   secreto o inventar un HTTP 200.
3. Centralizar la traducción de la excepción interna y otros fallos no
   esperados en un único punto de la capa HTTP. Preferir mantener el manejo
   en Resource si basta; no crear un mapper global que altere los rechazos
   Jackson 400/405/406/415 ajenos al alcance.
4. Registrar causa/stacktrace una vez en el servidor, con contexto no sensible;
   nunca password, hash, token ni request completo. El repositorio no debe
   registrar y volver a registrar la misma excepción arriba.
5. Mantener mensajes 400 actuales y 401 para credenciales incorrectas,
   incluida interpolación del usuario, salvo petición separada de cambiarlo.

**Bloqueo:** elimina el campo `exception`, cambia el mensaje 500 y transforma
el 401 de fallo SQL en 500. Las tres diferencias requieren autorización
explícita; las buenas prácticas no anulan el contrato vigente.
Mientras falte, conservar caracterización sin cerrar H-05.

### Archivos previstos

- [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java):
  distinguir ausencia de error.
- Nuevo `src/main/java/com/bcp/workshop/auth/AuthenticationPersistenceException.java`.
- [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java):
  traducción genérica única y logging; no agregar respuesta alternativa de salud.
- [LegacyDatabaseTest.java](src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java),
  [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java),
  [EndpointContractTest.java](src/test/java/com/bcp/workshop/auth/EndpointContractTest.java)
  y [HealthResourceTest.java](src/test/java/com/bcp/workshop/auth/HealthResourceTest.java):
  actualizar solo expectativas afectadas por la semántica autorizada del login.
- [README.md](README.md), [docs/TESTING.md](docs/TESTING.md) y
  [HALLAZGOS.md](HALLAZGOS.md); ENDPOINTS solo con autorización del error nuevo.

### Prueba antes de tocar producción y prueba de corrección

Ejecutar primero las pruebas existentes que provocan tabla temporalmente
inaccesible, hash inválido y excepciones con/sin mensaje. Registrar sus
401/500 y cuerpos actuales como baseline, sin considerarlos una solución.

Una vez aprobados los cambios, escribir pruebas que exijan 500 genérico para
fallo SQL, hash inválido y error inesperado; sin clase, mensaje interno ni
datos sensibles. Verificar logging con causa y sin secretos, recuperación
del fixture en `finally`, 401 de contraseña incorrecta intacto y las respuestas
400/405/406/415 sin cambios. Las nuevas expectativas deben fallar antes del
cambio, no aceptar tanto el cuerpo inseguro como el corregido.

## Item: H-06 — Investigar Quarkus 3.27.4 y Commons Text 1.9

### Hallazgo y alcance propio

[H-06](HALLAZGOS.md) es una sugerencia Pendiente, no un CVE confirmado.
[pom.xml](pom.xml#L13) declara Quarkus 3.27.4 y
[Commons Text 1.9](pom.xml#L49); el Resource usa
[`escapeHtml4`](src/main/java/com/bcp/workshop/auth/AuthResource.java#L62).
Este item es dueño de **investigación SCA y actualización compatible**.
No sustituye el escape HTML por texto sin escape ni se aprovecha para cambiar
token, errores, CORS o la estructura de capas.

### Cambio propuesto

1. Obtener árbol de dependencias resuelto y reportes disponibles de
   Dependency Review/CodeQL. Contrastar advisories oficiales por coordenadas,
   rango, versión corregida y condiciones de explotación. Consultar
   información pública sin enviar código privado ni credenciales.
2. Documentar qué es presencia de versión, alerta confirmada y aplicabilidad
   al uso real. No afirmar que `escapeHtml4` ejecuta mecanismos distintos
   de la biblioteca ni inventar una versión corregida o salida de scanner.
3. Si existe actualización justificada, preferir la menor versión mantenida
   que satisfaga corrección y compatibilidad. Para Quarkus mantener BOM y
   plugin alineados; no forzar transitivas independientes sin evidencia.
4. Preferir actualizar Commons Text antes que reemplazar su escape. Retirar
   la biblioteca solo si otra implementación existente reproduce exactamente
   el comportamiento documentado y las pruebas lo demuestran. No agregar una
   biblioteca alternativa ni un escape manual por conveniencia.
5. Comparar antes/después advisories, árbol y contrato. Si no hay evidencia
   suficiente para cambiar, registrar la investigación pendiente sin hacer
   un bump especulativo. No silenciar alertas ni reducir gates.

**Compatibilidad:** una actualización de Quarkus puede cambiar JSON de
deserialización o respuestas de salud. Si difieren los casos de ENDPOINTS,
buscar una actualización compatible o detener ese cambio para autorización;
no editar tests para aceptar silenciosamente el contrato nuevo.

### Archivos previstos

- [pom.xml](pom.xml): versiones seleccionadas con evidencia, sin ampliar
  dependencias injustificadamente.
- [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java):
  solo si se demuestra necesario para preservar el escape al retirar Commons;
  por defecto no se toca.
- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  caracterización adicional de escape y DTOs.
- [EndpointContractTest.java](src/test/java/com/bcp/workshop/auth/EndpointContractTest.java)
  y [HealthResourceTest.java](src/test/java/com/bcp/workshop/auth/HealthResourceTest.java):
  ejecutar como regresión; no cambiar expectativas sin autorización.
- [HALLAZGOS.md](HALLAZGOS.md), [README.md](README.md) y
  [docs/TESTING.md](docs/TESTING.md): evidencia, coordenadas, resultados reales.
  Guardar salidas de investigación bajo `target/`, no en otros repositorios.

### Prueba antes de tocar producción y prueba de corrección

Antes de actualizar manifiesto o escape, agregar casos de nombre visible
con `&`, `<`, `>`, comillas simples/dobles, Unicode y entidades ya escapadas;
fijar las salidas exactas del comportamiento actual. Verificar todos los
campos del login y las respuestas HTTP de framework y salud documentadas.

Ejecutar el mismo conjunto tras cada actualización coherente. La prueba
SCA de corrección es repetir el reporte que sustentó el cambio y comprobar
que el advisory aplicable ya no afecta las coordenadas resueltas; no es
suficiente que Maven compile. Versiones concretas, CVEs y códigos de salida
quedan por investigar, no se fabrican en el plan.

## Item: H-07 — Revisar separación de responsabilidades y Clean Code

### Hallazgo y alcance propio

[H-07](HALLAZGOS.md) sigue Pendiente de revisión específica. El código
ya tiene `AuthService`, repositorio, hasher, migración, DTOs y retornos
tempranos por H-02; no se debe recrear la separación como si no existiera.
Este item es dueño de **cohesión y eliminación de duplicación**, sin cambiar
el contrato ni resolver H-03/H-04/H-05/H-06 dentro de este apartado.

### Cambio propuesto

1. Verificar la cadena actual Resource → servicio → repositorio, el mapeo
   público sin hash/ID y que no se ejecuta SQL o verificación de contraseña
   dentro del Resource. Mantener validación y ensamblado HTTP en la capa API:
   no extraerlos a múltiples clases sin necesidad.
2. Revisar que consulta, mapeo de fila, siembra, migración y hash tengan una
   responsabilidad identificable por método y nombres de propósito.
   Reutilizar el mapeador y hasher actuales en vez de duplicarlos.
3. Confirmar que la duración 3600 ya tiene una fuente nombrada compartida.
   Mientras H-03 no se autorice, no trasladarla a configuración variable
   ni alterar su valor. Si H-03 la mueve, consumir esa única fuente.
4. Aplicar únicamente extracciones pequeñas donde exista mezcla o duplicación
   concreta confirmada; conservar guard clauses y DTOs existentes.
   Si la revisión demuestra que las correcciones de H-02 ya satisfacen el
   hallazgo, proponer cerrarlo con evidencia y pruebas, **sin refactor cosmético**.
5. No trasladar el cambio de errores a un refactor de arquitectura: cualquier
   sustitución de 401/500 corresponde exclusivamente a H-05.

### Archivos previstos

- [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java),
  [AuthService.java](src/main/java/com/bcp/workshop/auth/AuthService.java)
  y [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java):
  candidatos de revisión, modificar solo ante una necesidad concreta demostrada.
- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java),
  [AuthServiceTest.java](src/test/java/com/bcp/workshop/auth/AuthServiceTest.java)
  y [LegacyDatabaseTest.java](src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java):
  reforzar las responsabilidades ya separadas con dobles sencillos cuando haga falta.
- [HALLAZGOS.md](HALLAZGOS.md) y [docs/TESTING.md](docs/TESTING.md):
  evidencia de revisión y pruebas. No requiere nuevas dependencias, endpoints,
  configuración de negocio ni cambios en ENDPOINTS.

### Prueba antes de tocar producción y prueba de corrección

Completar primero una prueba de servicio con dobles de repositorio/hasher:
usuario ausente no ejecuta verificación; usuario existente verifica una sola
vez la contraseña original; contraseña incorrecta no devuelve identidad;
credenciales correctas devuelven solo `UserRecord`.

Mantener pruebas de Resource para validación, delegación y DTO público, y de
repositorio para búsqueda exacta parametrizada y mapeo. Ejecutar el contrato
completo como baseline antes de cualquier extracción y después sin cambiar
expectativas. La corrección de este item se verifica con evidencia estructural
y ausencia de regresiones, no con el número de archivos nuevos.

## Ejecución futura, validación y cierre por item

- La aprobación de un item no autoriza los demás. Crear pruebas antes de
  modificar cada superficie y mantener diffs revisables por ID; aunque un
  archivo sea compartido, no mezclar firma, CORS y errores en un único cambio.
- Si se implementan H-03 y H-04 juntos, H-03 es dueño de la clave de firma;
  H-04 no añade un secreto legado redundante. Si se implementan en otro
  orden, registrar explícitamente el estado de compatibilidad temporal.
- Usar JDK 21 y Maven, base y credenciales aisladas bajo `target/`. Ejecutar
  primero los tests afectados con `mvn "-Dtest=<clases afectadas>" test` y
  luego `mvn verify`, sin modificar la base de `data/`.
- Mantener el gate JaCoCo existente de **85 % de líneas por clase modificada**.
  Medir con el XML/HTML de la ejecución completa, sin acumular cobertura ni
  excluir clases nuevas. No reducir CodeQL o Dependency Review.
- Registrar comandos, directorio, salida y resultados antes/después en
  TESTING. Distinguir pruebas que caracterizan defectos de las regresiones
  que demuestran su corrección; no sustituir expectativas sin evidencia.
- Solo HALLAZGOS lleva los estados. Cerrar cada ID únicamente cuando su
  alcance esté verificado; no cerrar H-03 por firma local sin coordinación
  ni H-04 solo por mover un secreto manteniendo CORS abierto.
- En este paso solo cambia PLAN.md. No se actualizan estados ni se
  atribuye aprobación de implementación a la solicitud de planificar.
