# Hallazgos - Auth Backend

Registro de discovery del 2026-10-20. Los documentos de este repositorio mencionan una matriz historica H-01 a H-07 que no esta presente en el workspace. Este registro no reconstruye ni cambia sus estados; contiene solo candidatos nuevos para revision humana.

### H-CROSS-01 - Contrato JWT incompatible entre servicios
- Estado: CANDIDATO
- Evidencia: [TokenService.java](src/main/java/com/bcp/workshop/auth/TokenService.java#L46) emite un JWT firmado con RS256. La API de requisitos decodifica el Bearer como Base64 y espera cuatro campos delimitados por `|` en [main.py](../workshop-requirements-api/app/main.py#L17). El README de este repositorio indica que el token es consumido por la API Python ([README.md](README.md#L5)).
- Impacto: Un login exitoso puede no habilitar el flujo de tareas; la API puede rechazar el token emitido por este servicio.
- Prioridad propuesta: P1, por bloqueo del flujo integrado.
- Contrato potencialmente afectado: respuesta de `POST /auth/login` y autenticacion Bearer de la API de requisitos.
- Como validarlo: Prueba de integracion que obtenga un token de `POST /auth/login` y lo presente a `GET /api/tasks`; verificar que la API lo acepta y atribuye la identidad esperada.

Revisar el mismo candidato desde el consumidor en [workshop-login-frontend/HALLAZGOS.md](../workshop-login-frontend/HALLAZGOS.md) y desde el verificador en [workshop-requirements-api/HALLAZGOS.md](../workshop-requirements-api/HALLAZGOS.md).
