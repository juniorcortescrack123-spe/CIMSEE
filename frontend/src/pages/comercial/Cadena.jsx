import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

export default function Cadena() {
  const [filas, setFilas] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get("/comercial/cadena").then((r) => setFilas(r.data)).catch((e) => setError(mensajeError(e)));
  }, []);

  return (
    <section className="card">
      <h1>Cadena relacional</h1>
      <p className="muted">
        Recorrido <strong>CLIENTE → CONTRATO → PUESTO → ASIGNACIÓN ← GUARDIA</strong>: demuestra que cada guardia
        asignado se puede rastrear hasta su puesto, su contrato y su cliente.
      </p>
      {error && <p className="alerta alerta-err">{error}</p>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>Cliente</th><th>Contrato</th><th>Vence</th><th>Puesto</th><th>Turno</th><th>Guardia</th></tr></thead>
          <tbody>
            {filas.map((f, i) => (
              <tr key={i}>
                <td><strong>{f.cliente}</strong></td>
                <td>{f.idContrato ? `#${f.idContrato} (${f.estadoContrato})` : <span className="muted">Sin contrato</span>}</td>
                <td>{f.vencimiento ? String(f.vencimiento) : "—"}</td>
                <td>{f.puesto || <span className="muted">Sin puestos</span>}</td>
                <td>{f.turno || "—"}</td>
                <td>{f.guardia || <span className="muted">Sin guardia asignado</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {filas.length === 0 && !error && <p className="muted">No hay datos para mostrar.</p>}
      </div>
    </section>
  );
}
