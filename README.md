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
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.

## Correcciones locales y configuración

Esta copia implementa H-01 y H-02 de [HALLAZGOS.md](HALLAZGOS.md).
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

H-03 conserva el formato legado para respetar
[docs/ENDPOINTS.md](docs/ENDPOINTS.md). H-04, H-05 y H-06 no fueron corregidos;
se mantienen los errores HTTP documentados, incluido el 401 ante fallo de
consulta. H-07 no se cierra fuera del alcance aprobado.
