import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { TIPOS_INCIDENCIA } from "../../formato.js";

const VACIO = { nivel: "GUARDIA", idCliente: "", idPuesto: "", idEmpleadoAfectado: "", tipo: "", descripcion: "", idSupervisorAsignado: "" };
const COLOR = { ABIERTA: "badge-rojo", EN_PROCESO: "badge-sis", RESUELTA: "badge-ok" };
const fecha = (v) => String(v || "").replace("T", " ").slice(0, 16);

export default function Incidencias() {
  const [filas, setFilas] = useState([]);
  const [puestos, setPuestos] = useState([]);
  const [clientes, setClientes] = useState([]);
  const [guardiasPuesto, setGuardiasPuesto] = useState([]);
  const [supervisores, setSupervisores] = useState([]);
  const [comodines, setComodines] = useState([]);
  const [form, setForm] = useState(VACIO);
  const [filtro, setFiltro] = useState({ estado: "", nivel: "" });
  const [comodin, setComodin] = useState(null); // { incidencia, asignaciones, idEmpleadoReemplazo, idAsignacion }
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () =>
    api.get("/incidencias", { params: filtro }).then((r) => setFilas(r.data)).catch((e) => avisar("err", mensajeError(e)));
  useEffect(() => { cargar(); }, [filtro]);
  useEffect(() => {
    api.get("/incidencias/puestos").then((r) => setPuestos(r.data)).catch(() => {});
    api.get("/incidencias/clientes").then((r) => setClientes(r.data)).catch(() => {});
    api.get("/incidencias/supervisores").then((r) => setSupervisores(r.data)).catch(() => {});
    api.get("/incidencias/comodines").then((r) => setComodines(r.data)).catch(() => {});
    // si quien entra es supervisor, el supervisor asignado ya es él
    api.get("/supervisiones/contexto").then((r) => {
      if (r.data.esSupervisor && r.data.idSupervisor) setForm((f) => ({ ...f, idSupervisorAsignado: String(r.data.idSupervisor) }));
    }).catch(() => {});
  }, []);

  // al elegir el puesto se cargan sus guardias
  useEffect(() => {
    setGuardiasPuesto([]);
    if (form.nivel === "GUARDIA" && form.idPuesto) {
      api.get(`/incidencias/puestos/${form.idPuesto}/guardias`).then((r) => {
        setGuardiasPuesto(r.data);
        if (r.data.length === 1) setForm((f) => ({ ...f, idEmpleadoAfectado: String(r.data[0].id) }));
      }).catch(() => {});
    }
  }, [form.idPuesto, form.nivel]);

  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });
  const esGuardia = form.nivel === "GUARDIA";

  const registrar = async (e) => {
    e.preventDefault();
    try {
      await api.post("/incidencias", {
        nivel: form.nivel, tipo: form.tipo, descripcion: form.descripcion,
        idSupervisorAsignado: Number(form.idSupervisorAsignado),
        ...(esGuardia
          ? { idPuesto: Number(form.idPuesto), idEmpleadoAfectado: Number(form.idEmpleadoAfectado) }
          : { idCliente: Number(form.idCliente) }),
      });
      avisar("ok", "Incidencia registrada.");
      setForm({ ...VACIO, nivel: form.nivel, idSupervisorAsignado: form.idSupervisorAsignado });
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const avanzar = async (i, nuevo) => {
    try {
      await api.put(`/incidencias/${i.id}/estado`, { estado: nuevo });
      avisar("ok", `Incidencia ${nuevo}.`);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const abrirComodin = async (i) => {
    try {
      const r = await api.get(`/incidencias/${i.id}/asignaciones`);
      setComodin({ incidencia: i, asignaciones: r.data, idEmpleadoReemplazo: "", idAsignacion: r.data.length === 1 ? String(r.data[0].id) : "" });
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const solicitarComodin = async (e) => {
    e.preventDefault();
    try {
      const r = await api.post(`/incidencias/${comodin.incidencia.id}/comodin`, {
        idEmpleadoReemplazo: Number(comodin.idEmpleadoReemplazo),
        idAsignacion: comodin.idAsignacion ? Number(comodin.idAsignacion) : null,
      });
      avisar("ok", r.data.mensaje);
      setComodin(null);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  return (
    <>
      <section className="card">
        <h1>Incidencias</h1>
        <p className="muted">
          Hay dos niveles. <strong>Del guardia</strong>: le pertenece a un guardia y a su puesto (faltó, llegó tarde…).{" "}
          <strong>Del establecimiento</strong>: le pertenece al cliente, no a un guardia (se rompió un vidrio, se perdió o robaron un arma, hubo un asalto…).
          Las incidencias de una visita de supervisión se crean solas desde la pantalla de Visitas. Todos los campos son obligatorios.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">Registrar incidencia</h2>
        <form onSubmit={registrar} className="form-col">
          <div className="form-grid">
            <div>
              <label>Nivel *</label>
              <select className="input" value={form.nivel} onChange={(e) => setForm({ ...VACIO, nivel: e.target.value, idSupervisorAsignado: form.idSupervisorAsignado })} required>
                <option value="GUARDIA">Del guardia</option>
                <option value="ESTABLECIMIENTO">Del establecimiento / cliente</option>
              </select>
            </div>
            {esGuardia ? (
              <>
                <div>
                  <label>Puesto *</label>
                  <select className="input" value={form.idPuesto} onChange={(e) => setForm({ ...form, idPuesto: e.target.value, idEmpleadoAfectado: "" })} required>
                    <option value="">— Seleccione —</option>
                    {puestos.map((p) => <option key={p.id} value={p.id}>{p.cliente} — {p.descripcion}</option>)}
                  </select>
                </div>
                <div>
                  <label>Guardia *</label>
                  <select className="input" value={form.idEmpleadoAfectado} onChange={cambiar("idEmpleadoAfectado")} required disabled={!form.idPuesto}>
                    <option value="">— Seleccione —</option>
                    {guardiasPuesto.map((g) => <option key={g.id} value={g.id}>{g.nombre}</option>)}
                  </select>
                </div>
              </>
            ) : (
              <div>
                <label>Cliente *</label>
                <select className="input" value={form.idCliente} onChange={cambiar("idCliente")} required>
                  <option value="">— Seleccione —</option>
                  {clientes.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
                </select>
              </div>
            )}
            <div>
              <label>Tipo *</label>
              <select className="input" value={form.tipo} onChange={cambiar("tipo")} required>
                <option value="">— Seleccione —</option>
                {Object.entries(TIPOS_INCIDENCIA).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <div>
              <label>Supervisor asignado *</label>
              <select className="input" value={form.idSupervisorAsignado} onChange={cambiar("idSupervisorAsignado")} required>
                <option value="">— Seleccione —</option>
                {supervisores.map((s) => <option key={s.id} value={s.id}>{s.nombre}</option>)}
              </select>
            </div>
            <div style={{ gridColumn: "1 / -1" }}>
              <label>Descripción *</label>
              <textarea className="input" rows={4} value={form.descripcion} onChange={cambiar("descripcion")} maxLength={500} required
                placeholder="Describa lo ocurrido con el mayor detalle posible" />
            </div>
          </div>
          <div style={{ marginTop: 8 }}><button type="submit" className="btn btn-dark">REGISTRAR INCIDENCIA</button></div>
        </form>
      </section>

      {comodin && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2 className="section-title" style={{ marginTop: 0 }}>Solicitar guardia comodín — incidencia #{comodin.incidencia.id}</h2>
          <p className="muted">Se crea una asignación temporal de hoy para el guardia elegido, en el mismo puesto y turno.</p>
          <form onSubmit={solicitarComodin} className="form-grid">
            <div>
              <label>Asignación a reemplazar *</label>
              <select className="input" value={comodin.idAsignacion} onChange={(e) => setComodin({ ...comodin, idAsignacion: e.target.value })} required>
                <option value="">— Seleccione —</option>
                {comodin.asignaciones.map((a) => <option key={a.id} value={a.id}>{a.guardia} ({a.turno})</option>)}
              </select>
            </div>
            <div>
              <label>Guardia comodín *</label>
              <select className="input" value={comodin.idEmpleadoReemplazo} onChange={(e) => setComodin({ ...comodin, idEmpleadoReemplazo: e.target.value })} required>
                <option value="">— Seleccione —</option>
                {comodines.map((g) => <option key={g.id} value={g.id}>{g.nombre}</option>)}
              </select>
            </div>
            <div style={{ alignSelf: "end", display: "flex", gap: 8 }}>
              <button type="submit" className="btn btn-dark">ASIGNAR COMODÍN</button>
              <button type="button" className="btn btn-outline" onClick={() => setComodin(null)}>CANCELAR</button>
            </div>
          </form>
        </section>
      )}

      <section className="card" style={{ marginTop: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <h2 className="section-title" style={{ margin: 0 }}>Incidencias registradas</h2>
          <div style={{ display: "flex", gap: 8 }}>
            <select className="input" value={filtro.nivel} onChange={(e) => setFiltro({ ...filtro, nivel: e.target.value })}>
              <option value="">Todos los niveles</option><option value="GUARDIA">Del guardia</option><option value="ESTABLECIMIENTO">Del establecimiento</option>
            </select>
            <select className="input" value={filtro.estado} onChange={(e) => setFiltro({ ...filtro, estado: e.target.value })}>
              <option value="">Todos los estados</option><option value="ABIERTA">Abiertas</option><option value="EN_PROCESO">En proceso</option><option value="RESUELTA">Resueltas</option>
            </select>
          </div>
        </div>
        <div className="table-wrap" style={{ marginTop: 10 }}>
          <table>
            <thead><tr><th>#</th><th>Fecha</th><th>Nivel</th><th>Tipo</th><th>Cliente / Puesto</th><th>Guardia</th><th>Descripción</th><th>Reporta</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              {filas.map((i) => (
                <tr key={i.id}>
                  <td>{i.id}</td><td>{fecha(i.fechaHora)}</td>
                  <td><span className={"badge " + (i.nivel === "ESTABLECIMIENTO" ? "badge-sis" : "badge-off")}>{i.nivel === "ESTABLECIMIENTO" ? "Establecimiento" : "Guardia"}</span></td>
                  <td>{TIPOS_INCIDENCIA[i.tipo] || i.tipo}</td>
                  <td>{i.cliente}{i.puesto ? " / " + i.puesto : ""}</td><td>{i.guardia || "—"}</td><td>{i.descripcion}</td><td>{i.reporta}</td>
                  <td><span className={"badge " + (COLOR[i.estado] || "badge-off")}>{String(i.estado).replace("_", " ")}</span></td>
                  <td className="acciones">
                    {i.estado === "ABIERTA" && <button className="btn btn-outline btn-sm" onClick={() => avanzar(i, "EN_PROCESO")}>En proceso</button>}
                    {i.estado !== "RESUELTA" && <button className="btn btn-outline btn-sm" onClick={() => avanzar(i, "RESUELTA")}>Resolver</button>}
                    {i.estado !== "RESUELTA" && i.idPuesto && <button className="btn btn-outline btn-sm" onClick={() => abrirComodin(i)}>Comodín</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filas.length === 0 && <p className="muted">No hay incidencias con ese filtro.</p>}
        </div>
      </section>
    </>
  );
}
