import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const VACIO = { id: null, idCliente: "", descripcion: "", ubicacion: "", tipoTurno: "12h", horas: "", estado: "ACTIVO" };

export default function Puestos() {
  const [puestos, setPuestos] = useState([]);
  const [clientes, setClientes] = useState([]);
  const [form, setForm] = useState(VACIO);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () => {
    api.get("/puestos").then((r) => setPuestos(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/clientes").then((r) => setClientes(r.data)).catch(() => {});
  };
  useEffect(() => { cargar(); }, []);

  const editando = form.id !== null;
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  const guardar = async (e) => {
    e.preventDefault();
    const datos = {
      idCliente: Number(form.idCliente), descripcion: form.descripcion, ubicacion: form.ubicacion,
      tipoTurno: form.tipoTurno, horas: form.horas === "" ? null : Number(form.horas), estado: form.estado,
    };
    try {
      if (editando) {
        await api.put(`/puestos/${form.id}`, datos);
        avisar("ok", "Puesto actualizado.");
      } else {
        await api.post("/puestos", datos);
        avisar("ok", "Puesto registrado.");
      }
      setForm(VACIO);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const editar = (p) => {
    setForm({ id: p.id, idCliente: String(p.idCliente), descripcion: p.descripcion, ubicacion: p.ubicacion || "", tipoTurno: p.tipoTurno, horas: String(p.horas ?? ""), estado: p.estado });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const alternar = async (p) => {
    try {
      await api.put(`/puestos/${p.id}`, {
        idCliente: p.idCliente, descripcion: p.descripcion, ubicacion: p.ubicacion, tipoTurno: p.tipoTurno,
        horas: p.horas, estado: p.estado === "ACTIVO" ? "INACTIVO" : "ACTIVO",
      });
      avisar("ok", "Estado del puesto actualizado.");
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  return (
    <>
      <section className="card">
        <h1>Puestos de trabajo</h1>
        <p className="muted">Lugares donde se presta el servicio. Cada puesto pertenece a un cliente.</p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">{editando ? `Editar puesto #${form.id}` : "Registrar puesto"}</h2>
        <form onSubmit={guardar} className="form-col">
          <div className="form-grid">
            <div>
              <label>Cliente *</label>
              <select className="input" value={form.idCliente} onChange={cambiar("idCliente")} required>
                <option value="">— Seleccione —</option>
                {clientes.map((c) => <option key={c.idCliente} value={c.idCliente}>{c.nombre}</option>)}
              </select>
            </div>
            <div><label>Descripción *</label><input className="input" value={form.descripcion} onChange={cambiar("descripcion")} required maxLength={100} /></div>
            <div><label>Ubicación *</label><input className="input" value={form.ubicacion} onChange={cambiar("ubicacion")} required maxLength={200} placeholder="Ciudad, edificio" /></div>
            <div>
              <label>Tipo de turno *</label>
              <select className="input" value={form.tipoTurno} onChange={cambiar("tipoTurno")}>
                <option value="12h">12h</option><option value="24h">24h</option>
              </select>
            </div>
            <div><label>Horas contratadas *</label><input className="input" type="number" min="1" max="24" value={form.horas} onChange={cambiar("horas")} required placeholder="12 o 24" /></div>
            {editando && (
              <div>
                <label>Estado</label>
                <select className="input" value={form.estado} onChange={cambiar("estado")}>
                  <option>ACTIVO</option><option>INACTIVO</option>
                </select>
              </div>
            )}
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button type="submit" className="btn btn-dark">{editando ? "GUARDAR CAMBIOS" : "REGISTRAR PUESTO"}</button>
            {editando && <button type="button" className="btn btn-outline" onClick={() => setForm(VACIO)}>CANCELAR</button>}
          </div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Puestos registrados</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Cliente</th><th>Puesto</th><th>Ubicación</th><th>Turno</th><th>Guardias</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              {puestos.map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td><td>{p.cliente}</td><td><strong>{p.descripcion}</strong></td><td>{p.ubicacion}</td>
                  <td>{p.tipoTurno} ({p.horas} h)</td><td>{p.guardias}</td>
                  <td><span className={"badge " + (p.estado === "ACTIVO" ? "badge-ok" : "badge-off")}>{p.estado}</span></td>
                  <td className="acciones">
                    <button className="btn btn-outline btn-sm" onClick={() => editar(p)}>Editar</button>
                    <button className="btn btn-outline btn-sm" onClick={() => alternar(p)}>{p.estado === "ACTIVO" ? "Inactivar" : "Activar"}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {puestos.length === 0 && <p className="muted">Aún no hay puestos registrados.</p>}
        </div>
      </section>
    </>
  );
}
