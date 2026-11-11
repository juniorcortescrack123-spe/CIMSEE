-- Datos de prueba (ficticios)
INSERT INTO turnos (nombre, hora_inicio, hora_fin) VALUES
('MATUTINO','06:00','14:00'), ('VESPERTINO','14:00','22:00'), ('NOCTURNO','22:00','06:00');

INSERT INTO clientes (nombre, rtn, direccion, telefono, correo, contacto) VALUES
('Banco Ejemplo', '08011999000011', 'Col. Palmira, Tegucigalpa', '2222-3333', 'contacto@banco.com', 'Ana Martinez'),
('Centro Comercial Norte', '08011999000022', 'Blvd. Norte, San Pedro Sula', '2444-5555', 'info@ccnorte.com', 'Luis Mejia');

INSERT INTO proveedores (tipo_proveedor, nombre, rtn, telefono, correo, contacto) VALUES
('Uniformes', 'Uniformes HN', '08011999000033', '2255-1000', 'ventas@uniformeshn.com', 'Rosa Diaz'),
('Armamento', 'Armeria Central', '08011999000044', '2255-2000', 'ventas@armeriacentral.com', 'Pedro Ruiz');

INSERT INTO permisos_administrativos (tipo, numero_permiso, fecha_emision, fecha_vencimiento) VALUES
('OPERACION', 'OP-2026-001', '2026-01-15', '2027-01-15'),
('ARMAS',     'AR-2026-010', '2026-02-01', '2027-02-01'),
('ALCALDIA',  'AL-2026-007', '2025-11-20', '2026-11-20');

INSERT INTO empleados (identidad, nombre_completo, fecha_nacimiento, cargo, estado_candidato, sueldo_base, telefono) VALUES
('0801198000001', 'Gerente General de Prueba',       '1980-05-10', 'GERENTE_GENERAL',     'CONTRATADO', 45000, '9999-0001'),
('0801198500002', 'Gerente Operaciones de Prueba',   '1985-03-22', 'GERENTE_OPERACIONES', 'CONTRATADO', 38000, '9999-0002'),
('0801199000003', 'Administrador de Prueba',         '1990-07-15', 'ADMINISTRATIVO',      'CONTRATADO', 22000, '9999-0003'),
('0801199200004', 'Supervisor de Prueba',            '1992-01-30', 'SUPERVISOR',          'CONTRATADO', 18000, '9999-0004'),
('0801199500005', 'Juan Perez',                      '1995-09-12', 'GUARDIA',             'CONTRATADO', 12000, '9999-0005'),
('0801199600006', 'Carlos Lopez',                    '1996-11-02', 'GUARDIA',             'CONTRATADO', 12000, '9999-0006'),
('0801199300007', 'Personal Administrativo de Prueba', '1993-04-18', 'ADMINISTRATIVO',      'CONTRATADO', 16000, '9999-0007');

-- Contrasena de los usuarios de prueba: 123456 (guardada con BCrypt)
INSERT INTO usuarios (id_empleado, nombre_usuario, contrasena_hash, rol) VALUES
(3, 'admin', '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.', 'ADMINISTRADOR'),
(4, 'supervisor1', '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.', 'SUPERVISOR'),
(1, 'gerente', '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.', 'GERENTE_GENERAL'),
(5, 'guardia1', '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.', 'GUARDIA'),
(6, 'guardia2', '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.', 'GUARDIA'),
(7, 'administrativo1', '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.', 'ADMINISTRATIVO');

INSERT INTO solicitudes (id_empleado, tipo, fecha_inicio, fecha_fin, motivo, id_aprobador) VALUES
(5, 'VACACIONES', '2026-12-20', '2026-12-30', 'Vacaciones de fin de ano', 2);

INSERT INTO puestos_trabajo (id_cliente, descripcion, ubicacion, tipo_turno, horas_contratadas) VALUES
(1, 'Entrada principal', 'Banco Ejemplo - Agencia Centro', '12h', 12),
(2, 'Parqueo sur',       'CC Norte - Parqueo sur',          '24h', 24);

INSERT INTO contratos (id_cliente, fecha_inicio, fecha_vencimiento, duracion_anios, monto_mensual, dia_pago_estipulado, cai_factura) VALUES
(1, '2026-01-01', '2027-01-01', 1, 45000.00, 15, 'CAI-0001'),
(2, '2026-03-01', '2028-03-01', 2, 60000.00, 30, 'CAI-0002');

INSERT INTO facturas_emitidas (id_contrato, numero_factura, fecha_emision, monto, fecha_pago, estado_pago) VALUES
(1, '001-001-01-00000001', '2026-02-01', 45000.00, '2026-02-15', 'PAGADO'),
(1, '001-001-01-00000002', '2026-03-01', 45000.00, NULL, 'PENDIENTE');

INSERT INTO asignacion_puestos (id_empleado, id_puesto, fecha_inicio, turno_real, id_turno, id_usuario_asigna) VALUES
(5, 1, '2026-01-01', 'MATUTINO', 1, 1),
(6, 2, '2026-03-01', 'NOCTURNO', 3, 1);

INSERT INTO asistencia_diaria (id_empleado, id_asignacion_puesto, fecha, hora_entrada, id_turno, estado_asistencia, entrega_realizada, recepcion_realizada) VALUES
(5, 1, '2026-10-12', '2026-10-12 05:55:00', 1, 'PRESENTE', 'S', 'S');

INSERT INTO supervision_operativa (id_empleado, id_asistencia, guardia_presente, observaciones) VALUES
(4, 1, 'S', 'Ronda de verificacion sin novedades');

INSERT INTO incidencias (id_empleado_reporte, id_puesto, id_supervisor_asignado, tipo, descripcion) VALUES
(4, 1, 4, 'LLEGADA_TARDIA', 'Guardia llego 15 minutos tarde al turno');

INSERT INTO compras_proveedores (id_proveedor, id_empleado_autoriza, categoria_insumo, numero_factura, monto_total) VALUES
(2, 3, 'ARMAS',     'F-0001', 25000.00),
(1, 3, 'UNIFORMES', 'F-0002',  5000.00),
(1, 3, 'PAPELERIA', 'F-0003',   800.00),
(2, 3, 'VEHICULOS', 'F-0004', 450000.00);

INSERT INTO detalle_compras (id_compra, descripcion, cantidad, unidad_medida, precio_unitario, subtotal) VALUES
(1, 'Pistola 9mm',        1,  'UNIDAD', 25000.00, 25000.00),
(2, 'Uniforme completo', 10,  'UNIDAD',   500.00,  5000.00),
(3, 'Resma de papel',    20,  'UNIDAD',    40.00,   800.00);

INSERT INTO inventario_activos (id_compra, tipo_activo, descripcion, numero_serie_o_placa, codigo_inventario, valor_adquisicion, ubicacion_actual) VALUES
(1, 'ARMA',     'Pistola 9mm',          'SN-000123', 'INV-0001', 25000.00,  'Armeria'),
(4, 'VEHICULO', 'Pickup Toyota Hilux',  'HAB-1234',  'INV-0002', 450000.00, 'Parqueo y flota'),
(2, 'UNIFORME', 'Uniforme completo M',  NULL,        'INV-0003', 500.00,    'Almacen principal');

-- El supervisor de prueba (empleado 4) tiene a su cargo los dos clientes de prueba
INSERT INTO supervisor_clientes (id_supervisor, id_cliente) VALUES (4, 1), (4, 2);

INSERT INTO inventario_materiales (id_compra, descripcion, cantidad_adquirida, unidad_medida, stock_actual) VALUES
(3, 'Resma de papel', 20, 'UNIDAD', 20);

INSERT INTO armas (id_activo, marca, modelo, calibre, tipo_arma, numero_registro, numero_serie) VALUES
(1, 'Glock', '17', '9mm', 'PISTOLA', 'REG-001', 'SN-000123');

INSERT INTO vehiculos (id_activo, tipo_vehiculo, marca, modelo, anio, color, placa, vin, numero_motor, kilometraje_actual, fecha_vencimiento_matricula) VALUES
(2, 'CAMIONETA', 'Toyota', 'Hilux', 2022, 'Blanco', 'HAB-1234', 'VIN-0001', 'MOT-0001', 15000, '2027-03-01');

INSERT INTO uniformes (id_activo, tipo_prenda, talla, genero, cantidad) VALUES
(3, 'Uniforme completo', 'M', 'M', 10);

INSERT INTO asignacion_equipos (id_empleado, id_activo, id_empleado_entrega, id_empleado_recibe, id_usuario_asigna, observaciones) VALUES
(5, 1, 3, 5, 1, 'Acta de resguardo #001');

INSERT INTO asignacion_uniformes (id_empleado, id_uniforme, cantidad, id_usuario_asigna) VALUES
(5, 1, 2, 1);

INSERT INTO movimientos_inventario (id_activo, tipo_movimiento, cantidad, ubicacion_destino, motivo, id_empleado_responsable, id_usuario) VALUES
(1, 'ENTRADA', 1, 'Armeria CIMSE', 'Ingreso inicial a inventario', 3, 1);

INSERT INTO pagos_administrativos (id_permiso, concepto, categoria, fecha_vencimiento, monto, estado) VALUES
(3,    'Renovacion permiso de alcaldia', 'ALCALDIA', '2026-11-20', 5000.00, 'PENDIENTE'),
(NULL, 'IHSS octubre',                   'IHSS',     '2026-10-30', 18000.00, 'PENDIENTE');

INSERT INTO alertas_vencimiento (tipo_entidad, id_referencia, descripcion, fecha_vencimiento, dias_anticipacion, fecha_alerta, prioridad) VALUES
('PERMISO', 3, 'Permiso de alcaldia por vencer', '2026-11-20', 30, '2026-10-21', 'ALTA'),
('CONTRATO', 1, 'Contrato Banco Ejemplo por vencer', '2027-01-01', 30, '2026-12-02', 'MEDIA');

INSERT INTO auditoria (id_usuario, accion, tabla_afectada, id_registro) VALUES
(1, 'LOGIN', 'usuarios', 1);

-- ---------- SEGURIDAD (Sprint 1): permisos y roles base ----------
INSERT INTO permisos (codigo, descripcion) VALUES
('PERSONAL', 'Gestion de Personal'),
('SUPERVISION', 'Supervision'),
('CLIENTES', 'Clientes'),
('CONTRATOS', 'Contratos'),
('PAGOS', 'Pagos y Permisos'),
('PROVEEDORES', 'Proveedores'),
('INVENTARIO', 'Inventario'),
('EQUIPOS', 'Asignacion de Equipos'),
('REPORTES', 'Reportes'),
('VISITAS', 'Visitas de supervision e incidencias de campo'),
('PORTAL_GUARDIA', 'Portal del guardia: mi puesto, solicitudes e incidencias'),
('COMPRAS', 'Compras e insumos'),
('PERMISOS_LEGALES', 'Permisos legales (alcaldia, armeria, operacion)'),
('CADENA', 'Cadena relacional cliente - contrato - puesto - guardia'),
('SEGURIDAD', 'Seguridad: usuarios, roles y auditoria');

INSERT INTO roles (nombre, descripcion, es_sistema) VALUES
('ADMINISTRADOR', 'Administra usuarios, roles y la seguridad del sistema', 'S'),
('GERENTE_GENERAL', 'Gerencia general de la empresa', 'S'),
('GERENTE_OPERACIONES', 'Gerencia de operaciones', 'S'),
('SUPERVISOR', 'Supervisa guardias y puestos de trabajo', 'S'),
('GUARDIA', 'Guardia de seguridad', 'S'),
('ADMINISTRATIVO', 'Personal administrativo: reportes, compras, pagos, activos, contratos y cadena relacional', 'S');

-- El ADMINISTRADOR tiene todos los permisos
INSERT INTO rol_permisos (id_rol, codigo_permiso)
SELECT r.id_rol, p.codigo FROM roles r CROSS JOIN permisos p WHERE r.nombre = 'ADMINISTRADOR';

INSERT INTO rol_permisos (id_rol, codigo_permiso)
SELECT r.id_rol, v.codigo
FROM roles r
JOIN (VALUES
('GERENTE_GENERAL','PERSONAL'), ('GERENTE_GENERAL','SUPERVISION'), ('GERENTE_GENERAL','CLIENTES'),
('GERENTE_GENERAL','CONTRATOS'), ('GERENTE_GENERAL','PAGOS'), ('GERENTE_GENERAL','PROVEEDORES'),
('GERENTE_GENERAL','INVENTARIO'), ('GERENTE_GENERAL','EQUIPOS'), ('GERENTE_GENERAL','REPORTES'),
('GERENTE_GENERAL','VISITAS'), ('GERENTE_OPERACIONES','VISITAS'),
('GERENTE_OPERACIONES','PERSONAL'), ('GERENTE_OPERACIONES','SUPERVISION'), ('GERENTE_OPERACIONES','CLIENTES'),
('GERENTE_OPERACIONES','CONTRATOS'), ('GERENTE_OPERACIONES','INVENTARIO'), ('GERENTE_OPERACIONES','EQUIPOS'),
('GERENTE_OPERACIONES','REPORTES'),
('SUPERVISOR','VISITAS'), ('GUARDIA','PORTAL_GUARDIA'),
('ADMINISTRATIVO','REPORTES'), ('ADMINISTRATIVO','COMPRAS'), ('ADMINISTRATIVO','PAGOS'),
('ADMINISTRATIVO','INVENTARIO'), ('ADMINISTRATIVO','CONTRATOS'), ('ADMINISTRATIVO','CADENA')
) AS v(rol, codigo) ON v.rol = r.nombre;

-- Compras y permisos legales ahora tienen su propio permiso: lo conservan los roles que ya los usaban
INSERT INTO rol_permisos (id_rol, codigo_permiso)
SELECT id_rol, 'COMPRAS' FROM rol_permisos WHERE codigo_permiso = 'PROVEEDORES' ON CONFLICT DO NOTHING;
INSERT INTO rol_permisos (id_rol, codigo_permiso)
SELECT id_rol, 'PERMISOS_LEGALES' FROM rol_permisos WHERE codigo_permiso = 'PAGOS' ON CONFLICT DO NOTHING;

-- ---------- Cada usuario de prueba conserva su rol como rol principal (un usuario puede tener varios roles) ----------
INSERT INTO usuario_roles (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol FROM usuarios u JOIN roles r ON r.nombre = u.rol
ON CONFLICT DO NOTHING;
