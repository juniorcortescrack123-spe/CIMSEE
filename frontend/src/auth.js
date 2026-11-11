// Manejo de la sesion en el navegador (el token JWT y los datos del usuario)

export function guardarSesion(datos) {
  localStorage.setItem("token", datos.token);
  localStorage.setItem(
    "sesion",
    JSON.stringify({
      username: datos.username,
      nombre: datos.nombre,
      rol: datos.rol,
      roles: datos.roles || (datos.rol ? [datos.rol] : []),
      permisos: datos.permisos || [],
      debeCambiarClave: !!datos.debeCambiarClave,
    })
  );
}

export function obtenerSesion() {
  if (!localStorage.getItem("token")) return null;
  try {
    return JSON.parse(localStorage.getItem("sesion"));
  } catch {
    return null;
  }
}

export function actualizarSesion(cambios) {
  const s = obtenerSesion();
  if (s) localStorage.setItem("sesion", JSON.stringify({ ...s, ...cambios }));
}

export function cerrarSesion() {
  localStorage.removeItem("token");
  localStorage.removeItem("sesion");
}

// Un modulo sin "permiso" es visible para todos los usuarios con sesion
// "codigo" puede ser un permiso o una lista de permisos (basta con tener uno)
export function tienePermiso(codigo) {
  if (!codigo) return true;
  const s = obtenerSesion();
  if (!s) return false;
  return Array.isArray(codigo) ? codigo.some((c) => s.permisos.includes(c)) : s.permisos.includes(codigo);
}

export function mensajeError(err, porDefecto = "Ocurrió un error. Intente de nuevo.") {
  if (err?.response?.data?.error) return err.response.data.error;
  if (!err?.response) return "Error de conexión con el servidor.";
  return porDefecto;
}
