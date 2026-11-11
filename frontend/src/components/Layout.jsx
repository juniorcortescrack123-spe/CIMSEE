import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate, Link } from "react-router-dom";
import { MODULOS, SEGURIDAD, GRUPOS } from "../modulos.js";
import { api } from "../api.js";
import { obtenerSesion, tienePermiso, cerrarSesion } from "../auth.js";

export default function Layout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const sesion = obtenerSesion();
  const [conectado, setConectado] = useState(null);

  // Aviso para el administrador: solicitudes de clave y cuentas bloqueadas pendientes
  const [pendientes, setPendientes] = useState(0);
  useEffect(() => {
    if (!tienePermiso("SEGURIDAD")) return undefined;
    const revisar = () =>
      api.get("/seguridad/solicitudes-clave").then((r) => setPendientes(r.data.length)).catch(() => {});
    revisar();
    const t = setInterval(revisar, 30000);
    return () => clearInterval(t);
  }, []);

  // Campana del centro de notificaciones: alertas de vencimiento pendientes (contratos, permisos, pagos, matriculas)
  const [alertas, setAlertas] = useState({ total: 0, criticas: 0, altas: 0 });
  useEffect(() => {
    const revisar = () =>
      api.get("/notificaciones/resumen").then((r) => setAlertas(r.data)).catch(() => {});
    revisar();
    const t = setInterval(revisar, 60000);
    return () => clearInterval(t);
  }, [pathname]);

  // Comprueba cada 30 s que el backend y la base de datos respondan
  useEffect(() => {
    const revisar = () =>
      api.get("/health").then(() => setConectado(true)).catch(() => setConectado(false));
    revisar();
    const t = setInterval(revisar, 30000);
    return () => clearInterval(t);
  }, []);

  const visibles = MODULOS.filter((m) => tienePermiso(m.permiso));
  const todos = [...MODULOS, ...SEGURIDAD.items, ...GRUPOS.flatMap((g) => g.items), { nombre: "Cambiar contraseña", ruta: "/cambiar-clave" }];
  const actual = todos.find((m) => m.ruta === pathname) || MODULOS[0];

  // Grupos desplegables del menu: Seguridad, Gestión Comercial y Operaciones
  // El menú muestra solo lo que cada rol puede usar: un supervisor ve Visitas e Incidencias; el administrador ve todo.
  // Seguridad va al final porque se usa poco.
  const grupos = [
    ...GRUPOS.map((g) => ({ nombre: g.nombre, icono: g.icono, items: g.items.filter((m) => tienePermiso(m.permiso)) })),
    { nombre: SEGURIDAD.nombre, icono: SEGURIDAD.icono, items: tienePermiso(SEGURIDAD.permiso) ? SEGURIDAD.items : [] },
  ].filter((g) => g.items.length > 0);

  const [abiertos, setAbiertos] = useState({});
  // al entrar a una pantalla, se abre el grupo al que pertenece
  useEffect(() => {
    const g = grupos.find((x) => x.items.some((m) => m.ruta === pathname));
    if (g) setAbiertos((a) => ({ ...a, [g.nombre]: true }));
  }, [pathname]);
  const alternar = (nombre) => setAbiertos((a) => ({ ...a, [nombre]: !a[nombre] }));

  const salir = () => {
    cerrarSesion();
    navigate("/login");
  };

  const clase = ({ isActive }) => "nav-item" + (isActive ? " active" : "");

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          {/* Coloca el logo en frontend/public/logo.png */}
          <img src="/logo.png" alt="CIMSE" onError={(e) => (e.currentTarget.style.display = "none")} />
          <div className="brand-name">CIMSE</div>
          <div className="brand-sub">Seguridad Privada</div>
        </div>
        <nav>
          {visibles.map((m) => (
            <NavLink key={m.ruta} to={m.ruta} end className={clase}>
              <span className="nav-icon">{m.icono}</span>
              {m.nombre}
            </NavLink>
          ))}

          {grupos.map((g) => (
            <div key={g.nombre}>
              <button
                type="button"
                className="nav-grupo nav-grupo-btn"
                onClick={() => alternar(g.nombre)}
                aria-expanded={!!abiertos[g.nombre]}
              >
                <span>{g.icono} {g.nombre.toUpperCase()}</span>
                <span className="nav-flecha">{abiertos[g.nombre] ? "▾" : "▸"}</span>
              </button>
              {abiertos[g.nombre] &&
                g.items.map((m) => (
                  <NavLink key={m.ruta} to={m.ruta} className={clase}>
                    <span className="nav-icon">{m.icono}</span>
                    {m.nombre}
                  </NavLink>
                ))}
            </div>
          ))}
        </nav>
      </aside>

      <div className="content">
        <header className="topbar">
          <div className="breadcrumb">
            CIMSE &gt; <strong>{actual.nombre}</strong>
          </div>
          <div className="topbar-usuario">
            {pendientes > 0 && (
              <Link to="/seguridad/usuarios" className="aviso-pendientes" title="Cuentas bloqueadas o claves por restablecer">
                ⚠ {pendientes} pendiente(s) de seguridad
              </Link>
            )}
            <Link to="/notificaciones" className={"campana" + (alertas.criticas > 0 ? " campana-critica" : alertas.total > 0 ? " campana-alerta" : "")}
              title="Centro de notificaciones: contratos, permisos, pagos y matrículas por vencer">
              🔔 {alertas.total > 0 ? alertas.total : ""}
            </Link>
            <div className="usuario-datos">
              <strong>{sesion?.nombre || sesion?.username}</strong>
              <span className="muted">{sesion?.username} · {(sesion?.roles && sesion.roles.length ? sesion.roles.join(', ') : sesion?.rol)}</span>
              <span className={"estado-conexion " + (conectado === null ? "" : conectado ? "ok" : "mal")}>
                ● {conectado === null ? "Conectando..." : conectado ? "Conectado" : "Sin conexión"}
              </span>
            </div>
            <Link to="/cambiar-clave" className="btn btn-outline btn-sm">Cambiar contraseña</Link>
            <button className="btn btn-dark btn-sm" onClick={salir}>Cerrar sesión</button>
          </div>
        </header>
        <Outlet />
      </div>
    </div>
  );
}
