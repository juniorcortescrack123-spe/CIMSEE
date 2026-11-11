import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

export default function Turnos() {
  const [turnos, setTurnos] = useState([]);
  const [edicion, setEdicion] = useState({});
  const [msg, setMsg] = useState({ tipo: "", texto: "" });

  const cargar = () =>
    api.get("/turnos").then((r) => setTurnos(r.data)).catch((e) => setMsg({ tipo: "err", texto: mensajeError(e) }));
  useEffect(() => { cargar(); }, []);

  const empezar = (t) => setEdicion({ id: t.id, horaInicio: t.horaInicio, horaFin: t.horaFin });

  const guardar = async () => {
    try {
      await api.put(`/turnos/${edicion.id}`, { horaInicio: edicion.horaInicio, horaFin: edicion.horaFin });
      setMsg({ tipo: "ok", texto: "Horario del turno actualizado." });
      setEdicion({});
      cargar();
    } catch (err) {
      setMsg({ tipo: "err", texto: mensajeError(err) });
    }
  };

  return (
    <section className="card">
      <h1>Gestión de turnos</h1>
      <p className="muted">Turnos de trabajo de los guardias y cuántos guardias tienen asignados hoy.</p>
      {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>Turno</th><th>Hora de inicio</th><th>Hora de fin</th><th>Guardias asignados</th><th></th></tr></thead>
          <tbody>
            {turnos.map((t) => (
              <tr key={t.id}>
                <td><strong>{t.nombre}</strong></td>
                {edicion.id === t.id ? (
                  <>
                    <td><input className="input input-sm" value={edicion.horaInicio} onChange={(e) => setEdicion({ ...edicion, horaInicio: e.target.value })} placeholder="06:00" /></td>
                    <td><input className="input input-sm" value={edicion.horaFin} onChange={(e) => setEdicion({ ...edicion, horaFin: e.target.value })} placeholder="14:00" /></td>
                    <td>{t.guardias}</td>
                    <td className="acciones">
                      <button className="btn btn-dark btn-sm" onClick={guardar}>Guardar</button>
                      <button className="btn btn-outline btn-sm" onClick={() => setEdicion({})}>Cancelar</button>
                    </td>
                  </>
                ) : (
                  <>
                    <td>{t.horaInicio}</td><td>{t.horaFin}</td><td>{t.guardias}</td>
                    <td><button className="btn btn-outline btn-sm" onClick={() => empezar(t)}>Cambiar horario</button></td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
