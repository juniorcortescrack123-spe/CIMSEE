# Ajustes de la revisión del 27 de octubre (Sprints 1 y 2)

| # | Lo que pidió la licenciada | Estado |
|---|---|---|
| 1 | Procedimiento de creación de usuarios en el Sprint 1 | `docs/Procedimiento_Usuarios.md` |
| 2 | Empleado con cargo y sueldo (sueldo solo para el administrador) | Hecho |
| 3 | Asignar el usuario al empleado desde Usuarios | Hecho |
| 4 | Contraseña generada por el sistema y enviada por correo, con tiempo para cambiarla | Hecho (requiere `.env` para el correo) |
| 5 | Cambio de contraseña: actual, nueva, confirmar; 8 caracteres, mayúscula, especial | Hecho |
| 6 | Recuperar/restaurar contraseña | Hecho |
| 7 | Varios roles por usuario; permisos por rol | Hecho |
| 8 | Cliente con RTN, teléfono celular, correo, dirección | Hecho |
| 9 | Varias personas por cliente (antes "contacto"; ahora **Persona de enlace**) | Hecho |
| 10 | Contrato: fechas, monto, día de pago 15/30, CAI/facturas, observaciones, PDF escaneado en ruta fija del servidor | Hecho (el PDF se adjunta al registrar) |
| 11 | Alertas a 7, 30, 60 y 90 días y vencidos | Hecho |
| 12 | Misma posición del "Detalle" en todas las pantallas (siempre debajo de la tabla) | Hecho |
| 13 | El cliente no se elimina | Hecho |
| 14 | Asignar guardias desde el cliente en una tabla tipo Excel, con horario y ubicación exacta | Hecho |
| 15 | Validar formatos | Identidad 13 dígitos, RTN 14, teléfono 8 con guion automático, correo |
| 16 | Todos los campos obligatorios | Hecho (excepciones: fecha de retiro y "Hasta" de la asignación) |
| 17 | 3 intentos fallidos bloquean el login hasta que el administrador intervenga | Hecho |

## Correcciones técnicas incluidas
- Un empleado nuevo se guardaba con estado `activo` vacío y no aparecía para crearle usuario ni asignarlo a un puesto. Corregido (y se repara al iniciar el backend).
- `/api/personal` ahora exige el permiso PERSONAL (antes cualquier usuario con sesión podía usarlo).
- El alta directa `POST /api/clientes` ahora valida igual que la pantalla.

## Sprint 3 (revisado contra el plan)
Ya estaban: activos con serie única (armas) y placa única (vehículos), cambio automático de estado (Disponible/Asignado/Mantenimiento), asignación de equipos, devoluciones con acta digital, movimientos, supervisiones, incidencias y guardia comodín.
Ajuste aplicado: campos obligatorios en activos (valor, ubicación, fecha de adquisición y datos del tipo), incidencias (supervisor), supervisiones (observaciones) y equipos (quien entrega/recibe, kilometraje y observaciones). Opcionales a propósito: código de inventario (se genera), "incidencia detectada" y la serie del equipo de protección.

## Sprint 4 (administración, reportes, notificaciones y nube)
- **Proveedores** (RTN 14 dígitos, teléfono 9999-9999, persona de enlace) y **Compras e insumos** (cabecera + detalle, total calculado por el servidor, factura única por proveedor). Las compras de papelería y materiales actualizan solas las existencias del catálogo.
- **Pagos** (RAP, IHSS, canon, alcaldía, otros) y **Permisos legales** (alcaldía, armería, operación) con semáforo y renovación.
- **Centro de notificaciones**: alertas automáticas a 30 días (contratos, permisos, pagos, matrículas de vehículos) guardadas en `ALERTAS_VENCIMIENTO`; se cierran solas al renovar/pagar o se marcan atendidas/ignoradas con observación. Campana con contador en la barra superior.
- **Reportes**: tablero con indicadores de personal, comercial, operaciones, inventario y administración, y lista de vencimientos (imprimible).
- **Nube**: `docker-compose.prod.yml` + `frontend/Dockerfile.prod` + nginx; guía en `docs/Despliegue_Nube.md`.

### Pantallas eliminadas
Se quitaron las pantallas genéricas que solo mostraban una tabla de la base de datos: Gestión de Personal, Supervisión, Clientes, Contratos, Pagos y Permisos, Proveedores, Inventario, Asignación de Equipos y Reportes (versión de tabla), además del "visor rápido de base de datos" del panel principal. Cada una ya tiene su pantalla real y las direcciones viejas redirigen a ella. El menú quedó en grupos: Seguridad, Personal, Gestión Comercial, Operaciones, Inventario y Equipos, Administración y Reportes y alertas.
El visor genérico `/api/tablas` quedó restringido al administrador.

## Revisión del 3 de noviembre (correcciones de la licenciada)
| Lo que pidió | Qué se hizo |
|---|---|
| El supervisor no elige quién es: al entrar con el rol SUPERVISOR el sistema ya lo sabe | `Visitas de supervisión`: lee el usuario y su rol; el administrador/gerente sin rol de supervisor elige a cuál supervisor representa |
| Ver los clientes a su cargo y sus guardias, y pasar asistencia como en una tabla | Nueva tabla `supervisor_clientes` (se asigna en Gestión de clientes > Detalle > Supervisores a cargo). El supervisor elige cliente y marca Presente/Ausente guardia por guardia |
| Falta con excusa o sin excusa, con lista desplegable del motivo | Tipo de falta + motivo (permiso, incapacidad, emergencia, enfermedad, sin aviso, abandono, otro); la asistencia queda PRESENTE, JUSTIFICADO o AUSENTE |
| Observación específica por guardia y una general; botón Finalizar | Observación por guardia + observaciones generales obligatorias; `FINALIZAR VISITA` guarda todo en una sola operación |
| Incidencias a nivel de guardia (por fila) y a nivel de visita/establecimiento | Incidencias con nivel GUARDIA o ESTABLECIMIENTO; tipos nuevos: daño a la propiedad, pérdida o robo de equipo/arma, asalto. Se crean desde la visita o en la pantalla de Incidencias |
| Tipos de activo ampliables solo por el administrador | Catálogo `tipos_activo` (botón "Tipos y ubicaciones" en Activos fijos, solo administrador) |
| Ubicaciones y traslado con lista de almacenes, no texto libre | Catálogo `ubicaciones_inventario`; registrar y trasladar eligen de la lista (el destino no puede ser la ubicación actual) |
| Observaciones en una caja más grande | Cuadros de texto de varias líneas en activos, equipos, contratos, compras, pagos, visitas e incidencias |
| Ordenar el menú para las distintas vistas (administrador, supervisor) | Nuevo permiso VISITAS: el supervisor solo ve "Visitas de supervisión" e "Incidencias"; Seguridad pasó al final del menú |
| Quién registra la devolución (empleado o administrador) | **Por confirmar con el cliente.** Hoy la registra quien tiene el permiso EQUIPOS (administrador/gerentes) |

Notas: el supervisor solo puede visitar a los clientes que tiene asignados. Si la base ya existía, el backend crea las tablas nuevas al iniciar, pero hay que asignar los supervisores a sus clientes (la base nueva ya trae al supervisor de prueba con los dos clientes).

## Portal del guardia (rol GUARDIA)
- **Mi puesto** (`/guardia`): el guardia ve su supervisor (nombre y teléfono), su lugar asignado con turno y horario, y su equipo asignado.
- **Solicitudes**: el guardia solo *pide* cambio de turno, cambio de equipo o devolución de equipo. El supervisor del cliente donde trabaja recibe el aviso (campana 🔔 y Centro de notificaciones), aprueba o rechaza con un comentario, y el guardia ve la respuesta en su pantalla y en su campana. La aprobación es la autorización: el cambio en sí lo registra el administrador en Asignación a puestos o Asignación de equipos.
- **Incidencias del guardia**: el guardia las reporta (suyas o del lugar); se asignan solas al supervisor de su cliente, que recibe el aviso. El guardia ve cómo va su incidencia pero **no puede cambiar su estado**.
- **Estado de las incidencias**: solo el supervisor asignado a la incidencia (y el administrador, como respaldo) puede ponerla EN PROCESO o RESUELTA.
- Nuevo permiso `PORTAL_GUARDIA` (rol GUARDIA). Usuarios de prueba: `guardia1` (Juan Perez, Banco Ejemplo) y `guardia2` (Carlos Lopez, CC Norte), clave `123456`.
- Tabla nueva: `solicitudes_guardia`. Pantalla nueva para el supervisor: Operaciones > Solicitudes de guardias.

## Arranque de Docker
Si el backend se cerraba con "Connection to db:5432 refused" en la primera carga, era porque PostgreSQL todavía se inicializaba. Ahora el healthcheck espera a que Postgres escuche por red (`pg_isready -h 127.0.0.1`) y el backend se reinicia solo si la base aún no responde.

## Vacaciones, avisos del gerente y rol de personal administrativo
- **Vacaciones**: el guardia puede pedir vacaciones (fecha de inicio y fecha final; no pueden empezar en el pasado). Llega a su supervisor como las demás solicitudes y se aprueba o rechaza con comentario.
- **Gerentes (GERENTE_GENERAL y GERENTE_OPERACIONES)**: en la campana y el Centro de notificaciones ven lo que pasa en campo aunque no vaya a su nombre: incidencias abiertas, visitas de las últimas 48 horas con ausencias o incidencias, y solicitudes de guardias esperando a su supervisor. En Operaciones ya ven todas las visitas, incidencias y solicitudes. El registro de personas lo conservan (permiso PERSONAL).
- **Mi equipo asignado**: cualquier persona con un vehículo, arma u otro equipo a su nombre lo ve en el panel principal (aplica a gerentes, supervisores y guardias).
- **Rol ADMINISTRATIVO** (usuario de prueba `administrativo1`, clave `123456`): solo ve Reportes, Centro de notificaciones, Compras e insumos, Pagos, Activos fijos, Movimientos, Gestión de contratos, Alertas de contratos y Cadena relacional. Para que esto sea posible se separaron permisos que estaban juntos: `COMPRAS` (antes dentro de Proveedores), `PERMISOS_LEGALES` (antes dentro de Pagos) y `CADENA` (antes dentro de Clientes). Los roles que ya tenían Proveedores o Pagos conservan Compras y Permisos legales automáticamente. Cada pantalla cargó sus listas auxiliares (proveedores, clientes, permisos) por su propio permiso, para que funcione sin pedir acceso a las demás.
