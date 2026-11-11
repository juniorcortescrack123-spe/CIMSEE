import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api.js";
import { GRUPOS, SEGURIDAD } from "../modulos.js";
import { obtenerSesion, tienePermiso } from "../auth.js";

export default function Dashboard() {
  const sesion = obtenerSesion();
  const [resumen, setResumen] = useState(null);
  const [alertas, setAlertas] = useState([]);
  const [miEquipo, setMiEquipo] = useState([]); // equipo asignado a mi nombre (vehiculo, arma...)

  useEffect(() => {
    api.get("/mi-equipo").then((r) => setMiEquipo(r.data)).catch(() => {});
    api.get("/notificaciones/resumen").then((r) => setResumen(r.data)).catch(() => {});
    api.get("/notificaciones", { params: { estado: "PENDIENTE" } }).then((r) => setAlertas(r.data.slice(0, 5))).catch(() => {});
  }, []);

  // Accesos rápidos: una tarjeta por grupo del menú, con las pantallas a las que el usuario tiene permiso
  const grupos = [
    ...GRUPOS.map((g) => ({ nombre: g.nombre, icono: g.icono, items: g.items.filter((m) => tienePermiso(m.permiso)) })),
    { nombre: SEGURIDAD.nombre, icono: SEGURIDAD.icono, items: tienePermiso(SEGURIDAD.permiso) ? SEGURIDAD.items : [] },
  ].filter((g) => g.items.length > 0);

  return (
    <>
      <div className="hero-row">
        <section className="card hero">
          <div>
            <h1>Bienvenido, {sesion?.nombre || sesion?.username}</h1>
            <p>
              Sistema de Gestión Administrativa y Operativa de CIMSE. Desde aquí gestiona el personal, los clientes y contratos, las asignaciones de
              guardias, el inventario, las compras, los pagos y los permisos legales.
            </p>
            <div className="hero-buttons">
              {tienePermiso("REPORTES") && <Link to="/reportes" className="btn btn-dark">VER REPORTES</Link>}
              <Link to="/notificaciones" className="btn btn-outline">CENTRO DE NOTIFICACIONES</Link>
            </div>
          </div>
          <div className="shield">🛡</div>
        </section>

        <section className="card notif">
          <small>NOTIFICACIONES</small>
          {resumen && resumen.total > 0 && (
            <p style={{ margin: "8px 0" }}>
              <strong>{resumen.total}</strong> pendiente(s): <span className="badge prio-CRITICA">{resumen.criticas} críticas</span>{" "}
              <span className="badge prio-ALTA">{resumen.altas} altas</span>
            </p>
          )}
          {alertas.length === 0 && <p className="muted">Sin alertas pendientes.</p>}
          {alertas.map((a) => (
            <div key={a.id} className="notif-item">
              <strong>{a.descripcion}</strong>
              <span>Vence: {String(a.fechaVencimiento)}</span>
            </div>
          ))}
        </section>
      </div>

      {miEquipo.length > 0 && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2 className="section-title" style={{ marginTop: 0 }}>Mi equipo asignado</h2>
          <div className="table-wrap"><table>
            <thead><tr><th>Equipo</th><th>Serie / placa</th><th>Código</th><th>Entregado</th></tr></thead>
            <tbody>{miEquipo.map((e) => (
              <tr key={e.idAsignacion}><td><strong>{e.descripcion}</strong></td><td>{e.serieOPlaca || "—"}</td><td>{e.codigo}</td><td>{String(e.fechaEntrega)}</td></tr>
            ))}</tbody>
          </table></div>
        </section>
      )}

      <div className="grid" style={{ marginTop: 16 }}>
        {grupos.map((g) => (
          <section key={g.nombre} className="card">
            <div className="modulo-icon">{g.icono}</div>
            <strong>{g.nombre}</strong>
            <div style={{ display: "flex", flexDirection: "column", gap: 4, marginTop: 8 }}>
              {g.items.map((m) => (
                <Link key={m.ruta} to={m.ruta} style={{ fontSize: 13 }}>{m.icono} {m.nombre}</Link>
              ))}
            </div>
          </section>
        ))}
      </div>
    </>
  );
}
