import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const TIPOS = { CAMBIO_TURNO: "Cambio de turno", CAMBIO_EQUIPO: "Cambio de equipo", DEVOLUCION_EQUIPO: "Devolución de equipo", VACACIONES: "Vacaciones" };
const COLOR = { PENDIENTE: "badge-sis", APROBADA: "badge-ok", RECHAZADA: "badge-rojo" };
const fecha = (v) => String(v || "").replace("T", " ").slice(0, 16);

export default function SolicitudesGuardias() {
  const [filas, setFilas] = useState([]);
  const [estado, setEstado] = useState("PENDIENTE");
  const [msg, setMsg] = useState({ tipo: "", texto: "" });

  const cargar = () =>
    api.get("/solicitudes-guardias", { params: estado ? { estado } : {} }).then((r) => setFilas(r.data)).catch((e) => setMsg({ tipo: "err", texto: mensajeError(e) }));
  useEffect(() => { cargar(); }, [estado]);

  const responder = async (s, aprobar) => {
    const texto = window.prompt(`${aprobar ? "APROBAR" : "RECHAZAR"} la solicitud de ${s.guardia} (${TIPOS[s.tipo]}).\nEscriba el comentario que verá el guardia:`);
    if (texto === null) return;
    if (!texto.trim()) return setMsg({ tipo: "err", texto: "El comentario para el guardia es obligatorio." });
    try {
      const r = await api.put(`/solicitudes-guardias/${s.id}/responder`, { aprobar, respuesta: texto.trim() });
      setMsg({ tipo: "ok", texto: r.data.mensaje });
    } catch (err) {
      setMsg({ tipo: "err", texto: mensajeError(err) });
    }
    cargar();
  };

  return (
    <section className="card">
      <h1>Solicitudes de guardias</h1>
      <p className="muted">
        Lo que sus guardias le piden: cambio de turno, cambio de equipo, devolución de equipo o vacaciones. Usted aprueba o rechaza dejando un comentario y el guardia
        recibe el aviso. Al aprobar, el administrador registra el cambio (turno, equipo o las fechas de vacaciones) en el sistema.
      </p>
      {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}
      <div className="filtros" style={{ gridTemplateColumns: "220px", marginBottom: 10 }}>
        <select className="input" value={estado} onChange={(e) => setEstado(e.target.value)}>
          <option value="PENDIENTE">Esperando respuesta</option><option value="APROBADA">Aprobadas</option><option value="RECHAZADA">Rechazadas</option><option value="">Todas</option>
        </select>
      </div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Fecha</th><th>Guardia</th><th>Cliente</th><th>Solicitud</th><th>Motivo</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>
            {filas.map((s) => (
              <tr key={s.id}>
                <td>{fecha(s.fecha)}</td><td><strong>{s.guardia}</strong></td><td>{s.cliente || "—"}</td>
                <td>
                  <strong>{TIPOS[s.tipo] || s.tipo}</strong>
                  <div className="muted">{s.turnoSolicitado ? `${s.turnoActual || "?"} → ${s.turnoSolicitado}` : s.fechaInicio ? `Del ${String(s.fechaInicio)} al ${String(s.fechaFin)}` : `${s.equipo || ""} ${s.serieOPlaca ? "(" + s.serieOPlaca + ")" : ""}`}</div>
                </td>
                <td>{s.motivo}</td>
                <td>
                  <span className={"badge " + (COLOR[s.estado] || "badge-off")}>{s.estado}</span>
                  {s.respuesta && <div className="muted">{s.respuesta}<br />{fecha(s.fechaRespuesta)}</div>}
                </td>
                <td className="acciones">
                  {s.estado === "PENDIENTE" ? (
                    <>
                      <button className="btn btn-dark btn-sm" onClick={() => responder(s, true)}>Aprobar</button>
                      <button className="btn btn-outline btn-sm" onClick={() => responder(s, false)}>Rechazar</button>
                    </>
                  ) : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filas.length === 0 && <p className="muted">No hay solicitudes con ese filtro.</p>}
      </div>
    </section>
  );
}
