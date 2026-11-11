import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const hoy = () => new Date().toISOString().slice(0, 10);
const fila = () => ({ idEmpleado: "", idPuesto: "", idTurno: "", fechaInicio: hoy(), fechaFin: "", ubicacion: "" });
const PUESTO_VACIO = { descripcion: "", ubicacion: "", tipoTurno: "12h" };

// Asignar varios guardias a un cliente en una sola tabla (en vez de entrar guardia por guardia)
export default function AsignarGuardias() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [cliente, setCliente] = useState(null);
  const [guardias, setGuardias] = useState([]);
  const [turnos, setTurnos] = useState([]);
  const [filas, setFilas] = useState([fila()]);
  const [puesto, setPuesto] = useState(PUESTO_VACIO);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargarCliente = () =>
    api.get(`/clientes/${id}/detalle`).then((r) => setCliente(r.data)).catch((e) => avisar("err", mensajeError(e)));
  useEffect(() => {
    cargarCliente();
    api.get("/asignaciones/guardias").then((r) => setGuardias(r.data)).catch(() => {});
    api.get("/turnos").then((r) => setTurnos(r.data)).catch(() => {});
  }, [id]);

  const puestos = (cliente?.puestos || []).filter((p) => p.estado === "ACTIVO");

  const cambiarFila = (i, campo, valor) => setFilas(filas.map((f, k) => (k === i ? { ...f, [campo]: valor } : f)));
  const quitarFila = (i) => setFilas(filas.length === 1 ? [fila()] : filas.filter((_, k) => k !== i));

  const crearPuesto = async (e) => {
    e.preventDefault();
    try {
      await api.post("/puestos", { idCliente: Number(id), descripcion: puesto.descripcion, ubicacion: puesto.ubicacion, tipoTurno: puesto.tipoTurno });
      setPuesto(PUESTO_VACIO);
      avisar("ok", "Puesto creado. Ya puede asignarle guardias.");
      cargarCliente();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const guardar = async (e) => {
    e.preventDefault();
    try {
      const r = await api.post("/asignaciones/lote", {
        idCliente: Number(id),
        asignaciones: filas.map((f) => ({
          idEmpleado: Number(f.idEmpleado), idPuesto: Number(f.idPuesto), idTurno: Number(f.idTurno),
          fechaInicio: f.fechaInicio, fechaFin: f.fechaFin || null, ubicacion: f.ubicacion,
        })),
      });
      avisar("ok", r.data.mensaje);
      setFilas([fila()]);
      cargarCliente();
      api.get("/asignaciones/guardias").then((g) => setGuardias(g.data)).catch(() => {});
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  return (
    <>
      <section className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
          <h1 style={{ margin: 0 }}>Asignar guardias{cliente ? ` — ${cliente.nombre}` : ""}</h1>
          <button className="btn btn-outline" onClick={() => navigate("/comercial/clientes")}>VOLVER A CLIENTES</button>
        </div>
        <p className="muted">
          Agregue una fila por guardia: elija el empleado (ya contratado), el puesto, el turno y la ubicación exacta donde debe estar.
          Todos los campos son obligatorios (menos "Hasta", si el guardia no tiene fecha de salida). Todas las filas se guardan juntas; si una falla, no se guarda ninguna.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        {puestos.length === 0 && (
          <p className="alerta alerta-info">Este cliente aún no tiene puestos. Cree uno aquí abajo (por ejemplo "General" si el cliente reparte internamente a los guardias).</p>
        )}

        <details open={puestos.length === 0} style={{ marginBottom: 12 }}>
          <summary style={{ cursor: "pointer", fontWeight: 600 }}>+ Crear un puesto para este cliente</summary>
          <form onSubmit={crearPuesto} className="form-grid" style={{ marginTop: 10 }}>
            <div><label>Descripción del puesto *</label><input className="input" value={puesto.descripcion} onChange={(e) => setPuesto({ ...puesto, descripcion: e.target.value })} required placeholder="Ej. General, Entrada principal" /></div>
            <div><label>Ubicación (ciudad, edificio) *</label><input className="input" value={puesto.ubicacion} onChange={(e) => setPuesto({ ...puesto, ubicacion: e.target.value })} required maxLength={200} /></div>
            <div>
              <label>Horario del puesto *</label>
              <select className="input" value={puesto.tipoTurno} onChange={(e) => setPuesto({ ...puesto, tipoTurno: e.target.value })}>
                <option value="12h">12 horas</option><option value="24h">24 horas</option>
              </select>
            </div>
            <div style={{ alignSelf: "end" }}><button type="submit" className="btn btn-outline btn-sm">CREAR PUESTO</button></div>
          </form>
        </details>

        <form onSubmit={guardar}>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Guardia *</th><th>Puesto *</th><th>Turno *</th><th>Desde *</th><th>Hasta (opcional)</th><th>Ubicación exacta *</th><th></th></tr></thead>
              <tbody>
                {filas.map((f, i) => (
                  <tr key={i}>
                    <td>
                      <select className="input input-sm" value={f.idEmpleado} onChange={(e) => cambiarFila(i, "idEmpleado", e.target.value)} required>
                        <option value="">— Guardia —</option>
                        {guardias.map((g) => <option key={g.id} value={g.id}>{g.nombre}</option>)}
                      </select>
                    </td>
                    <td>
                      <select className="input input-sm" value={f.idPuesto} onChange={(e) => cambiarFila(i, "idPuesto", e.target.value)} required>
                        <option value="">— Puesto —</option>
                        {puestos.map((p) => <option key={p.id} value={p.id}>{p.descripcion} ({p.tipoTurno})</option>)}
                      </select>
                    </td>
                    <td>
                      <select className="input input-sm" value={f.idTurno} onChange={(e) => cambiarFila(i, "idTurno", e.target.value)} required>
                        <option value="">— Turno —</option>
                        {turnos.map((t) => <option key={t.id} value={t.id}>{t.nombre} ({t.horaInicio}-{t.horaFin})</option>)}
                      </select>
                    </td>
                    <td><input className="input input-sm" type="date" value={f.fechaInicio} onChange={(e) => cambiarFila(i, "fechaInicio", e.target.value)} required /></td>
                    <td><input className="input input-sm" type="date" value={f.fechaFin} onChange={(e) => cambiarFila(i, "fechaFin", e.target.value)} /></td>
                    <td><input className="input input-sm" value={f.ubicacion} onChange={(e) => cambiarFila(i, "ubicacion", e.target.value)} placeholder="Edificio, puerta, área o 'General'" maxLength={120} required /></td>
                    <td><button type="button" className="btn btn-outline btn-sm" onClick={() => quitarFila(i)}>Quitar</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <button type="button" className="btn btn-outline" onClick={() => setFilas([...filas, fila()])}>+ AGREGAR FILA</button>
            <button type="submit" className="btn btn-dark" disabled={puestos.length === 0}>GUARDAR {filas.length} ASIGNACIÓN(ES)</button>
          </div>
        </form>
      </section>

      {cliente && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2 className="section-title" style={{ marginTop: 0 }}>Guardias asignados actualmente</h2>
          {cliente.puestos.every((p) => p.guardias.length === 0) ? <p className="muted">Este cliente aún no tiene guardias asignados.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Puesto</th><th>Horario</th><th>Guardias</th></tr></thead>
              <tbody>{cliente.puestos.map((p) => (
                <tr key={p.id}>
                  <td><strong>{p.descripcion}</strong><br /><span className="muted">{p.ubicacion}</span></td>
                  <td>{p.tipoTurno}</td>
                  <td>{p.guardias.length === 0 ? <span className="muted">Sin guardia</span> : p.guardias.map((g, i) => <span key={i} className="chip">{g.nombre} ({g.turno})</span>)}</td>
                </tr>
              ))}</tbody>
            </table></div>
          )}
        </section>
      )}
    </>
  );
}
