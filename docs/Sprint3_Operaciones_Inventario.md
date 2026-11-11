# Sprint 3 - Operaciones, Inventario y Activos Fijos (CIMSE)

Plan Scrum: PB-07 (supervisiones e incidencias), PB-08 (control único de activos) y PB-09 (asignación de equipos y devoluciones con actas).
La base de datos **no cambió**: las tablas `supervision_operativa`, `asistencia_diaria`, `incidencias`, `inventario_activos`, `armas`, `vehiculos`, `uniformes`, `asignacion_equipos` y `movimientos_inventario` ya existían en `database/schema.sql`.

## Ciclo de vida del activo
`Ingreso (ENTRADA) -> DISPONIBLE -> Entrega (SALIDA) -> ASIGNADO -> Devolución -> DISPONIBLE o MANTENIMIENTO`
Cada paso genera un registro en `movimientos_inventario` y en la auditoría.

## Reglas de negocio
**Activos (PB-08)**
- Tipos: ARMA, VEHICULO, UNIFORME y EQUIPO_PROTECCION. Cada activo nace de una compra registrada.
- **Arma:** número de serie obligatorio y único; marca y tipo de arma obligatorios.
- **Vehículo:** placa obligatoria y única; tipo y marca obligatorios.
- La serie o la placa **no se pueden modificar** después del registro (control único).
- Un activo ASIGNADO no puede pasar a mantenimiento ni trasladarse; primero se registra su devolución.
- Un activo dado de baja no se puede asignar, trasladar ni cambiar de estado.

**Asignación de equipos (PB-09)**
- Solo se entregan activos DISPONIBLES, sin baja y sin una asignación vigente.
- Las armas solo se entregan a empleados activos con cargo GUARDIA o SUPERVISOR.
- Vehículos: se registra el kilometraje inicial (no puede ser menor al actual) y al devolver el final (no puede ser menor al inicial); el kilometraje del vehículo se actualiza.
- **Devolución:** BUENO o REGULAR -> el activo queda DISPONIBLE; DAÑADO o INCOMPLETO -> MANTENIMIENTO; PERDIDO -> se da de baja.
- Cada entrega y devolución tiene su **acta digital** (se imprime o se guarda como PDF).
- Los uniformes se manejan por cantidad y no se asignan desde esta pantalla.

**Supervisiones e incidencias (PB-07)**
- La supervisión la registra un empleado activo con cargo SUPERVISOR sobre una asignación ACTIVA de un guardia.
- Si el guardia está **ausente**, el sistema genera automáticamente una incidencia de AUSENCIA para ese puesto.
- Si hubo falta o incidencia, se puede pedir un **guardia comodín**: se crea una asignación temporal de hoy en el mismo puesto y turno, con la misma regla de no sobreposición del Sprint 2.
- Una incidencia solo se registra sobre un puesto ACTIVO que tenga una asignación ACTIVA.
- El estado de la incidencia solo avanza: ABIERTA -> EN_PROCESO -> RESUELTA.

## Permisos
`SUPERVISION` (supervisiones e incidencias), `INVENTARIO` (activos y movimientos) y `EQUIPOS` (asignación de equipos y actas). El ADMINISTRADOR tiene todos.

## Endpoints
| Método | Ruta | Descripción |
|---|---|---|
| GET / POST | /api/supervisiones | Historial / registrar supervisión |
| GET | /api/supervisiones/{id} | Detalle de una supervisión |
| GET | /api/supervisiones/supervisores | Supervisores activos |
| GET / POST | /api/incidencias | Listar / registrar incidencia |
| PUT | /api/incidencias/{id}/estado | Avanzar el estado |
| POST | /api/incidencias/{id}/comodin | Solicitar guardia comodín |
| GET / POST | /api/activos | Listar (filtros) / registrar activo |
| GET / PUT | /api/activos/{id} | Detalle con historial / editar |
| PUT | /api/activos/{id}/estado | DISPONIBLE o MANTENIMIENTO |
| POST | /api/activos/{id}/trasladar | Trasladar de ubicación |
| GET | /api/activos/movimientos | Movimientos de inventario |
| GET | /api/activos/compras | Compras para dar de alta activos |
| GET / POST | /api/equipos/asignaciones | Listar / entregar equipo |
| POST | /api/equipos/asignaciones/{id}/devolver | Registrar devolución |
| GET | /api/equipos/asignaciones/{id}/acta | Datos del acta digital |
| GET | /api/equipos/disponibles | Activos que se pueden entregar |

## Pantallas (menú lateral)
**Operaciones:** Supervisiones, Incidencias.
**Inventario y Equipos:** Activos fijos, Asignación de equipos (con actas de entrega y devolución), Movimientos.

## Pruebas manuales sugeridas (evidencia)
1. Activos fijos > Registrar un arma con un número de serie nuevo: queda DISPONIBLE. Intentar registrar otra con la misma serie: se rechaza.
2. Registrar un vehículo con su placa; intentar repetir la placa: se rechaza.
3. Asignación de equipos > entregar el arma a un guardia: el activo pasa a ASIGNADO y se genera el acta de entrega.
4. Intentar entregar la misma arma otra vez: se rechaza (no está disponible).
5. Devolver el arma en estado DAÑADO: queda en MANTENIMIENTO; en Movimientos aparecen ENTRADA, SALIDA y DEVOLUCION.
6. Supervisiones > registrar una con guardia ausente: se genera la incidencia de AUSENCIA.
7. Incidencias > Comodín: se crea la asignación temporal del guardia de reemplazo.
8. Seguridad > Auditoría: aparecen todas las operaciones.
