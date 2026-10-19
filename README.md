# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

Para una base nueva, configurar las contraseñas de siembra de laboratorio.
No son secretos reales; los ejemplos reproducen las credenciales del taller.
Si los usuarios ya existen, se conservan sus credenciales y estas variables
no son necesarias.

```powershell
$env:AUTH_SEED_STUDENT_PASSWORD = 'Workshop123!'
$env:AUTH_SEED_TRAINER_PASSWORD = 'Trainer123!'
$env:AUTH_TOKEN_PRIVATE_KEY = 'C:\ruta\aislada\signing-private.pem'
mvn quarkus:dev -Dquarkus.enforceBuildGoal=false
```

Servicio: `http://localhost:8080`

La ruta de clave es ilustrativa: provisionar fuera de Git una clave RSA
PKCS#8 PEM de al menos 2048 bits antes de iniciar; no usar la clave de tests.
El override de desarrollo evita la omisión del arranque por la configuración
preexistente del plugin sin ejecución `build`; no modifica quality gates.

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.

## Correcciones locales y configuración

Esta copia implementa los siete items de [HALLAZGOS.md](HALLAZGOS.md).
Los apartados H-01/H-02 siguientes conservan la justificación histórica.
Las consultas usan parámetros y las contraseñas se verifican con
PBKDF2-HMAC-SHA256 del JDK, con salt aleatorio de 16 bytes y clave de 32 bytes.
Se eligió en lugar de BCrypt, con aprobación humana, para admitir contraseñas
Unicode y de más de 72 bytes sin truncarlas y sin dependencias nuevas.
La descripción de deuda de `main` es histórica, no un resultado de un escáner.

| Variable | Uso |
|---|---|
| `AUTH_DB_URL` | URL JDBC; por defecto `jdbc:sqlite:./data/workshop.db`. El directorio padre debe existir. |
| `AUTH_PASSWORD_ITERATIONS` | Coste de PBKDF2, por defecto `600000`; rango validado `10000` a `2000000`. El mínimo solo se usa para agilizar pruebas aisladas, no se recomienda como coste de despliegue. |
| `AUTH_SEED_STUDENT_PASSWORD` | Contraseña para crear `student` si falta. Nunca reemplaza un usuario existente. |
| `AUTH_SEED_TRAINER_PASSWORD` | Contraseña para crear `trainer` si falta. Nunca reemplaza un usuario existente. |
| `AUTH_TOKEN_PRIVATE_KEY` | Obligatoria: ruta al PEM PKCS#8 RSA externo de firma; sin valor por defecto. |
| `AUTH_CORS_ORIGINS` | Lista de orígenes HTTP(S) explícitos; por defecto `http://localhost:4200`. |

El formato almacenado incluye algoritmo, iteraciones, salt y hash. La
comparación usa `MessageDigest.isEqual`. El coste se conserva en cada hash,
por lo que cambiar la configuración no invalida credenciales existentes.
La medición local de 600000 iteraciones para hash más verificación está en
[target/hash-after.log](target/hash-after.log); no es una garantía de latencia.

### Migración de una base existente

1. Detener todas las instancias que usen la base y preparar una recuperación
   autorizada antes de arrancar la versión nueva. No ejecutar varias
   migraciones concurrentes. No guardar copias de contraseñas en Git.
2. Elegir `AUTH_DB_URL` dentro del ámbito del laboratorio y confirmar el
   directorio padre. No apuntar a datos de producción.
3. Al arrancar, el repositorio verifica el esquema por metadatos. Una tabla
   legado se copia dentro de una transacción a una tabla con `password_hash`,
   hasheando cada contraseña y preservando ID, usuario, nombre y rol.
   Verifica cantidad e identidades antes de reemplazarla.
4. Una migración fallida hace rollback y falla explícitamente el arranque;
   una migración ya aplicada no vuelve a hashear. Un esquema desconocido
   también impide el arranque. Los usuarios nuevos se siembran con hash y
   requieren configuración explícita; las contraseñas no se registran.
5. Verificar login y esquema en el ambiente aislado. La eliminación de la
   columna no elimina necesariamente restos de páginas SQLite, WAL, journals
   o copias anteriores: no equivale a borrado seguro. El retiro o reemplazo
   de esos archivos requiere autorización y un procedimiento de manejo de datos.

Esta ejecución de implementación solo migró fixtures bajo `target/`, no la
base del laboratorio en `data/`. La próxima ejecución de la aplicación sí
aplicará la migración a la base configurada.

### Validación y diffs

Con JDK 21 seleccionado también en `JAVA_HOME`:

```powershell
mvn test
mvn verify
```

Surefire fija una base SQLite aislada en `target/`, contraseñas de prueba y
un coste de 10000 iteraciones solo para tests. Las pruebas del coste normal
también ejercitan 600000 iteraciones. No se usa `data/workshop.db` al probar.
`mvn verify` mantiene JaCoCo y exige al menos 85 % de líneas **por clase**
de producción modificada/agregada, sin exclusiones de esas clases.
El informe se genera en [target/site/jacoco/index.html](target/site/jacoco/index.html).

Tabla histórica H-01/H-02 y primera caracterización de H-03; los resultados
actuales H-03 a H-07 están en [docs/items/RESULTADOS.md](docs/items/RESULTADOS.md).

| Hallazgo | Caracterización antes / después | Corrección antes / después | Diff |
|---|---|---|---|
| H-01 | 9 pruebas pasan antes; las mismas expectativas se conservan después | Las 3 regresiones SQL fallan antes y pasan después | [H-01.diff](docs/H-01.diff), cambio intermedio parametrizado |
| H-02 | Login, Unicode, contraseña larga, espacios, errores y forma HTTP pasan antes y después; el montaje de fixtures se adapta al nuevo almacenamiento | La prueba de esquema falla antes por falta de `password_hash`; pasa después. Las nuevas pruebas adicionales verifican hash, migración, idempotencia, rollback y errores de arranque | [H-02.diff](docs/H-02.diff), delta de producción desde H-01 más pruebas y configuración |
| H-03 | La prueba de cuatro segmentos y vencimiento pasa antes y después | No se ejecutó una corrección: el token firmado sigue bloqueado por contrato | [H-03.diff](docs/H-03.diff), solo constante compartida y caracterización |

Evidencia de ejecución:
[caracterización inicial](target/characterization-before.log),
[SQL antes](target/sql-before.log), [SQL después](target/sql-after.log),
[almacenamiento antes](target/hash-before.log) y
[verificación final](target/hash-after.log). Los fallos esperados anteriores
se conservaron; no se presentan como suites exitosas. Las pruebas nuevas de
migración y del hasher no se ejecutaron contra clases que aún no existían.
La verificación final incluye 24 pruebas, sin fallos ni errores, incluida la
protección frente a un fallo del propio rollback (no activa autocommit).

Históricamente H-03 conservó el formato legado. La implementación aprobada
posteriormente lo sustituye por JWT RS256 (ver sección siguiente).
La investigación H-06 y los errores autorizados H-05 se documentan al final.
El 401 histórico ante fallo SQL se sustituyó por 500 genérico.

## H-03: firma aprobada y clave externa

El emisor usa `quarkus-smallrye-jwt-build`, administrado por el BOM, para
firma estándar en lugar de criptografía propia. Configurar
`AUTH_TOKEN_PRIVATE_KEY` con una ruta a PKCS#8 PEM RSA de al menos 2048 bits.
No hay clave privada de producción versionada ni fallback Base64.
Las pruebas generan pares efímeros bajo `target/` y eliminan sus claves.

Los responsables externos confirmaron RS256, claims `sub`, `role`,
`iss=workshop-auth-backend`, `aud=workshop-api`, `iat`, `exp` y plazo 3600 s.
Deben recibir solo la clave pública por un canal acordado y verificar esos
valores; no reutilizar la constante de laboratorio eliminada. La aceptación
externa es confirmación humana, no una prueba ejecutada en otros repositorios.

## H-04: política CORS explícita

`AUTH_CORS_ORIGINS` admite orígenes HTTP(S) explícitos separados por comas,
sin rutas, usuarios, query ni fragmentos; predeterminado
`http://localhost:4200`. Una lista vacía o inválida impide iniciar.
Solo se autorizan GET/POST y headers accept, authorization, content-type.
No existe secreto legado que externalizar: H-03 ya eliminó ese emisor.

## H-05: fallos internos

Los fallos SQL conservan su causa en `AuthenticationPersistenceException`;
solo usuario ausente/contraseña incorrecta devuelve 401. El Resource registra
una vez la excepción sin request ni credenciales y responde
`500 {"message":"Internal server error"}`. No cambia errores de validación
o deserialización ni incorpora un mapper global.

## H-06: dependencias investigadas

BOM y plugin Quarkus alineados en 3.27.6.1 (parche de la misma línea LTS),
Commons Text 1.10.0 (mínima corrección publicada para CVE-2022-42889).
Se conserva `escapeHtml4`: no usa la interpolación vulnerable.
Los rangos oficiales, condiciones de aplicabilidad, árboles resueltos y
regresión están en [resultados H-06](docs/items/RESULTADOS.md#h-06).
Dependency Review de GitHub falla por falta de soporte/configuración de
Dependency graph en el repositorio; el responsable externo debe habilitarlo
y reejecutarlo. No se modificaron workflows ni se sustituyó ese gate por
la comparación local. No se declara ausencia global de vulnerabilidades.

## H-07: responsabilidades verificadas

No se reescribió producción: la separación existente cumple el plan.
`AuthServiceTest` usa dobles sin SQL ni hashing real para verificar
consulta exacta, ausencia sin verificación y una única verificación de la
contraseña original; devuelve solo `UserRecord`. Los tests HTTP mantienen
DTOs sin hash/ID y validación sin normalizar credenciales.
Resultado final: 51 tests, `mvn verify` salida 0, gate por clase >=85% intacto.
