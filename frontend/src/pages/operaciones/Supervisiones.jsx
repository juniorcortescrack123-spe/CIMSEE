import { useEffect, useRef, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { TIPOS_INCIDENCIA } from "../../formato.js";

const MOTIVOS = {
  PERMISO: "Permiso", INCAPACIDAD_MEDICA: "Incapacidad médica", EMERGENCIA_FAMILIAR: "Emergencia familiar",
  ENFERMEDAD: "Enfermedad", SIN_AVISO: "Sin aviso", ABANDONO_PUESTO: "Abandono del puesto", OTRO: "Otro",
};
const FILA_VACIA = { presente: null, tipoFalta: "", motivoFalta: "", idEmpleadoReemplazo: "", firmaLibro: true, observaciones: "", tipoIncidencia: "", incidencia: "" };
const fecha = (v) => String(v || "").replace("T", " ").slice(0, 16);

export default function Supervisiones() {
  const [contexto, setContexto] = useState(null);       // quién entra: supervisor o administrador/gerente
  const [idSupervisor, setIdSupervisor] = useState("");  // solo cuando no es supervisor
  const [clientes, setClientes] = useState([]);
  const [idCliente, setIdCliente] = useState("");
  const [guardias, setGuardias] = useState([]);          // lista para pasar asistencia
  const [filas, setFilas] = useState({});                // marcas por idAsignacionPuesto
  const [comodines, setComodines] = useState([]);
  const [generales, setGenerales] = useState([]);        // incidencias de la visita (del establecimiento)
  const [observaciones, setObservaciones] = useState("");
  const [visitas, setVisitas] = useState([]);
  const [rango, setRango] = useState({ desde: "", hasta: "" });
  const [detalle, setDetalle] = useState(null);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });
  const panel = useRef(null);

  const supervisorActual = contexto?.esSupervisor ? contexto.idSupervisor : idSupervisor;

  const cargarVisitas = () =>
    api.get("/supervisiones/visitas", { params: rango }).then((r) => setVisitas(r.data)).catch((e) => avisar("err", mensajeError(e)));

  useEffect(() => {
    api.get("/supervisiones/contexto").then((r) => setContexto(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/supervisiones/comodines").then((r) => setComodines(r.data)).catch(() => {});
  }, []);
  useEffect(() => { cargarVisitas(); }, [rango]);

  // al saber quién es el supervisor se cargan sus clientes
  useEffect(() => {
    setClientes([]); setIdCliente(""); setGuardias([]);
    if (!supervisorActual) return;
    api.get("/supervisiones/clientes", { params: contexto?.esSupervisor ? {} : { idSupervisor: supervisorActual } })
      .then((r) => setClientes(r.data)).catch((e) => avisar("err", mensajeError(e)));
  }, [supervisorActual]);

  // al elegir el cliente aparece la lista de sus guardias
  useEffect(() => {
    setGuardias([]); setFilas({}); setGenerales([]); setObservaciones("");
    if (!idCliente) return;
    api.get(`/supervisiones/clientes/${idCliente}/guardias`, { params: contexto?.esSupervisor ? {} : { idSupervisor: supervisorActual } })
      .then((r) => {
        setGuardias(r.data);
        const f = {};
        r.data.forEach((g) => { f[g.idAsignacionPuesto] = { ...FILA_VACIA }; });
        setFilas(f);
      }).catch((e) => avisar("err", mensajeError(e)));
  }, [idCliente]);

  const poner = (id, campo, valor) => setFilas((f) => ({ ...f, [id]: { ...f[id], [campo]: valor } }));
  const marcar = (id, presente) =>
    setFilas((f) => ({ ...f, [id]: { ...f[id], presente, ...(presente ? { tipoFalta: "", motivoFalta: "", idEmpleadoReemplazo: "" } : {}) } }));
  const todosPresentes = () => setFilas((f) => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, { ...v, presente: true, tipoFalta: "", motivoFalta: "", idEmpleadoReemplazo: "" }])));

  const marcados = Object.values(filas).filter((f) => f.presente !== null).length;
  const comodinesEnUso = (idAsig) => new Set(Object.entries(filas).filter(([k, f]) => k !== String(idAsig) && f.idEmpleadoReemplazo).map(([, f]) => String(f.idEmpleadoReemplazo)));

  const finalizar = async (e) => {
    e.preventDefault();
    for (const g of guardias) {
      const f = filas[g.idAsignacionPuesto];
      if (f.presente === null) return avisar("err", `Marque presente o ausente para ${g.guardia}.`);
      if (f.presente === false && (!f.tipoFalta || !f.motivoFalta)) return avisar("err", `Indique si la falta de ${g.guardia} es con o sin excusa y el motivo.`);
    }
    try {
      const r = await api.post("/supervisiones/visitas", {
        idSupervisor: contexto?.esSupervisor ? null : Number(supervisorActual),
        idCliente: Number(idCliente),
        observaciones,
        guardias: guardias.map((g) => {
          const f = filas[g.idAsignacionPuesto];
          return {
            idAsignacionPuesto: g.idAsignacionPuesto, presente: f.presente, tipoFalta: f.tipoFalta, motivoFalta: f.motivoFalta,
            idEmpleadoReemplazo: f.idEmpleadoReemplazo ? Number(f.idEmpleadoReemplazo) : null,
            firmaLibro: f.firmaLibro, observaciones: f.observaciones, incidencia: f.incidencia, tipoIncidencia: f.incidencia ? (f.tipoIncidencia || "OTRO") : "",
          };
        }),
        incidencias: generales,
      });
      avisar("ok", r.data.mensaje);
      // vuelve a cargar la lista del cliente, limpia, por si se hace otra visita
      const idAnterior = idCliente;
      setIdCliente("");
      setTimeout(() => setIdCliente(idAnterior), 0);
      cargarVisitas();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const verDetalle = async (id) => {
    try {
      setDetalle((await api.get(`/supervisiones/visitas/${id}`)).data);
      setTimeout(() => panel.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  if (!contexto) return <section className="card"><p className="muted">Cargando...</p></section>;

  return (
    <>
      <section className="card">
        <h1>Visitas de supervisión</h1>
        <p className="muted">
          Escoja el cliente que va a visitar y pase asistencia a sus guardias: marque presente o ausente (con o sin excusa y el motivo), escriba una
          observación o incidencia por guardia y, al final, las incidencias generales del lugar (por ejemplo, un vidrio roto o un arma perdida).
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        {contexto.esSupervisor ? (
          <p>Supervisor: <span className="chip"><strong>{contexto.nombre}</strong></span></p>
        ) : (
          <div style={{ maxWidth: 360, marginBottom: 12 }}>
            <label>Supervisor que realiza la visita * <span className="muted">(usted no tiene el rol de supervisor)</span></label>
            <select className="input" value={idSupervisor} onChange={(e) => setIdSupervisor(e.target.value)}>
              <option value="">— Seleccione —</option>
              {contexto.supervisores.map((s) => <option key={s.id} value={s.id}>{s.nombre}</option>)}
            </select>
          </div>
        )}

        {supervisorActual && (
          <div style={{ maxWidth: 360 }}>
            <label>Cliente a supervisar *</label>
            <select className="input" value={idCliente} onChange={(e) => setIdCliente(e.target.value)}>
              <option value="">— Seleccione —</option>
              {clientes.map((c) => <option key={c.id} value={c.id}>{c.nombre} ({c.guardias} guardia(s))</option>)}
            </select>
            {clientes.length === 0 && (
              <p className="alerta alerta-err" style={{ marginTop: 8 }}>
                No tiene clientes a su cargo. El administrador debe asignárselos en Gestión de clientes &gt; Detalle &gt; Supervisores a cargo.
              </p>
            )}
          </div>
        )}

        {idCliente && guardias.length === 0 && <p className="muted" style={{ marginTop: 12 }}>Este cliente no tiene guardias asignados hoy.</p>}

        {guardias.length > 0 && (
          <form onSubmit={finalizar} className="form-col" style={{ marginTop: 16 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2 className="section-title" style={{ margin: 0 }}>Asistencia de los guardias · {marcados} de {guardias.length} marcados</h2>
              <button type="button" className="btn btn-outline btn-sm" onClick={todosPresentes}>MARCAR TODOS PRESENTES</button>
            </div>
            <div className="table-wrap" style={{ marginTop: 8 }}>
              <table>
                <thead><tr><th>Guardia</th><th>Puesto / turno</th><th>Asistencia *</th><th>Detalle</th><th>Observación</th><th>Incidencia del guardia</th></tr></thead>
                <tbody>
                  {guardias.map((g) => {
                    const id = g.idAsignacionPuesto;
                    const f = filas[id] || FILA_VACIA;
                    const usados = comodinesEnUso(id);
                    return (
                      <tr key={id}>
                        <td><strong>{g.guardia}</strong></td>
                        <td>{g.puesto}<div className="muted">{g.turno}</div></td>
                        <td style={{ whiteSpace: "nowrap" }}>
                          <button type="button" className={"btn btn-sm " + (f.presente === true ? "btn-dark" : "btn-outline")} onClick={() => marcar(id, true)}>✔ Presente</button>{" "}
                          <button type="button" className={"btn btn-sm " + (f.presente === false ? "btn-dark" : "btn-outline")} onClick={() => marcar(id, false)}>✘ Ausente</button>
                        </td>
                        <td style={{ minWidth: 190 }}>
                          {f.presente === true && (
                            <label style={{ display: "flex", gap: 6, alignItems: "center", fontWeight: 400 }}>
                              <input type="checkbox" checked={f.firmaLibro} onChange={(e) => poner(id, "firmaLibro", e.target.checked)} /> Firmó el libro
                            </label>
                          )}
                          {f.presente === false && (
                            <div style={{ display: "grid", gap: 4 }}>
                              <select className="input" value={f.tipoFalta} onChange={(e) => poner(id, "tipoFalta", e.target.value)} required>
                                <option value="">Falta: ¿con o sin excusa? *</option>
                                <option value="CON_EXCUSA">Con excusa</option>
                                <option value="SIN_EXCUSA">Sin excusa</option>
                              </select>
                              <select className="input" value={f.motivoFalta} onChange={(e) => poner(id, "motivoFalta", e.target.value)} required>
                                <option value="">Motivo de la falta *</option>
                                {Object.entries(MOTIVOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                              </select>
                              <select className="input" value={f.idEmpleadoReemplazo} onChange={(e) => poner(id, "idEmpleadoReemplazo", e.target.value)}>
                                <option value="">Sin guardia comodín</option>
                                {comodines.filter((c) => c.id !== g.idGuardia && !usados.has(String(c.id))).map((c) => <option key={c.id} value={c.id}>Comodín: {c.nombre}</option>)}
                              </select>
                            </div>
                          )}
                          {f.presente === null && <span className="muted">Marque la asistencia</span>}
                        </td>
                        <td style={{ minWidth: 170 }}>
                          <textarea className="input" rows={2} maxLength={200} value={f.observaciones} onChange={(e) => poner(id, "observaciones", e.target.value)} placeholder="Opcional" />
                        </td>
                        <td style={{ minWidth: 200 }}>
                          <select className="input" value={f.tipoIncidencia} onChange={(e) => poner(id, "tipoIncidencia", e.target.value)} style={{ marginBottom: 4 }}>
                            <option value="">Tipo (si hubo incidencia)</option>
                            {Object.entries(TIPOS_INCIDENCIA).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                          </select>
                          <textarea className="input" rows={2} maxLength={500} value={f.incidencia} onChange={(e) => poner(id, "incidencia", e.target.value)} placeholder="Opcional" />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <h3 style={{ marginBottom: 4 }}>Incidencias de la visita (del establecimiento)</h3>
            <p className="muted" style={{ marginTop: 0 }}>Lo que pasó en el lugar y no es culpa de un guardia: vidrio roto, arma perdida o robada, asalto…</p>
            {generales.map((g, i) => (
              <div key={i} style={{ display: "grid", gridTemplateColumns: "240px 1fr 40px", gap: 8, marginBottom: 6 }}>
                <select className="input" value={g.tipo} required onChange={(e) => setGenerales(generales.map((x, k) => (k === i ? { ...x, tipo: e.target.value } : x)))}>
                  <option value="">Tipo *</option>
                  {Object.entries(TIPOS_INCIDENCIA).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <textarea className="input" rows={2} maxLength={500} required value={g.descripcion} placeholder="Descripción *"
                  onChange={(e) => setGenerales(generales.map((x, k) => (k === i ? { ...x, descripcion: e.target.value } : x)))} />
                <button type="button" className="btn btn-outline btn-sm" title="Quitar" onClick={() => setGenerales(generales.filter((_, k) => k !== i))}>✕</button>
              </div>
            ))}
            <button type="button" className="btn btn-outline btn-sm" onClick={() => setGenerales([...generales, { tipo: "", descripcion: "" }])}>+ AGREGAR INCIDENCIA DE LA VISITA</button>

            <div style={{ marginTop: 14 }}>
              <label>Observaciones generales de la visita *</label>
              <textarea className="input" rows={4} maxLength={500} required value={observaciones} onChange={(e) => setObservaciones(e.target.value)}
                placeholder="Si no hubo novedades, escriba: Sin novedades" />
            </div>
            <div style={{ marginTop: 10 }}><button type="submit" className="btn btn-dark">FINALIZAR VISITA</button></div>
          </form>
        )}
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
          <h2 className="section-title" style={{ margin: 0 }}>{contexto.esSupervisor ? "Mis visitas" : "Visitas registradas"}</h2>
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <label style={{ fontWeight: 400 }}>Desde</label><input className="input" type="date" value={rango.desde} onChange={(e) => setRango({ ...rango, desde: e.target.value })} />
            <label style={{ fontWeight: 400 }}>Hasta</label><input className="input" type="date" value={rango.hasta} onChange={(e) => setRango({ ...rango, hasta: e.target.value })} />
          </div>
        </div>
        <div className="table-wrap" style={{ marginTop: 10 }}>
          <table>
            <thead><tr><th>#</th><th>Fecha</th><th>Supervisor</th><th>Cliente</th><th>Presentes</th><th>Ausentes</th><th>Incidencias</th><th>Acciones</th></tr></thead>
            <tbody>
              {visitas.map((v) => (
                <tr key={v.id}>
                  <td>{v.id}</td><td>{fecha(v.fechaHora)}</td><td>{v.supervisor}</td><td><strong>{v.cliente}</strong></td>
                  <td>{v.presentes}</td><td>{Number(v.ausentes) > 0 ? <span className="badge badge-rojo">{v.ausentes}</span> : 0}</td><td>{v.incidencias}</td>
                  <td className="acciones"><button className="btn btn-outline btn-sm" onClick={() => verDetalle(v.id)}>Detalle</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          {visitas.length === 0 && <p className="muted">Aún no hay visitas registradas.</p>}
        </div>
      </section>

      {detalle && (
        <section className="card" style={{ marginTop: 16 }} ref={panel}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <h2 className="section-title" style={{ marginTop: 0 }}>Detalle de la visita #{detalle.id} · {detalle.cliente}</h2>
            <button className="btn btn-outline btn-sm" onClick={() => setDetalle(null)}>Cerrar</button>
          </div>
          <p className="muted">{fecha(detalle.fechaHora)} · Supervisor: {detalle.supervisor}</p>
          <p><strong>Observaciones generales:</strong> {detalle.observaciones}</p>
          <div className="table-wrap"><table>
            <thead><tr><th>Guardia</th><th>Puesto / turno</th><th>Asistencia</th><th>Falta</th><th>Observación</th><th>Incidencia</th></tr></thead>
            <tbody>{detalle.guardias.map((g, i) => (
              <tr key={i}>
                <td><strong>{g.guardia}</strong></td><td>{g.puesto}<div className="muted">{g.turno}</div></td>
                <td><span className={"badge " + (g.presente ? "badge-ok" : "badge-rojo")}>{g.presente ? "Presente" : "Ausente"}</span>{g.presente && !g.firmaLibro ? <div className="muted">No firmó el libro</div> : null}</td>
                <td>{g.presente ? "—" : <>{g.tipoFalta === "CON_EXCUSA" ? "Con excusa" : "Sin excusa"}<div className="muted">{MOTIVOS[g.motivoFalta] || g.motivoFalta}{g.comodin ? " · comodín: " + g.comodin : ""}</div></>}</td>
                <td>{g.observaciones || "—"}</td><td>{g.incidencia || "—"}</td>
              </tr>
            ))}</tbody>
          </table></div>
          {detalle.listaIncidencias.length > 0 && (
            <>
              <h3>Incidencias de la visita</h3>
              <div className="table-wrap"><table>
                <thead><tr><th>#</th><th>Nivel</th><th>Tipo</th><th>Guardia</th><th>Descripción</th><th>Estado</th></tr></thead>
                <tbody>{detalle.listaIncidencias.map((i) => (
                  <tr key={i.id}><td>{i.id}</td><td>{i.nivel === "ESTABLECIMIENTO" ? "Establecimiento" : "Guardia"}</td><td>{TIPOS_INCIDENCIA[i.tipo] || i.tipo}</td>
                    <td>{i.guardia || "—"}</td><td>{i.descripcion}</td><td>{String(i.estado).replace("_", " ")}</td></tr>
                ))}</tbody>
              </table></div>
            </>
          )}
        </section>
      )}
    </>
  );
}
