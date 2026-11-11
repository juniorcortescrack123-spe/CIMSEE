import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const VACIO = { id: null, nombre: "", descripcion: "", permisos: [], esSistema: false };

export default function Roles() {
  const [roles, setRoles] = useState([]);
  const [catalogo, setCatalogo] = useState([]);
  const [form, setForm] = useState(VACIO);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });

  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () => {
    api.get("/seguridad/roles").then((r) => setRoles(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/seguridad/permisos").then((r) => setCatalogo(r.data)).catch(() => {});
  };
  useEffect(cargar, []);

  const editando = form.id !== null;
  const esAdmin = form.nombre === "ADMINISTRADOR";

  const marcar = (codigo) => {
    const tiene = form.permisos.includes(codigo);
    setForm({ ...form, permisos: tiene ? form.permisos.filter((p) => p !== codigo) : [...form.permisos, codigo] });
  };

  const guardar = async (e) => {
    e.preventDefault();
    try {
      if (editando) {
        await api.put(`/seguridad/roles/${form.id}`, { descripcion: form.descripcion, permisos: form.permisos });
        avisar("ok", "Rol actualizado.");
      } else {
        const r = await api.post("/seguridad/roles", {
          nombre: form.nombre, descripcion: form.descripcion, permisos: form.permisos,
        });
        avisar("ok", `Rol ${r.data.nombre} creado. Ya puede asignarlo a un usuario.`);
      }
      setForm(VACIO);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const editar = (r) => {
    setMsg({ tipo: "", texto: "" });
    setForm({ id: r.id, nombre: r.nombre, descripcion: r.descripcion || "", permisos: r.permisos, esSistema: r.esSistema });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const alternar = async (r) => {
    try {
      await api.put(`/seguridad/roles/${r.id}`, { descripcion: r.descripcion, activo: !r.activo });
      avisar("ok", `Rol ${r.activo ? "desactivado" : "activado"}.`);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const eliminar = async (r) => {
    if (!window.confirm(`¿Eliminar el rol ${r.nombre}?`)) return;
    try {
      await api.delete(`/seguridad/roles/${r.id}`);
      avisar("ok", "Rol eliminado.");
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  return (
    <>
      <section className="card">
        <h1>Roles y permisos</h1>
        <p className="muted">
          Los roles son dinámicos: cree uno nuevo, elija a qué módulos tiene acceso y asígnelo a un usuario.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">{editando ? `Editar rol ${form.nombre}` : "Crear rol nuevo"}</h2>
        <form onSubmit={guardar} className="form-col">
          <div className="form-grid">
            <div>
              <label>Nombre del rol</label>
              <input className="input" value={form.nombre} disabled={editando} placeholder="Ej. ASISTENTE"
                     onChange={(e) => setForm({ ...form, nombre: e.target.value })} required />
            </div>
            <div>
              <label>Descripción</label>
              <input className="input" value={form.descripcion} onChange={(e) => setForm({ ...form, descripcion: e.target.value })} />
            </div>
          </div>

          <label>Permisos (módulos a los que tendrá acceso)</label>
          <div className="checks">
            {catalogo.map((p) => (
              <label key={p.codigo} className="check">
                <input type="checkbox" checked={form.permisos.includes(p.codigo)} disabled={esAdmin}
                       onChange={() => marcar(p.codigo)} /> {p.descripcion}
              </label>
            ))}
          </div>
          {esAdmin && <small className="muted">El rol ADMINISTRADOR siempre tiene todos los permisos.</small>}

          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button type="submit" className="btn btn-dark">{editando ? "GUARDAR CAMBIOS" : "CREAR ROL"}</button>
            {editando && <button type="button" className="btn btn-outline" onClick={() => setForm(VACIO)}>CANCELAR</button>}
          </div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Roles existentes</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Rol</th><th>Descripción</th><th>Permisos</th><th>Usuarios</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              {roles.map((r) => (
                <tr key={r.id}>
                  <td><strong>{r.nombre}</strong>{r.esSistema && <span className="badge badge-sis">base</span>}</td>
                  <td>{r.descripcion}</td>
                  <td>{r.permisos.length === 0 ? <span className="muted">Sin permisos</span> : r.permisos.map((p) => <span key={p} className="chip">{p}</span>)}</td>
                  <td>{r.usuarios}</td>
                  <td><span className={"badge " + (r.activo ? "badge-ok" : "badge-off")}>{r.activo ? "Activo" : "Inactivo"}</span></td>
                  <td className="acciones">
                    <button className="btn btn-outline btn-sm" onClick={() => editar(r)}>Editar</button>
                    {r.nombre !== "ADMINISTRADOR" && (
                      <button className="btn btn-outline btn-sm" onClick={() => alternar(r)}>{r.activo ? "Desactivar" : "Activar"}</button>
                    )}
                    {!r.esSistema && <button className="btn btn-outline btn-sm" onClick={() => eliminar(r)}>Eliminar</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
