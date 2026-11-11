import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const VACIO = { idEmpleado: "", idActivo: "", idEmpleadoEntrega: "", kmInicial: "", estadoEntrega: "BUENO", observaciones: "" };
const fecha = (v) => String(v || "").replace("T", " ").slice(0, 16);

export default function AsignacionEquipos() {
  const navigate = useNavigate();
  const [filas, setFilas] = useState([]);
  const [empleados, setEmpleados] = useState([]);
  const [disponibles, setDisponibles] = useState([]);
  const [form, setForm] = useState(VACIO);
  const [estado, setEstado] = useState("ACTIVA");
  const [dev, setDev] = useState(null);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () => {
    api.get("/equipos/asignaciones", { params: { estado } }).then((r) => setFilas(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/equipos/disponibles").then((r) => setDisponibles(r.data)).catch(() => {});
  };
  useEffect(() => { cargar(); }, [estado]);
  useEffect(() => { api.get("/equipos/empleados").then((r) => setEmpleados(r.data)).catch(() => {}); }, []);

  const c = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });
  const activoElegido = disponibles.find((a) => String(a.id) === form.idActivo);

  const asignar = async (e) => {
    e.preventDefault();
    try {
      const r = await api.post("/equipos/asignaciones", {
        idEmpleado: Number(form.idEmpleado), idActivo: Number(form.idActivo),
        idEmpleadoEntrega: form.idEmpleadoEntrega ? Number(form.idEmpleadoEntrega) : null,
        kmInicial: form.kmInicial === "" ? null : form.kmInicial, estadoEntrega: form.estadoEntrega, observaciones: form.observaciones,
      });
      avisar("ok", r.data.mensaje);
      setForm(VACIO);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const devolver = async (e) => {
    e.preventDefault();
    try {
      const r = await api.post(`/equipos/asignaciones/${dev.a.id}/devolver`, {
        estadoDevolucion: dev.estadoDevolucion, kmFinal: dev.kmFinal === "" ? null : dev.kmFinal,
        idEmpleadoRecibe: dev.idEmpleadoRecibe ? Number(dev.idEmpleadoRecibe) : null, observaciones: dev.observaciones,
      });
      avisar("ok", r.data.mensaje);
      const id = dev.a.id;
      setDev(null);
      cargar();
      if (window.confirm("Devolución registrada. ¿Desea ver el acta de devolución?")) navigate(`/inventario/acta/${id}?tipo=DEVOLUCION`);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  return (
    <>
      <section className="card">
        <h1>Asignación de equipos</h1>
        <p className="muted">Entrega de armas, vehículos y equipo al personal, con acta de resguardo. Al entregar, el activo pasa a ASIGNADO; al devolverlo vuelve a DISPONIBLE o va a MANTENIMIENTO.</p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">Entregar equipo</h2>
        <form onSubmit={asignar} className="form-col">
          <div className="form-grid">
            <div>
              <label>Empleado que recibe *</label>
              <select className="input" value={form.idEmpleado} onChange={c("idEmpleado")} required>
                <option value="">— Seleccione —</option>{empleados.map((e) => <option key={e.id} value={e.id}>{e.nombre} ({e.cargo})</option>)}
              </select>
            </div>
            <div>
              <label>Activo disponible *</label>
              <select className="input" value={form.idActivo} onChange={c("idActivo")} required>
                <option value="">— Seleccione —</option>
                {disponibles.map((a) => <option key={a.id} value={a.id}>{a.tipo} — {a.descripcion} {a.serieOPlaca ? `(${a.serieOPlaca})` : ""}</option>)}
              </select>
            </div>
            <div>
              <label>Entrega (empleado) *</label>
              <select className="input" value={form.idEmpleadoEntrega} onChange={c("idEmpleadoEntrega")} required>
                <option value="">— Seleccione —</option>{empleados.map((e) => <option key={e.id} value={e.id}>{e.nombre}</option>)}
              </select>
            </div>
            <div>
              <label>Estado del equipo al entregar *</label>
              <select className="input" value={form.estadoEntrega} onChange={c("estadoEntrega")}><option>BUENO</option><option>REGULAR</option><option value="DANADO">DAÑADO</option></select>
            </div>
            {activoElegido?.tipo === "VEHICULO" && (
              <div><label>Kilometraje inicial *</label><input className="input" type="number" min="0" value={form.kmInicial} onChange={c("kmInicial")} required /></div>
            )}
            <div style={{ gridColumn: "1 / -1" }}><label>Observaciones *</label><textarea className="input" rows={4} value={form.observaciones} onChange={c("observaciones")} maxLength={200} required placeholder="Si no hay, escriba: Sin observaciones" /></div>
          </div>
          <div style={{ marginTop: 8 }}><button type="submit" className="btn btn-dark">ENTREGAR EQUIPO</button></div>
        </form>
        {disponibles.length === 0 && <p className="muted">No hay activos disponibles para entregar.</p>}
      </section>

      {dev && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2 className="section-title" style={{ marginTop: 0 }}>Registrar devolución — {dev.a.activo} ({dev.a.empleado})</h2>
          <form onSubmit={devolver} className="form-grid">
            <div>
              <label>Estado en que se devuelve *</label>
              <select className="input" value={dev.estadoDevolucion} onChange={(e) => setDev({ ...dev, estadoDevolucion: e.target.value })}>
                <option>BUENO</option><option>REGULAR</option><option value="DANADO">DAÑADO</option><option>INCOMPLETO</option><option>PERDIDO</option>
              </select>
            </div>
            {dev.a.tipo === "VEHICULO" && (
              <div><label>Kilometraje final * (inicial: {String(dev.a.kmInicial)})</label><input className="input" type="number" min="0" value={dev.kmFinal} onChange={(e) => setDev({ ...dev, kmFinal: e.target.value })} required /></div>
            )}
            <div>
              <label>Recibe (empleado) *</label>
              <select className="input" value={dev.idEmpleadoRecibe} onChange={(e) => setDev({ ...dev, idEmpleadoRecibe: e.target.value })} required>
                <option value="">— Seleccione —</option>{empleados.map((e) => <option key={e.id} value={e.id}>{e.nombre}</option>)}
              </select>
            </div>
            <div style={{ gridColumn: "1 / -1" }}><label>Observaciones *</label><textarea className="input" rows={4} value={dev.observaciones} onChange={(e) => setDev({ ...dev, observaciones: e.target.value })} maxLength={200} required placeholder="Si no hay, escriba: Sin observaciones" /></div>
            <div style={{ alignSelf: "end", display: "flex", gap: 8 }}>
              <button type="submit" className="btn btn-dark">REGISTRAR DEVOLUCIÓN</button>
              <button type="button" className="btn btn-outline" onClick={() => setDev(null)}>CANCELAR</button>
            </div>
          </form>
          <p className="muted">Si vuelve DAÑADO o INCOMPLETO pasa a mantenimiento; si está PERDIDO se da de baja.</p>
        </section>
      )}

      <section className="card" style={{ marginTop: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
          <h2 className="section-title" style={{ margin: 0 }}>Asignaciones de equipo</h2>
          <select className="input" style={{ maxWidth: 200 }} value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="ACTIVA">Vigentes</option><option value="DEVUELTA">Devueltas</option><option value="">Todas</option>
          </select>
        </div>
        <div className="table-wrap" style={{ marginTop: 10 }}>
          <table>
            <thead><tr><th>Acta</th><th>Empleado</th><th>Equipo</th><th>Serie / Placa</th><th>Entrega</th><th>Devolución</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              {filas.map((a) => (
                <tr key={a.id}>
                  <td>#{a.id}</td><td><strong>{a.empleado}</strong></td><td>{a.activo}</td><td>{a.serieOPlaca || "—"}</td>
                  <td>{fecha(a.fechaEntrega)} ({a.estadoEntrega})</td>
                  <td>{a.fechaDevolucion ? `${fecha(a.fechaDevolucion)} (${a.estadoDevolucion})` : "—"}</td>
                  <td><span className={"badge " + (a.activa ? "badge-sis" : "badge-ok")}>{a.activa ? "En uso" : "Devuelto"}</span></td>
                  <td className="acciones">
                    <button className="btn btn-outline btn-sm" onClick={() => navigate(`/inventario/acta/${a.id}?tipo=ENTREGA`)}>Acta de entrega</button>
                    {a.activa
                      ? <button className="btn btn-dark btn-sm" onClick={() => setDev({ a, estadoDevolucion: "BUENO", kmFinal: "", idEmpleadoRecibe: "", observaciones: "" })}>Devolver</button>
                      : <button className="btn btn-outline btn-sm" onClick={() => navigate(`/inventario/acta/${a.id}?tipo=DEVOLUCION`)}>Acta de devolución</button>}
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
