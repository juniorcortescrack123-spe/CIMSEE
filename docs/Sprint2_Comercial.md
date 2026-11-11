# Sprint 2 - Gestión Comercial y Relación Central (CIMSE)

Plan Scrum: PB-04 (asignación de guardias a puestos), PB-05 (clientes, contratos y puestos) y PB-06 (alertas de vencimiento).
La base de datos **no cambió**: las tablas `clientes`, `contratos`, `puestos_trabajo`, `turnos` y `asignacion_puestos` ya existían en `database/schema.sql`.

## Cadena relacional
`CLIENTE -> CONTRATO -> PUESTO -> ASIGNACIÓN <- GUARDIA`. En la pantalla **Cadena relacional** se recorre completa:
cada guardia asignado se rastrea hasta su puesto, su contrato y su cliente.

## Reglas de negocio
**Clientes**
- Nombre obligatorio; el RTN no se puede repetir.
- Un cliente solo se elimina si no tiene contratos ni puestos (protege la cadena).

**Contratos**
- Duración de 1 o 2 años; fecha de vencimiento posterior a la de inicio; monto mensual mayor que cero; día de pago 15 o 30.
- Estados: VIGENTE, VENCIDO, RENOVADO. **Renovar** marca el contrato como RENOVADO y crea uno nuevo VIGENTE que empieza cuando vencía el anterior (máximo 2 años).
- La lista muestra cuántos puestos activos tiene el cliente y avisa "Sin puestos" cuando no tiene ninguno.

**Alertas (semáforo)** - solo contratos VIGENTES
| Semáforo | Condición |
|---|---|
| VERDE | vence en más de 30 días |
| AMARILLO | vence en 30 días o menos |
| ROJO | vence en 7 días o menos |
| VENCIDO | la fecha de vencimiento ya pasó |

**Puestos**
- Pertenecen a un cliente; tipo de turno 12h o 24h; horas entre 1 y 24 (por defecto 12 o 24 según el tipo).
- No se puede inactivar un puesto con guardias asignados.

**Turnos**
- Catálogo fijo: MATUTINO, VESPERTINO y NOCTURNO. Se puede cambiar el horario (formato HH:mm).

**Asignación de guardias**
- Solo empleados activos con cargo GUARDIA; el puesto debe estar ACTIVO.
- **No sobreposición:** un guardia no puede tener dos asignaciones ACTIVAS cuyas fechas se crucen dentro del mismo turno. Se valida en el backend con un mensaje claro y, además, la base de datos lo impide con la restricción `ex_asp_sin_traslape` (EXCLUDE).
- Una asignación se puede finalizar o cancelar.

## Permisos
Cada módulo exige el permiso de su rol: `CLIENTES` (clientes, puestos, cadena), `CONTRATOS` (contratos y alertas) y `SUPERVISION` (turnos y asignaciones). Los puestos también los puede consultar quien tenga `SUPERVISION`.
El ADMINISTRADOR tiene todos los permisos. Roles y permisos se administran en *Seguridad > Roles y permisos*.

## Auditoría
Toda creación, edición, eliminación, renovación o cambio de estado de estos módulos se guarda en la tabla `auditoria` con el usuario real, la acción, la tabla, el registro afectado y el detalle. Se consulta en *Seguridad > Auditoría*.

## Endpoints
| Método | Ruta | Descripción |
|---|---|---|
| GET | /api/clientes | Listar clientes (ya existía) |
| POST | /api/clientes/registrar | Registrar cliente (con auditoría) |
| PUT / DELETE | /api/clientes/{id} | Editar / eliminar |
| GET | /api/clientes/{id}/detalle | Contratos, puestos y guardias del cliente |
| GET / POST | /api/contratos | Listar (con semáforo) / registrar |
| GET / PUT | /api/contratos/{id} | Consultar / editar |
| GET | /api/contratos/alertas?dias=30 | Contratos por vencer |
| POST | /api/contratos/{id}/renovar | Renovar contrato |
| GET / POST | /api/puestos | Listar / registrar puestos |
| PUT | /api/puestos/{id} | Editar / activar / inactivar |
| GET | /api/turnos | Listar turnos con guardias asignados |
| PUT | /api/turnos/{id} | Cambiar horario |
| GET / POST | /api/asignaciones | Listar / asignar guardia a puesto |
| GET | /api/asignaciones/guardias | Guardias disponibles |
| PUT | /api/asignaciones/{id}/finalizar | Finalizar asignación |
| PUT | /api/asignaciones/{id}/cancelar | Cancelar asignación |
| GET | /api/comercial/cadena | Recorrido CLIENTE-CONTRATO-PUESTO-ASIGNACIÓN-GUARDIA |

## Pantallas (menú lateral)
**Gestión Comercial:** Gestión de clientes (lista, registrar, editar, detalle), Gestión de contratos, Alertas de contratos, Puestos de trabajo, Cadena relacional.
**Operaciones:** Turnos, Asignación a puestos.

## Pruebas manuales sugeridas (evidencia)
1. Registrar un cliente y un puesto; luego un contrato con vencimiento en menos de 30 días: aparece AMARILLO en *Alertas de contratos*.
2. Asignar un guardia al puesto en el turno MATUTINO; volver a asignarlo al mismo turno con fechas que se crucen: el sistema lo rechaza.
3. Renovar el contrato desde *Alertas*: el viejo queda RENOVADO y aparece uno nuevo VIGENTE.
4. Intentar eliminar un cliente con contratos: el sistema lo rechaza.
5. Revisar *Seguridad > Auditoría*: aparecen todas las operaciones anteriores.
