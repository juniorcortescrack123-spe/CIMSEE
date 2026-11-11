import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const ACCIONES = ["LOGIN", "LOGIN_FALLIDO", "INSERT", "UPDATE", "DELETE", "CAMBIO_ROL", "CAMBIO_CLAVE", "RESTABLECER_CLAVE", "SOLICITUD_CLAVE", "BLOQUEO_CUENTA", "DESBLOQUEO"];

export default function Auditoria() {
  const [filas, setFilas] = useState([]);
  const [usuario, setUsuario] = useState("");
  const [accion, setAccion] = useState("");
  const [error, setError] = useState("");

  const cargar = () => {
    setError("");
    api
      .get("/seguridad/auditoria", { params: { usuario, accion, limite: 200 } })
      .then((r) => setFilas(r.data))
      .catch((e) => setError(mensajeError(e)));
  };
  useEffect(cargar, []);

  return (
    <section className="card">
      <h1>Auditoría</h1>
      <p className="muted">Registro de quién hizo qué y cuándo (inicios de sesión, usuarios, roles y contraseñas).</p>
      {error && <p className="alerta alerta-err">{error}</p>}

      <div className="filtros">
        <input className="input" placeholder="Filtrar por usuario" value={usuario} onChange={(e) => setUsuario(e.target.value)} />
        <select className="input" value={accion} onChange={(e) => setAccion(e.target.value)}>
          <option value="">Todas las acciones</option>
          {ACCIONES.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
        <button className="btn btn-dark" onClick={cargar}>BUSCAR</button>
      </div>

      <div className="table-wrap">
        <table>
          <thead><tr><th>Fecha y hora</th><th>Usuario</th><th>Acción</th><th>Tabla</th><th>Antes</th><th>Detalle</th></tr></thead>
          <tbody>
            {filas.map((f) => (
              <tr key={f.id}>
                <td>{String(f.fecha).replace("T", " ").slice(0, 19)}</td>
                <td>{f.usuario || "—"}</td>
                <td><span className="badge badge-sis">{f.accion}</span></td>
                <td>{f.tabla}{f.idRegistro ? ` #${f.idRegistro}` : ""}</td>
                <td>{f.antes}</td>
                <td>{f.despues}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {filas.length === 0 && !error && <p className="muted">No hay registros con ese filtro.</p>}
      </div>
    </section>
  );
}
