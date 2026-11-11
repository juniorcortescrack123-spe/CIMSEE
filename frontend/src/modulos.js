// Pantalla de inicio. Las demas pantallas viven en los grupos del menu (Seguridad y GRUPOS, mas abajo).
// Se eliminaron las pantallas genericas que solo mostraban una tabla de la base de datos (Clientes, Contratos,
// Supervision, Inventario, Equipos, Pagos, Proveedores y Reportes): cada una tiene ahora su pantalla real.
export const MODULOS = [
  { nombre: "Panel Principal", ruta: "/dashboard", icono: "▦" },
];

// Modulo de Seguridad (Sprint 1): solo lo ve quien tenga el permiso SEGURIDAD
export const SEGURIDAD = {
  nombre: "Seguridad",
  icono: "🔐",
  permiso: "SEGURIDAD",
  items: [
    { nombre: "Usuarios", ruta: "/seguridad/usuarios", icono: "👥" },
    { nombre: "Roles y permisos", ruta: "/seguridad/roles", icono: "🛂" },
    { nombre: "Auditoría", ruta: "/seguridad/auditoria", icono: "📜" },
  ],
};

// Grupos desplegables del menu lateral
export const GRUPOS = [
  {
    nombre: "Mi puesto",
    icono: "🛡️",
    items: [
      { nombre: "Mi puesto, solicitudes e incidencias", ruta: "/guardia", icono: "🛡️", permiso: "PORTAL_GUARDIA" },
    ],
  },
  {
    nombre: "Personal",
    icono: "👤",
    items: [
      { nombre: "Registrar empleado", ruta: "/personal/nuevo", icono: "➕", permiso: "PERSONAL" },
      { nombre: "Gestión de empleados", ruta: "/personal/gestion", icono: "🗂️", permiso: "PERSONAL" },
    ],
  },
  {
    nombre: "Gestión Comercial",
    icono: "💼",
    items: [
      { nombre: "Gestión de clientes", ruta: "/comercial/clientes", icono: "🏢", permiso: "CLIENTES" },
      { nombre: "Gestión de contratos", ruta: "/comercial/contratos", icono: "📑", permiso: "CONTRATOS" },
      { nombre: "Alertas de contratos", ruta: "/comercial/alertas", icono: "⏰", permiso: "CONTRATOS" },
      { nombre: "Puestos de trabajo", ruta: "/comercial/puestos", icono: "📍", permiso: "CLIENTES" },
      { nombre: "Cadena relacional", ruta: "/comercial/cadena", icono: "🔗", permiso: ["CLIENTES", "CADENA"] },
    ],
  },
  {
    nombre: "Operaciones",
    icono: "🛡️",
    items: [
      { nombre: "Turnos", ruta: "/operaciones/turnos", icono: "🕒", permiso: "SUPERVISION" },
      { nombre: "Asignación a puestos", ruta: "/operaciones/asignaciones", icono: "🧭", permiso: "SUPERVISION" },
      { nombre: "Visitas de supervisión", ruta: "/operaciones/supervisiones", icono: "🔎", permiso: "VISITAS" },
      { nombre: "Incidencias", ruta: "/operaciones/incidencias", icono: "🚨", permiso: "VISITAS" },
      { nombre: "Solicitudes de guardias", ruta: "/operaciones/solicitudes", icono: "📨", permiso: "VISITAS" },
    ],
  },
  {
    nombre: "Inventario y Equipos",
    icono: "📦",
    items: [
      { nombre: "Activos fijos", ruta: "/inventario/activos", icono: "🗄️", permiso: "INVENTARIO" },
      { nombre: "Asignación de equipos", ruta: "/inventario/equipos", icono: "🔫", permiso: "EQUIPOS" },
      { nombre: "Movimientos", ruta: "/inventario/movimientos", icono: "🔁", permiso: "INVENTARIO" },
    ],
  },
  {
    nombre: "Administración",
    icono: "🧾",
    items: [
      { nombre: "Proveedores", ruta: "/administracion/proveedores", icono: "🚚", permiso: "PROVEEDORES" },
      { nombre: "Compras e insumos", ruta: "/administracion/compras", icono: "🛒", permiso: "COMPRAS" },
      { nombre: "Pagos", ruta: "/administracion/pagos", icono: "💵", permiso: "PAGOS" },
      { nombre: "Permisos legales", ruta: "/administracion/permisos", icono: "📜", permiso: "PERMISOS_LEGALES" },
    ],
  },
  {
    nombre: "Reportes y alertas",
    icono: "📊",
    items: [
      { nombre: "Reportes", ruta: "/reportes", icono: "📈", permiso: "REPORTES" },
      { nombre: "Centro de notificaciones", ruta: "/notificaciones", icono: "🔔" },
    ],
  },
];
