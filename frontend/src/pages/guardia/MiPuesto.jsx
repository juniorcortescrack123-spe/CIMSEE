import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { TIPOS_INCIDENCIA } from "../../formato.js";

export const TIPOS_SOLICITUD = { CAMBIO_TURNO: "Cambio de turno", CAMBIO_EQUIPO: "Cambio de equipo", DEVOLUCION_EQUIPO: "Devolución de equipo", VACACIONES: "Vacaciones" };
const COLOR = { PENDIENTE: "badge-sis", APROBADA: "badge-ok", RECHAZADA: "badge-rojo", ABIERTA: "badge-rojo", EN_PROCESO: "badge-sis", RESUELTA: "badge-ok" };
const fecha = (v) => String(v || "").replace("T", " ").slice(0, 16);
const VACIA = { tipo: "", idTurnoSolicitado: "", idActivo: "", fechaInicio: "", fechaFin: "", motivo: "" };
const hoy = () => new Date().toISOString().slice(0, 10);
const INC_VACIA = { nivel: "GUARDIA", tipo: "", descripcion: "", idPuesto: "" };

export default function MiPuesto() {
  const [info, setInfo] = useState(null);
  const [turnos, setTurnos] = useState([]);
  const [solicitudes, setSolicitudes] = useState([]);
  const [incidencias, setIncidencias] = useState([]);
  const [sol, setSol] = useState(VACIA);
  const [inc, setInc] = useState(INC_VACIA);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () => {
    api.get("/guardia/mi-informacion").then((r) => setInfo(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/guardia/solicitudes").then((r) => {
      setSolicitudes(r.data);
      // ya las vio: el aviso de "respuesta nueva" desaparece de la campana
      if (r.data.some((s) => s.nueva)) api.put("/guardia/solicitudes/vistas").catch(() => {});
    }).catch(() => {});
    api.get("/guardia/incidencias").then((r) => setIncidencias(r.data)).catch(() => {});
  };
  useEffect(() => { cargar(); api.get("/guardia/turnos").then((r) => setTurnos(r.data)).catch(() => {}); }, []);

  if (!info) return <section className="card"><p className="muted">Cargando...</p></section>;

  const principal = info.asignaciones[0];
  const turnosOtros = turnos.filter((t) => !principal || t.id !== principal.idTurno);
  const esTurno = sol.tipo === "CAMBIO_TURNO";

  const pedir = async (e) => {
    e.preventDefault();
    try {
      const r = await api.post("/guardia/solicitudes", {
        tipo: sol.tipo, motivo: sol.motivo,
        ...(esTurno ? { idTurnoSolicitado: Number(sol.idTurnoSolicitado) }
          : sol.tipo === "VACACIONES" ? { fechaInicio: sol.fechaInicio, fechaFin: sol.fechaFin }
          : { idActivo: Number(sol.idActivo) }),
      });
      avisar("ok", r.data.mensaje);
      setSol(VACIA);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const reportar = async (e) => {
    e.preventDefault();
    try {
      const r = await api.post("/guardia/incidencias", {
        nivel: inc.nivel, tipo: inc.tipo, descripcion: inc.descripcion, ...(inc.idPuesto ? { idPuesto: Number(inc.idPuesto) } : {}),
      });
      avisar("ok", r.data.mensaje);
      setInc(INC_VACIA);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  return (
    <>
      <section className="card">
        <h1>Mi puesto</h1>
        <p className="muted">Bienvenido, <strong>{info.nombre}</strong>. Aquí ve su supervisor, su lugar de trabajo y su equipo, y puede pedirle cambios a su supervisor o reportar una incidencia.</p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        {info.asignaciones.length === 0 ? (
          <p className="alerta alerta-err">Todavía no tiene un puesto asignado. Cuando el administrador se lo asigne, aquí verá su supervisor, lugar y turno.</p>
        ) : (
          <div className="dos-columnas">
            <div>
              <h3>Mi lugar asignado y turno</h3>
              {info.asignaciones.map((a) => (
                <div key={a.idAsignacion} className="kpi" style={{ marginBottom: 8 }}>
                  <small>{a.cliente}</small>
                  <strong style={{ fontSize: 16 }}>{a.puesto}</strong>
                  <div className="muted">{a.ubicacion}</div>
                  <div>Turno: <strong>{a.turno || "—"}</strong> {a.horario ? `(${a.horario})` : ""}</div>
                  <div className="muted">Desde {String(a.desde)}</div>
                </div>
              ))}
            </div>
            <div>
              <h3>Mi supervisor</h3>
              {(principal.supervisores.length === 0) ? <p className="muted">Su puesto todavía no tiene supervisor asignado.</p> :
                principal.supervisores.map((s) => (
                  <div key={s.id} className="kpi kpi-verde" style={{ marginBottom: 8 }}>
                    <small>Supervisor</small><strong style={{ fontSize: 16 }}>{s.nombre}</strong>
                    <div className="muted">Teléfono: {s.telefono || "—"}</div>
                  </div>
                ))}
            </div>
          </div>
        )}

        <h3>Mi equipo asignado</h3>
        {info.equipo.length === 0 ? <p className="muted">No tiene equipo asignado.</p> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Equipo</th><th>Serie / placa</th><th>Código</th><th>Entregado</th></tr></thead>
            <tbody>{info.equipo.map((e) => (
              <tr key={e.idAsignacion}><td><strong>{e.descripcion}</strong></td><td>{e.serieOPlaca || "—"}</td><td>{e.codigo}</td><td>{String(e.fechaEntrega)}</td></tr>
            ))}</tbody>
          </table></div>
        )}
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Pedir a mi supervisor</h2>
        <p className="muted">Usted solo pide. Su supervisor recibe el aviso y decide si lo aprueba o lo rechaza; la respuesta le aparece aquí y en la campana 🔔.</p>
        <form onSubmit={pedir} className="form-col">
          <div className="form-grid">
            <div>
              <label>¿Qué necesita? *</label>
              <select className="input" value={sol.tipo} onChange={(e) => setSol({ ...VACIA, tipo: e.target.value })} required>
                <option value="">— Seleccione —</option>
                {Object.entries(TIPOS_SOLICITUD).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            {sol.tipo === "CAMBIO_TURNO" && (
              <div>
                <label>Turno que solicita *</label>
                <select className="input" value={sol.idTurnoSolicitado} onChange={(e) => setSol({ ...sol, idTurnoSolicitado: e.target.value })} required>
                  <option value="">— Seleccione —</option>
                  {turnosOtros.map((t) => <option key={t.id} value={t.id}>{t.nombre} ({t.horario})</option>)}
                </select>
              </div>
            )}
            {sol.tipo === "VACACIONES" && (
              <>
                <div><label>Desde *</label><input className="input" type="date" min={hoy()} value={sol.fechaInicio} onChange={(e) => setSol({ ...sol, fechaInicio: e.target.value })} required /></div>
                <div><label>Hasta (último día de vacaciones) *</label><input className="input" type="date" min={sol.fechaInicio || hoy()} value={sol.fechaFin} onChange={(e) => setSol({ ...sol, fechaFin: e.target.value })} required /></div>
              </>
            )}
            {(sol.tipo === "CAMBIO_EQUIPO" || sol.tipo === "DEVOLUCION_EQUIPO") && (
              <div>
                <label>Equipo *</label>
                <select className="input" value={sol.idActivo} onChange={(e) => setSol({ ...sol, idActivo: e.target.value })} required>
                  <option value="">— Seleccione —</option>
                  {info.equipo.map((e) => <option key={e.idActivo} value={e.idActivo}>{e.descripcion} {e.serieOPlaca ? `(${e.serieOPlaca})` : ""}</option>)}
                </select>
                {info.equipo.length === 0 && <span className="muted">No tiene equipo asignado.</span>}
              </div>
            )}
            <div style={{ gridColumn: "1 / -1" }}>
              <label>Motivo *</label>
              <textarea className="input" rows={3} maxLength={300} value={sol.motivo} onChange={(e) => setSol({ ...sol, motivo: e.target.value })} required
                placeholder="Explique por qué lo necesita" />
            </div>
          </div>
          <div style={{ marginTop: 8 }}><button type="submit" className="btn btn-dark">ENVIAR SOLICITUD</button></div>
        </form>

        <h3>Mis solicitudes</h3>
        <div className="table-wrap"><table>
          <thead><tr><th>Fecha</th><th>Solicitud</th><th>Detalle</th><th>Supervisor</th><th>Estado</th><th>Respuesta</th></tr></thead>
          <tbody>{solicitudes.map((s) => (
            <tr key={s.id}>
              <td>{fecha(s.fecha)}</td><td><strong>{TIPOS_SOLICITUD[s.tipo] || s.tipo}</strong>{s.nueva && <> <span className="badge badge-rojo">nueva respuesta</span></>}</td>
              <td>{s.turnoSolicitado ? "Turno: " + s.turnoSolicitado : s.fechaInicio ? `Del ${String(s.fechaInicio)} al ${String(s.fechaFin)}` : s.equipo || "—"}<div className="muted">{s.motivo}</div></td>
              <td>{s.supervisor}</td>
              <td><span className={"badge " + (COLOR[s.estado] || "badge-off")}>{s.estado}</span></td>
              <td>{s.respuesta || "—"}{s.fechaRespuesta && <div className="muted">{fecha(s.fechaRespuesta)}</div>}</td>
            </tr>
          ))}</tbody>
        </table>
        {solicitudes.length === 0 && <p className="muted">Aún no ha hecho solicitudes.</p>}</div>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Reportar una incidencia</h2>
        <p className="muted">Su supervisor recibe el aviso. Solo él puede ponerla en proceso o resolverla; usted puede ver aquí cómo va.</p>
        <form onSubmit={reportar} className="form-col">
          <div className="form-grid">
            <div>
              <label>¿De quién es? *</label>
              <select className="input" value={inc.nivel} onChange={(e) => setInc({ ...inc, nivel: e.target.value })} required>
                <option value="GUARDIA">Mía (me pasó a mí)</option>
                <option value="ESTABLECIMIENTO">Del lugar (vidrio roto, robo, asalto…)</option>
              </select>
            </div>
            <div>
              <label>Tipo *</label>
              <select className="input" value={inc.tipo} onChange={(e) => setInc({ ...inc, tipo: e.target.value })} required>
                <option value="">— Seleccione —</option>
                {Object.entries(TIPOS_INCIDENCIA).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            {info.asignaciones.length > 1 && (
              <div>
                <label>Puesto *</label>
                <select className="input" value={inc.idPuesto} onChange={(e) => setInc({ ...inc, idPuesto: e.target.value })} required>
                  <option value="">— Seleccione —</option>
                  {info.asignaciones.map((a) => <option key={a.idAsignacion} value={a.idPuesto}>{a.cliente} — {a.puesto}</option>)}
                </select>
              </div>
            )}
            <div style={{ gridColumn: "1 / -1" }}>
              <label>Descripción *</label>
              <textarea className="input" rows={4} maxLength={500} value={inc.descripcion} onChange={(e) => setInc({ ...inc, descripcion: e.target.value })} required
                placeholder="Cuente qué pasó con el mayor detalle posible" />
            </div>
          </div>
          <div style={{ marginTop: 8 }}><button type="submit" className="btn btn-dark" disabled={info.asignaciones.length === 0}>REPORTAR INCIDENCIA</button></div>
        </form>

        <h3>Mis incidencias</h3>
        <div className="table-wrap"><table>
          <thead><tr><th>Fecha</th><th>Tipo</th><th>Descripción</th><th>Supervisor</th><th>Estado</th></tr></thead>
          <tbody>{incidencias.map((i) => (
            <tr key={i.id}>
              <td>{fecha(i.fecha)}</td><td>{TIPOS_INCIDENCIA[i.tipo] || i.tipo}<div className="muted">{i.nivel === "ESTABLECIMIENTO" ? "Del lugar" : "Mía"}</div></td>
              <td>{i.descripcion}</td><td>{i.supervisor || "—"}</td>
              <td><span className={"badge " + (COLOR[i.estado] || "badge-off")}>{String(i.estado).replace("_", " ")}</span></td>
            </tr>
          ))}</tbody>
        </table>
        {incidencias.length === 0 && <p className="muted">No ha reportado incidencias.</p>}</div>
      </section>
    </>
  );
}
