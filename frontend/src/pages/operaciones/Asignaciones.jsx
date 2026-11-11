import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const hoy = () => new Date().toISOString().slice(0, 10);
const VACIO = { idEmpleado: "", idPuesto: "", idTurno: "", fechaInicio: hoy(), fechaFin: "", observaciones: "" };

export default function Asignaciones() {
  const [filas, setFilas] = useState([]);
  const [guardias, setGuardias] = useState([]);
  const [puestos, setPuestos] = useState([]);
  const [turnos, setTurnos] = useState([]);
  const [form, setForm] = useState(VACIO);
  const [estado, setEstado] = useState("ACTIVA");
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () => {
    api.get("/asignaciones", { params: { estado } }).then((r) => setFilas(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/asignaciones/guardias").then((r) => setGuardias(r.data)).catch(() => {});
  };
  useEffect(() => { cargar(); }, [estado]);
  useEffect(() => {
    api.get("/puestos").then((r) => setPuestos(r.data.filter((p) => p.estado === "ACTIVO"))).catch(() => {});
    api.get("/turnos").then((r) => setTurnos(r.data)).catch(() => {});
  }, []);

  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  const asignar = async (e) => {
    e.preventDefault();
    try {
      await api.post("/asignaciones", {
        idEmpleado: Number(form.idEmpleado), idPuesto: Number(form.idPuesto), idTurno: Number(form.idTurno),
        fechaInicio: form.fechaInicio, fechaFin: form.fechaFin || null, observaciones: form.observaciones,
      });
      avisar("ok", "Guardia asignado al puesto.");
      setForm({ ...VACIO, fechaInicio: hoy() });
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const finalizar = async (a) => {
    if (!window.confirm(`¿Finalizar la asignación de ${a.guardia}?`)) return;
    try {
      await api.put(`/asignaciones/${a.id}/finalizar`, {});
      avisar("ok", "Asignación finalizada.");
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const cancelar = async (a) => {
    if (!window.confirm(`¿Cancelar la asignación de ${a.guardia}?`)) return;
    try {
      await api.put(`/asignaciones/${a.id}/cancelar`);
      avisar("ok", "Asignación cancelada.");
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  return (
    <>
      <section className="card">
        <h1>Asignación de guardias a puestos</h1>
        <p className="muted">
          Un guardia no puede tener dos asignaciones activas que se crucen en fechas dentro del mismo turno.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">Nueva asignación</h2>
        <form onSubmit={asignar} className="form-col">
          <div className="form-grid">
            <div>
              <label>Guardia *</label>
              <select className="input" value={form.idEmpleado} onChange={cambiar("idEmpleado")} required>
                <option value="">— Seleccione —</option>
                {guardias.map((g) => <option key={g.id} value={g.id}>{g.nombre} ({g.asignaciones} activa/s)</option>)}
              </select>
            </div>
            <div>
              <label>Puesto *</label>
              <select className="input" value={form.idPuesto} onChange={cambiar("idPuesto")} required>
                <option value="">— Seleccione —</option>
                {puestos.map((p) => <option key={p.id} value={p.id}>{p.cliente} — {p.descripcion}</option>)}
              </select>
            </div>
            <div>
              <label>Turno *</label>
              <select className="input" value={form.idTurno} onChange={cambiar("idTurno")} required>
                <option value="">— Seleccione —</option>
                {turnos.map((t) => <option key={t.id} value={t.id}>{t.nombre} ({t.horaInicio}-{t.horaFin})</option>)}
              </select>
            </div>
            <div><label>Fecha de inicio *</label><input className="input" type="date" value={form.fechaInicio} onChange={cambiar("fechaInicio")} required /></div>
            <div><label>Fecha de fin (opcional)</label><input className="input" type="date" value={form.fechaFin} onChange={cambiar("fechaFin")} /></div>
            <div><label>Observaciones</label><input className="input" value={form.observaciones} onChange={cambiar("observaciones")} /></div>
          </div>
          <div style={{ marginTop: 8 }}><button type="submit" className="btn btn-dark">ASIGNAR GUARDIA</button></div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
          <h2 className="section-title" style={{ margin: 0 }}>Asignaciones</h2>
          <select className="input" style={{ maxWidth: 200 }} value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="ACTIVA">Activas</option>
            <option value="FINALIZADA">Finalizadas</option>
            <option value="CANCELADA">Canceladas</option>
            <option value="">Todas</option>
          </select>
        </div>
        <div className="table-wrap" style={{ marginTop: 10 }}>
          <table>
            <thead><tr><th>Guardia</th><th>Cliente</th><th>Puesto</th><th>Turno</th><th>Desde</th><th>Hasta</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              {filas.map((a) => (
                <tr key={a.id}>
                  <td><strong>{a.guardia}</strong></td><td>{a.cliente}</td><td>{a.puesto}</td><td>{a.turno}</td>
                  <td>{String(a.fechaInicio)}</td><td>{a.fechaFin ? String(a.fechaFin) : "—"}</td>
                  <td><span className={"badge " + (a.estado === "ACTIVA" ? "badge-ok" : "badge-off")}>{a.estado}</span></td>
                  <td className="acciones">
                    {a.estado === "ACTIVA" && <button className="btn btn-outline btn-sm" onClick={() => finalizar(a)}>Finalizar</button>}
                    {a.estado === "ACTIVA" && <button className="btn btn-outline btn-sm" onClick={() => cancelar(a)}>Cancelar</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filas.length === 0 && <p className="muted">No hay asignaciones con ese filtro.</p>}
        </div>
      </section>
    </>
  );
}
