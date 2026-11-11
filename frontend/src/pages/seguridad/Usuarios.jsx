import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const fecha = (v) => (v ? String(v).replace("T", " ").slice(0, 16) : "—");

export default function Usuarios() {
  const [usuarios, setUsuarios] = useState([]);
  const [roles, setRoles] = useState([]);
  const [empleados, setEmpleados] = useState([]);
  const [solicitudes, setSolicitudes] = useState([]);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const [temporal, setTemporal] = useState(null);
  const [form, setForm] = useState({ idEmpleado: "", nombreUsuario: "", roles: [] });
  const [edicion, setEdicion] = useState(null); // { usuario, roles }

  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () => {
    api.get("/seguridad/usuarios").then((r) => setUsuarios(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/seguridad/roles").then((r) => setRoles(r.data.filter((x) => x.activo))).catch(() => {});
    api.get("/seguridad/empleados-disponibles").then((r) => setEmpleados(r.data)).catch(() => {});
    api.get("/seguridad/solicitudes-clave").then((r) => setSolicitudes(r.data)).catch(() => {});
  };
  useEffect(cargar, []);

  const empleado = empleados.find((e) => String(e.id) === form.idEmpleado);

  const elegirEmpleado = (id) => {
    const emp = empleados.find((e) => String(e.id) === id);
    setForm({ ...form, idEmpleado: id, nombreUsuario: emp ? emp.sugerido : "" });
  };

  const alternarRol = (lista, nombre) => (lista.includes(nombre) ? lista.filter((r) => r !== nombre) : [...lista, nombre]);

  const mostrarResultado = (r) => {
    setTemporal(r.claveTemporal ? r : null);
    avisar(r.correoEnviado === false ? "err" : "ok", r.mensaje);
  };

  const crear = async (e) => {
    e.preventDefault();
    setTemporal(null);
    try {
      const r = await api.post("/seguridad/usuarios", {
        idEmpleado: Number(form.idEmpleado), nombreUsuario: form.nombreUsuario, roles: form.roles,
      });
      mostrarResultado({ ...r.data, mensaje: `Usuario ${r.data.usuario}: ${r.data.mensaje}` });
      setForm({ idEmpleado: "", nombreUsuario: "", roles: [] });
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const guardarRoles = async () => {
    try {
      const r = await api.put(`/seguridad/usuarios/${edicion.usuario.id}/roles`, { roles: edicion.roles });
      avisar("ok", r.data.mensaje);
      setEdicion(null);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const cambiarEstado = async (u) => {
    try {
      const r = await api.put(`/seguridad/usuarios/${u.id}/estado`, { activo: !u.activo });
      avisar("ok", r.data.mensaje);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const desbloquear = async (id) => {
    try {
      const r = await api.post(`/seguridad/usuarios/${id}/desbloquear`, {});
      avisar("ok", r.data.mensaje);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const restablecer = async (id) => {
    if (!window.confirm("¿Restaurar la contraseña? Se generará una clave temporal, se enviará por correo al usuario y se desbloqueará su cuenta si estaba bloqueada.")) return;
    try {
      const r = await api.post(`/seguridad/usuarios/${id}/restablecer-clave`, {});
      mostrarResultado(r.data);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  return (
    <>
      <section className="card">
        <h1>Usuarios</h1>
        <p className="muted">
          Cada empleado tiene su propio usuario (primer nombre y primer apellido). El sistema genera la contraseña y la envía al
          correo del empleado; si no la cambia a tiempo, vence. Un usuario puede tener más de un rol.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}
        {temporal && (
          <p className="alerta alerta-info">
            Clave temporal de <strong>{temporal.usuario}</strong>: <code className="clave-temp">{temporal.claveTemporal}</code>
            {" "}— se muestra una sola vez. Entréguela en persona; el usuario deberá cambiarla al iniciar sesión.
          </p>
        )}
      </section>

      {solicitudes.length > 0 && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2 className="section-title" style={{ marginTop: 0 }}>Solicitudes pendientes (claves y cuentas bloqueadas)</h2>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Usuario</th><th>Motivo</th><th>Fecha</th><th></th></tr></thead>
              <tbody>
                {solicitudes.map((s) => (
                  <tr key={s.id}>
                    <td>{s.usuario}</td>
                    <td>{s.motivo === "BLOQUEO"
                      ? <span className="badge badge-rojo">Cuenta bloqueada (3 intentos fallidos)</span>
                      : <span className="badge badge-sis">Olvidó su contraseña</span>}</td>
                    <td>{fecha(s.fecha)}</td>
                    <td className="acciones">
                      {s.idUsuario && s.motivo === "BLOQUEO" && s.bloqueado && <button className="btn btn-outline btn-sm" onClick={() => desbloquear(s.idUsuario)}>Desbloquear</button>}
                      {s.idUsuario && <button className="btn btn-dark btn-sm" onClick={() => restablecer(s.idUsuario)}>Enviar nueva clave</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Crear usuario</h2>
        <form onSubmit={crear} className="form-col">
          <div className="form-grid">
            <div>
              <label>Empleado *</label>
              <select className="input" value={form.idEmpleado} onChange={(e) => elegirEmpleado(e.target.value)} required>
                <option value="">— Seleccione —</option>
                {empleados.map((e) => <option key={e.id} value={e.id}>{e.nombre} ({e.cargo})</option>)}
              </select>
              {empleado && (
                <small className={empleado.correo ? "muted" : ""} style={empleado.correo ? {} : { color: "#991b1b" }}>
                  {empleado.correo ? `La clave se enviará a ${empleado.correo}` : "Este empleado no tiene correo; agréguelo en su expediente."}
                </small>
              )}
            </div>
            <div>
              <label>Nombre de usuario *</label>
              <input className="input" value={form.nombreUsuario} onChange={(e) => setForm({ ...form, nombreUsuario: e.target.value })} required />
            </div>
          </div>
          <label>Roles * (puede elegir más de uno)</label>
          <div className="checks">
            {roles.map((r) => (
              <label key={r.id} className="check">
                <input type="checkbox" checked={form.roles.includes(r.nombre)} onChange={() => setForm({ ...form, roles: alternarRol(form.roles, r.nombre) })} /> {r.nombre}
              </label>
            ))}
          </div>
          <div style={{ marginTop: 8 }}>
            <button type="submit" className="btn btn-dark" disabled={form.roles.length === 0}>CREAR USUARIO Y ENVIAR CLAVE</button>
          </div>
        </form>
        {empleados.length === 0 && <p className="muted">Todos los empleados activos ya tienen usuario.</p>}
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Usuarios registrados</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Usuario</th><th>Empleado</th><th>Roles</th><th>Estado</th><th>Último acceso</th><th>Acciones</th></tr></thead>
            <tbody>
              {usuarios.map((u) => (
                <tr key={u.id}>
                  <td><strong>{u.usuario}</strong></td>
                  <td>{u.nombre}<br /><span className="muted">{u.correo || "sin correo"}</span></td>
                  <td>{u.roles.map((r) => <span key={r} className="chip">{r}</span>)}</td>
                  <td>
                    <span className={"badge " + (u.activo ? "badge-ok" : "badge-off")}>{u.activo ? "Activo" : "Inactivo"}</span>
                    {u.bloqueado && <span className="badge badge-rojo">Bloqueada</span>}
                    {u.debeCambiarClave && <span className="badge badge-sis" title={u.claveExpira ? "Vence " + fecha(u.claveExpira) : ""}>Clave temporal</span>}
                  </td>
                  <td>{fecha(u.ultimoAcceso)}</td>
                  <td className="acciones">
                    {u.bloqueado && <button className="btn btn-dark btn-sm" onClick={() => desbloquear(u.id)}>Desbloquear</button>}
                    <button className="btn btn-outline btn-sm" onClick={() => setEdicion({ usuario: u, roles: [...u.roles] })}>Roles</button>
                    <button className="btn btn-outline btn-sm" onClick={() => cambiarEstado(u)}>{u.activo ? "Desactivar" : "Activar"}</button>
                    <button className="btn btn-outline btn-sm" onClick={() => restablecer(u.id)}>Restaurar contraseña</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {edicion && (
        <section className="card" style={{ marginTop: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <h2 className="section-title" style={{ marginTop: 0 }}>Roles de {edicion.usuario.usuario}</h2>
            <button className="btn btn-outline btn-sm" onClick={() => setEdicion(null)}>Cerrar</button>
          </div>
          <p className="muted">Los permisos del usuario son la suma de los permisos de todos sus roles. Por ejemplo, un administrador puede ser también supervisor mientras se contrata a otro.</p>
          <div className="checks">
            {roles.map((r) => (
              <label key={r.id} className="check">
                <input type="checkbox" checked={edicion.roles.includes(r.nombre)} onChange={() => setEdicion({ ...edicion, roles: alternarRol(edicion.roles, r.nombre) })} /> {r.nombre}
              </label>
            ))}
          </div>
          <div style={{ marginTop: 10 }}>
            <button className="btn btn-dark" disabled={edicion.roles.length === 0} onClick={guardarRoles}>GUARDAR ROLES</button>
          </div>
        </section>
      )}
    </>
  );
}
