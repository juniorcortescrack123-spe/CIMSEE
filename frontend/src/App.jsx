import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Login from './Login';
import Dashboard from './pages/Dashboard';
import Layout from './components/Layout';
import FormularioEmpleado from './pages/FormularioEmpleado';
import GestionEmpleados from './pages/GestionEmpleados';
import CambiarClave from './pages/CambiarClave';
import Usuarios from './pages/seguridad/Usuarios';
import Roles from './pages/seguridad/Roles';
import Auditoria from './pages/seguridad/Auditoria';
import Clientes from './pages/comercial/Clientes';
import AsignarGuardias from './pages/comercial/AsignarGuardias';
import Contratos from './pages/comercial/Contratos';
import AlertasContratos from './pages/comercial/AlertasContratos';
import Puestos from './pages/comercial/Puestos';
import Cadena from './pages/comercial/Cadena';
import Turnos from './pages/operaciones/Turnos';
import Asignaciones from './pages/operaciones/Asignaciones';
import Supervisiones from './pages/operaciones/Supervisiones';
import Incidencias from './pages/operaciones/Incidencias';
import Activos from './pages/inventario/Activos';
import AsignacionEquipos from './pages/inventario/AsignacionEquipos';
import Acta from './pages/inventario/Acta';
import Movimientos from './pages/inventario/Movimientos';
import Proveedores from './pages/administracion/Proveedores';
import Compras from './pages/administracion/Compras';
import Pagos from './pages/administracion/Pagos';
import PermisosLegales from './pages/administracion/PermisosLegales';
import Reportes from './pages/reportes/Reportes';
import Notificaciones from './pages/notificaciones/Notificaciones';
import MiPuesto from './pages/guardia/MiPuesto';
import SolicitudesGuardias from './pages/operaciones/SolicitudesGuardias';
import { obtenerSesion, tienePermiso } from './auth';

// Sin sesión -> login. Si debe cambiar su clave -> pantalla de cambio de clave.
function RutaProtegida({ children }) {
  const sesion = obtenerSesion();
  const { pathname } = useLocation();
  if (!sesion) return <Navigate to="/login" replace />;
  if (sesion.debeCambiarClave && pathname !== '/cambiar-clave') return <Navigate to="/cambiar-clave" replace />;
  return children;
}

// Solo deja ver la pantalla a quien tenga el permiso del módulo
function ConPermiso({ permiso, children }) {
  if (tienePermiso(permiso)) return children;
  return (
    <section className="card">
      <h1>Sin acceso</h1>
      <p className="muted">Su rol no tiene permiso para ver este módulo.</p>
    </section>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* El Login va SOLO, sin menú lateral */}
        <Route path="/" element={<Login />} />
        <Route path="/login" element={<Login />} />

        {/* Rutas que SÍ llevan menú lateral y exigen sesión */}
        <Route element={<RutaProtegida><Layout /></RutaProtegida>}>

          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/cambiar-clave" element={<CambiarClave />} />

          {/* Gestión de empleados */}
          <Route path="/personal/nuevo" element={<ConPermiso permiso="PERSONAL"><FormularioEmpleado /></ConPermiso>} />
          <Route path="/personal/gestion" element={<ConPermiso permiso="PERSONAL"><GestionEmpleados /></ConPermiso>} />

          {/* Módulo de Seguridad (Sprint 1): usuarios, roles y permisos, auditoría */}
          <Route path="/seguridad/usuarios" element={<ConPermiso permiso="SEGURIDAD"><Usuarios /></ConPermiso>} />
          <Route path="/seguridad/roles" element={<ConPermiso permiso="SEGURIDAD"><Roles /></ConPermiso>} />
          <Route path="/seguridad/auditoria" element={<ConPermiso permiso="SEGURIDAD"><Auditoria /></ConPermiso>} />

          {/* Sprint 2: gestión comercial y operaciones */}
          <Route path="/comercial/clientes" element={<ConPermiso permiso="CLIENTES"><Clientes /></ConPermiso>} />
          <Route path="/comercial/clientes/:id/guardias" element={<ConPermiso permiso="CLIENTES"><ConPermiso permiso="SUPERVISION"><AsignarGuardias /></ConPermiso></ConPermiso>} />
          <Route path="/comercial/contratos" element={<ConPermiso permiso="CONTRATOS"><Contratos /></ConPermiso>} />
          <Route path="/comercial/alertas" element={<ConPermiso permiso="CONTRATOS"><AlertasContratos /></ConPermiso>} />
          <Route path="/comercial/puestos" element={<ConPermiso permiso="CLIENTES"><Puestos /></ConPermiso>} />
          <Route path="/comercial/cadena" element={<ConPermiso permiso={["CLIENTES", "CADENA"]}><Cadena /></ConPermiso>} />
          <Route path="/operaciones/turnos" element={<ConPermiso permiso="SUPERVISION"><Turnos /></ConPermiso>} />
          <Route path="/operaciones/asignaciones" element={<ConPermiso permiso="SUPERVISION"><Asignaciones /></ConPermiso>} />

          {/* Sprint 3: supervisiones, incidencias, inventario y equipos */}
          <Route path="/operaciones/supervisiones" element={<ConPermiso permiso="VISITAS"><Supervisiones /></ConPermiso>} />
          <Route path="/operaciones/incidencias" element={<ConPermiso permiso="VISITAS"><Incidencias /></ConPermiso>} />
          <Route path="/inventario/activos" element={<ConPermiso permiso="INVENTARIO"><Activos /></ConPermiso>} />
          <Route path="/inventario/equipos" element={<ConPermiso permiso="EQUIPOS"><AsignacionEquipos /></ConPermiso>} />
          <Route path="/inventario/acta/:id" element={<ConPermiso permiso="EQUIPOS"><Acta /></ConPermiso>} />
          <Route path="/inventario/movimientos" element={<ConPermiso permiso="INVENTARIO"><Movimientos /></ConPermiso>} />

          {/* Sprint 4: administración, reportes y notificaciones */}
          <Route path="/administracion/proveedores" element={<ConPermiso permiso="PROVEEDORES"><Proveedores /></ConPermiso>} />
          <Route path="/administracion/compras" element={<ConPermiso permiso="COMPRAS"><Compras /></ConPermiso>} />
          <Route path="/administracion/pagos" element={<ConPermiso permiso="PAGOS"><Pagos /></ConPermiso>} />
          <Route path="/administracion/permisos" element={<ConPermiso permiso="PERMISOS_LEGALES"><PermisosLegales /></ConPermiso>} />
          <Route path="/reportes" element={<ConPermiso permiso="REPORTES"><Reportes /></ConPermiso>} />
          <Route path="/notificaciones" element={<Notificaciones />} />
          <Route path="/guardia" element={<ConPermiso permiso="PORTAL_GUARDIA"><MiPuesto /></ConPermiso>} />
          <Route path="/operaciones/solicitudes" element={<ConPermiso permiso="VISITAS"><SolicitudesGuardias /></ConPermiso>} />

          {/* Rutas antiguas: las pantallas genéricas se eliminaron; estas direcciones llevan a la pantalla real */}
          <Route path="/usuarios" element={<Navigate to="/seguridad/usuarios" replace />} />
          <Route path="/configuracion" element={<Navigate to="/seguridad/usuarios" replace />} />
          <Route path="/personal" element={<Navigate to="/personal/gestion" replace />} />
          <Route path="/supervision" element={<Navigate to="/operaciones/asignaciones" replace />} />
          <Route path="/clientes" element={<Navigate to="/comercial/clientes" replace />} />
          <Route path="/contratos" element={<Navigate to="/comercial/contratos" replace />} />
          <Route path="/pagos" element={<Navigate to="/administracion/pagos" replace />} />
          <Route path="/proveedores" element={<Navigate to="/administracion/proveedores" replace />} />
          <Route path="/inventario" element={<Navigate to="/inventario/activos" replace />} />
          <Route path="/equipos" element={<Navigate to="/inventario/equipos" replace />} />

        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
