# Sprint 1 - Procedimiento para crear usuarios y manejar contraseñas

## 1. Alta de un usuario (lo hace el ADMINISTRADOR; nadie se registra solo)
1. **Personal > Registrar empleado**: se llenan TODOS los campos (identidad de 13 dígitos, nombre completo, fecha de nacimiento, teléfono `9999-9999`, correo, cargo, dirección y sueldo; el sueldo solo lo ve y lo escribe el administrador). El correo es obligatorio porque ahí llega la clave.
2. **Seguridad > Usuarios > Crear usuario**: se elige al empleado (solo aparecen los que aún no tienen usuario), el sistema sugiere el usuario (`primernombre.primerapellido`) y se marcan uno o más roles.
3. El **sistema genera** la contraseña temporal (8+ caracteres, mayúscula, número y carácter especial), la guarda cifrada (BCrypt) y la **envía al correo** del empleado. Si el correo no está configurado, se muestra una sola vez al administrador para entregarla en persona.
4. La clave temporal **vence** (3 días por defecto, `CLAVE_DIAS_VIGENCIA`). Al primer ingreso el sistema obliga a cambiarla.

## 2. Cambio de contraseña
Pantalla *Cambiar contraseña*: contraseña actual, nueva y confirmación. Reglas: mínimo 8 caracteres, una mayúscula, un número y un carácter especial.

## 3. Olvidé mi contraseña
En el login, "¿Olvidó su contraseña?" deja una solicitud al administrador. El administrador usa **Restaurar contraseña**: se genera otra clave temporal, se envía por correo y la cuenta se desbloquea.

## 4. Bloqueo por intentos fallidos
- A cada contraseña incorrecta el login indica cuántos intentos quedan.
- Al **3.er intento fallido** la cuenta se bloquea y la **pantalla de inicio de sesión queda bloqueada** (aunque se recargue la página). Ni con la clave correcta se puede entrar.
- Se crea una solicitud para el administrador (aviso en la barra superior y por correo a los demás administradores).
- Solo el administrador la libera: **Desbloquear** (conserva su clave) o **Restaurar contraseña** (nueva clave temporal).
- Excepción de seguridad: si se bloquea el único administrador disponible, se libera sola a los 15 minutos para no dejar el sistema sin acceso.
- Todo queda en la tabla AUDITORIA (LOGIN_FALLIDO, BLOQUEO_CUENTA, DESBLOQUEO, RESTABLECER_CLAVE).

## 5. Roles y permisos
Los permisos se asignan a los **roles** y los roles a los **usuarios** (tablas `roles`, `rol_permisos`, `usuario_roles`). Un usuario puede tener varios roles; sus permisos son la suma de todos.
