# Sprint 1 - Módulo de Seguridad (CIMSE)

## 1. Autenticación
- **Tipo:** token **JWT** (JSON Web Token) firmado con HS256, válido por 10 horas.
- **Flujo:** `POST /api/auth/login` (usuario y contraseña) -> el backend devuelve el token -> el frontend lo guarda y lo envía en cada petición (`Authorization: Bearer <token>`).
- **Sin sesiones en el servidor** (stateless). El formulario de login por defecto de Spring está desactivado.
- En **cada petición** el filtro `JwtFilter` valida el token y confirma en la BD que el usuario sigue activo y qué permisos tiene su rol.
  Si el administrador desactiva un usuario o le cambia el rol, el efecto es inmediato.
- Un token inválido o vencido devuelve **401** y el sistema regresa al login.

## 2. Contraseñas
- **Cifrado:** BCrypt (hash con sal; nunca se guarda ni se muestra la contraseña).
- **Política:** mínimo **8 caracteres**, al menos **una letra** y **un número**, y no puede contener el nombre de usuario.
  Se valida en el servidor (`PoliticaClave`) y se muestra en pantalla mientras se escribe.
- Los usuarios nuevos y los que reciben una clave temporal **deben cambiarla en su primer ingreso**.
- **Recuperación:** el usuario pulsa "¿Olvidó su contraseña?" y deja una solicitud; el administrador la ve en *Seguridad > Usuarios*
  y le asigna una clave temporal (el sistema todavía no envía correos).

## 3. Usuarios
- **Un usuario por empleado** (no hay usuarios genéricos). Lo crea el **administrador**, no recursos humanos.
- **Regla del nombre de usuario:** primer nombre + `.` + primer apellido, en minúsculas y sin tildes (ej. `juan.perez`). Si ya existe se agrega un número. El administrador puede ajustarlo antes de guardar.
- Un empleado solo puede tener un usuario.
- Se puede **cambiar el rol** de un usuario (ej. un supervisor que sube a gerente conserva su usuario), **activar/desactivar** y **restablecer su clave**.
- Protecciones: siempre debe quedar al menos un administrador activo; un administrador no puede quitarse su propio rol ni desactivarse.

## 4. Roles y permisos (dinámicos)
- Tablas `roles`, `permisos` y `rol_permisos` (definidas en `database/schema.sql`). El administrador **crea roles nuevos desde la web** (ej. `ASISTENTE`), elige a qué módulos tienen acceso y los asigna con un clic. No hay roles "quemados" en el código.
- Roles base del sistema: ADMINISTRADOR, GERENTE_GENERAL, GERENTE_OPERACIONES, SUPERVISOR, GUARDIA (no se pueden eliminar).
- El ADMINISTRADOR siempre tiene todos los permisos. Un rol en uso no se puede eliminar ni desactivar.
- El menú y las rutas del frontend se filtran por permisos; la API protege `/api/seguridad/**` con el permiso `SEGURIDAD`.

## 5. Auditoría
Tabla `auditoria` con usuario, acción, tabla, registro, fecha y detalle (antes/después). Se registran:
`LOGIN`, `LOGIN_FALLIDO`, `INSERT`/`UPDATE`/`DELETE` (usuarios y roles), `CAMBIO_ROL`, `CAMBIO_CLAVE`, `RESTABLECER_CLAVE`, `SOLICITUD_CLAVE`.
Se consulta en *Seguridad > Auditoría* con filtros por usuario y acción.

## 6. Dashboard
Arriba a la derecha: nombre, usuario y rol del usuario conectado, el estado **Conectado** (verde) / Sin conexión (rojo) que se revisa cada 30 s, y los botones de cambiar contraseña y cerrar sesión.

## 7. Endpoints
| Método | Ruta | Descripción |
|---|---|---|
| POST | /api/auth/login | Iniciar sesión |
| POST | /api/auth/recuperar | Solicitar recuperación de contraseña |
| GET | /api/me | Datos del usuario conectado |
| POST | /api/me/clave | Cambiar la propia contraseña |
| GET/POST | /api/seguridad/roles | Listar / crear roles |
| PUT/DELETE | /api/seguridad/roles/{id} | Editar / eliminar rol |
| GET | /api/seguridad/permisos | Catálogo de permisos |
| GET/POST | /api/seguridad/usuarios | Listar / crear usuarios |
| PUT | /api/seguridad/usuarios/{id}/rol | Cambiar rol |
| PUT | /api/seguridad/usuarios/{id}/estado | Activar / desactivar |
| POST | /api/seguridad/usuarios/{id}/restablecer-clave | Asignar clave temporal |
| GET | /api/seguridad/empleados-disponibles | Empleados sin usuario |
| GET | /api/seguridad/solicitudes-clave | Solicitudes de recuperación pendientes |
| GET | /api/seguridad/auditoria | Consultar auditoría |

## 8. Pendiente para siguientes sprints
Envío de la clave temporal por correo, bloqueo por intentos fallidos, y las pantallas finales de los demás módulos (hoy muestran las tablas de la BD).
