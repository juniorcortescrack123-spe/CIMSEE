import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const fecha = (v) => String(v || "").replace("T", " ").slice(0, 16);

export default function Movimientos() {
  const [filas, setFilas] = useState([]);
  const [tipo, setTipo] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    api.get("/activos/movimientos", { params: { tipo } }).then((r) => setFilas(r.data)).catch((e) => setError(mensajeError(e)));
  }, [tipo]);

  return (
    <section className="card">
      <h1>Movimientos de inventario</h1>
      <p className="muted">Ingreso, entrega, devolución, traslado y ajuste de cada activo.</p>
      {error && <p className="alerta alerta-err">{error}</p>}
      <div className="filtros" style={{ gridTemplateColumns: "240px" }}>
        <select className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>
          <option value="">Todos los movimientos</option><option>ENTRADA</option><option>SALIDA</option><option>DEVOLUCION</option><option>TRANSFERENCIA</option><option>AJUSTE</option>
        </select>
      </div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Fecha</th><th>Movimiento</th><th>Activo</th><th>Serie / Placa</th><th>Origen</th><th>Destino</th><th>Motivo</th><th>Usuario</th></tr></thead>
          <tbody>
            {filas.map((m) => (
              <tr key={m.id}>
                <td>{fecha(m.fechaHora)}</td><td><span className="badge badge-sis">{m.tipo}</span></td><td>{m.activo}</td><td>{m.serieOPlaca || "—"}</td>
                <td>{m.origen || "—"}</td><td>{m.destino || "—"}</td><td>{m.motivo}</td><td>{m.usuario || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {filas.length === 0 && !error && <p className="muted">No hay movimientos registrados.</p>}
      </div>
    </section>
  );
}
