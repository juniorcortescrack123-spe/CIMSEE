import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const TIPOS = { CONTRATO: "Contratos", PERMISO: "Permisos legales", PAGO: "Pagos", MATRICULA: "Matrículas de vehículos" };

const dias = (d) => (d < 0 ? `venció hace ${-d} día(s)` : d === 0 ? "vence hoy" : `faltan ${d} día(s)`);

export default function Notificaciones() {
  const [filas, setFilas] = useState([]);
  const [estado, setEstado] = useState("PENDIENTE");
  const [tipo, setTipo] = useState("");
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const [avisos, setAvisos] = useState([]); // solicitudes, incidencias y respuestas dirigidas a esta persona
  useEffect(() => { api.get("/notificaciones/avisos").then((r) => setAvisos(r.data)).catch(() => {}); }, []);

  const cargar = () => {
    const params = { estado };
    if (tipo) params.tipo = tipo;
    api.get("/notificaciones", { params }).then((r) => setFilas(r.data)).catch((e) => setMsg({ tipo: "err", texto: mensajeError(e) }));
  };
  useEffect(() => { cargar(); }, [estado, tipo]);

  const cerrar = async (a, accion) => {
    const nota = window.prompt(accion === "atender"
      ? `Observación para marcar como ATENDIDA:\n${a.descripcion}\n(por ejemplo: "Ya se pagó" o "Se está tramitando la renovación")`
      : `Motivo para IGNORAR esta alerta:\n${a.descripcion}`);
    if (nota === null) return;
    if (!nota.trim()) return setMsg({ tipo: "err", texto: "La observación es obligatoria." });
    try {
      const r = await api.put(`/notificaciones/${a.id}/${accion}`, { observaciones: nota.trim() });
      setMsg({ tipo: "ok", texto: r.data.mensaje });
    } catch (err) {
      setMsg({ tipo: "err", texto: mensajeError(err) });
    }
    cargar();
  };

  const pendientes = filas.filter((f) => f.estado === "PENDIENTE");

  return (
    <section className="card">
      <h1>Centro de notificaciones</h1>
      <p className="muted">
        Alertas automáticas con 30 días de anticipación: contratos, permisos legales, pagos y matrículas de vehículos. Solo ve las de los módulos a los que
        tiene acceso. Si el contrato se renueva o el pago se registra, la alerta se cierra sola. También puede marcarla como atendida o ignorarla,
        dejando una observación.
      </p>
      {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

      {avisos.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <h2 className="section-title" style={{ marginTop: 0 }}>Avisos para usted</h2>
          {avisos.map((a, i) => (
            <div key={i} className="kpi" style={{ marginBottom: 6, display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8 }}>
              <span><strong>{a.titulo}</strong><div className="muted">{String(a.fecha || "").replace("T", " ").slice(0, 16)}</div></span>
              <Link to={a.ruta} className="btn btn-dark btn-sm">Ver</Link>
            </div>
          ))}
        </div>
      )}

      <div className="filtros" style={{ gridTemplateColumns: "200px 240px", marginBottom: 10 }}>
        <select className="input" value={estado} onChange={(e) => setEstado(e.target.value)}>
          <option value="PENDIENTE">Pendientes</option>
          <option value="ATENDIDA">Atendidas</option>
          <option value="IGNORADA">Ignoradas</option>
          <option value="TODAS">Todas</option>
        </select>
        <select className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>
          <option value="">Todos los tipos</option>
          {Object.entries(TIPOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </div>
      {estado === "PENDIENTE" && <p className="muted"><strong>{pendientes.length}</strong> alerta(s) pendiente(s).</p>}

      <div className="table-wrap">
        <table>
          <thead><tr><th>Prioridad</th><th>Tipo</th><th>Descripción</th><th>Vencimiento</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>
            {filas.map((a) => (
              <tr key={a.id}>
                <td><span className={"badge prio-" + a.prioridad}>{a.prioridad}</span></td>
                <td>{a.tipoNombre}</td>
                <td><strong>{a.descripcion}</strong></td>
                <td>{String(a.fechaVencimiento)}<div className="muted">{dias(Number(a.diasRestantes))}</div></td>
                <td>
                  {a.estado}
                  {a.estado !== "PENDIENTE" && <div className="muted">{a.atendio ? `por ${a.atendio} · ` : ""}{a.fechaAtencion ? String(a.fechaAtencion) : ""}<br />{a.observaciones}</div>}
                </td>
                <td className="acciones">
                  {a.estado === "PENDIENTE" ? (
                    <>
                      <Link to={a.ruta} className="btn btn-dark btn-sm">Ir a resolver</Link>
                      <button className="btn btn-outline btn-sm" onClick={() => cerrar(a, "atender")}>Atendida</button>
                      <button className="btn btn-outline btn-sm" onClick={() => cerrar(a, "ignorar")}>Ignorar</button>
                    </>
                  ) : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filas.length === 0 && <p className="muted">No hay notificaciones con ese filtro.</p>}
      </div>
    </section>
  );
}
