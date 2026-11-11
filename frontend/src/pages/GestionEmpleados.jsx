import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { Link } from 'react-router-dom';
import { tienePermiso, mensajeError } from '../auth.js';
import { CampoTelefono } from '../components/Campos.jsx';
import { esCorreo, esTelefono, hace18Anios, telefonoGuardado } from '../formato.js';

const CARGOS = ['GUARDIA', 'SUPERVISOR', 'GERENTE_OPERACIONES', 'ADMINISTRATIVO', 'GERENTE_GENERAL'];

export default function GestionEmpleados() {
  const esAdmin = tienePermiso('SEGURIDAD');
  const [empleados, setEmpleados] = useState([]);
  const [formData, setFormData] = useState(null); // Si es null, no mostramos formulario
  const [mensaje, setMensaje] = useState({ tipo: '', texto: '' });

  const cargarEmpleados = async () => {
    try {
      const res = await api.get('/personal');
      setEmpleados(res.data);
    } catch (error) {
      setMensaje({ tipo: 'error', texto: mensajeError(error) });
    }
  };

  useEffect(() => { cargarEmpleados(); }, []);

  const handleEliminar = async (id) => {
    if (window.confirm('¿Seguro que deseas eliminar este empleado?')) {
      try {
        await api.delete(`/personal/${id}`);
        setMensaje({ tipo: 'success', texto: 'Empleado eliminado.' });
        cargarEmpleados();
      } catch (error) {
        setMensaje({ tipo: 'error', texto: 'No se puede eliminar: el empleado tiene registros asociados (usuario, asignaciones, etc.). Márquelo como INACTIVO en su lugar.' });
      }
    }
  };

  const handleEditarClick = (emp) => {
    // Se cargan todos los campos; los que vienen vacíos (datos de prueba) se deben completar
    setFormData({
      ...emp,
      telefono: telefonoGuardado(emp.telefono),
      correo: emp.correo || '',
      direccion: emp.direccion || '',
      fechaNacimiento: emp.fechaNacimiento || '',
      cargo: emp.cargo || '',
      sueldoBase: emp.sueldoBase ?? '',
    });
    setMensaje({ tipo: '', texto: '' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const poner = (campo) => (valor) => setFormData({ ...formData, [campo]: valor });
  const cambiar = (campo) => (e) => poner(campo)(e.target.value);

  const handleGuardarEdicion = async (e) => {
    e.preventDefault();
    if (!esTelefono(formData.telefono)) return setMensaje({ tipo: 'error', texto: 'El teléfono debe tener 8 dígitos (9999-9999).' });
    if (!esCorreo(formData.correo)) return setMensaje({ tipo: 'error', texto: 'El correo electrónico es obligatorio y debe ser válido.' });
    try {
      await api.put(`/personal/${formData.idEmpleado}`, { ...formData, sueldoBase: esAdmin && formData.sueldoBase !== '' ? formData.sueldoBase : null });
      setMensaje({ tipo: 'success', texto: 'Empleado actualizado.' });
      setFormData(null); // Ocultar formulario
      cargarEmpleados();
    } catch (error) {
      setMensaje({ tipo: 'error', texto: mensajeError(error, 'Error al actualizar.') });
    }
  };

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h1 style={{ margin: 0 }}>Gestión de empleados</h1>
        <Link to="/personal/nuevo" className="btn btn-dark">+ NUEVO EMPLEADO</Link>
      </div>
      {mensaje.texto && (
        <p className={'alerta ' + (mensaje.tipo === 'success' ? 'alerta-ok' : 'alerta-err')}>{mensaje.texto}</p>
      )}

      {/* FORMULARIO DE EDICIÓN (aparece solo si das clic en Editar) */}
      {formData && (
        <div style={{ backgroundColor: '#f8f9fa', padding: '20px', marginBottom: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0 }}>Editando a: {formData.nombreCompleto} <span className="muted">(identidad {formData.identidad})</span></h3>
          <form onSubmit={handleGuardarEdicion} className="form-col">
            <div className="form-grid">
              <div>
                <label>Nombre completo *</label>
                <input className="input" value={formData.nombreCompleto || ''} onChange={cambiar('nombreCompleto')} required minLength={5} maxLength={100} />
              </div>
              <div>
                <label>Fecha de nacimiento *</label>
                <input className="input" type="date" value={formData.fechaNacimiento} onChange={cambiar('fechaNacimiento')} required max={hace18Anios()} />
              </div>
              <div>
                <label>Teléfono celular *</label>
                <CampoTelefono value={formData.telefono} onChange={poner('telefono')} />
              </div>
              <div>
                <label>Correo electrónico *</label>
                <input className="input" type="email" value={formData.correo} onChange={cambiar('correo')} required maxLength={100} />
              </div>
              <div>
                <label>Dirección *</label>
                <input className="input" value={formData.direccion} onChange={cambiar('direccion')} required minLength={5} maxLength={200} />
              </div>
              <div>
                <label>Cargo *</label>
                <select className="input" value={formData.cargo} onChange={cambiar('cargo')} required>
                  <option value="">— Seleccione —</option>
                  {CARGOS.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              {esAdmin && (
                <div>
                  <label>Sueldo base (L) *</label>
                  <input className="input" type="number" min="1" step="0.01" value={formData.sueldoBase} onChange={cambiar('sueldoBase')} required />
                </div>
              )}
              <div>
                <label>Estado del candidato *</label>
                <select className="input" value={formData.estadoCandidato || 'CONTRATADO'} onChange={cambiar('estadoCandidato')} required>
                  <option value="APROBADO">APROBADO</option>
                  <option value="RECHAZADO">RECHAZADO</option>
                  <option value="CONTRATADO">CONTRATADO</option>
                </select>
              </div>
              <div>
                <label>Estado laboral *</label>
                <select className="input" value={formData.activo || 'S'} onChange={cambiar('activo')} required>
                  <option value="S">ACTIVO</option>
                  <option value="N">INACTIVO</option>
                </select>
              </div>
              <div>
                <label>Fecha de retiro (solo si ya no trabaja aquí)</label>
                <input className="input" type="date" value={formData.fechaRetiro || ''} onChange={cambiar('fechaRetiro')} />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
              <button type="submit" className="btn btn-dark">GUARDAR CAMBIOS</button>
              <button type="button" className="btn btn-outline" onClick={() => setFormData(null)}>CANCELAR</button>
            </div>
          </form>
        </div>
      )}

      {/* TABLA DE EMPLEADOS */}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Identidad</th>
              <th>Cargo</th>
              <th>Teléfono celular</th>
              <th>Correo</th>
              {esAdmin && <th>Sueldo</th>}
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {empleados.map((emp) => (
              <tr key={emp.idEmpleado}>
                <td>{emp.nombreCompleto}</td>
                <td>{emp.identidad}</td>
                <td>{emp.cargo}</td>
                <td>{telefonoGuardado(emp.telefono) || '—'}</td>
                <td>{emp.correo || <span className="badge badge-sis">Sin correo</span>}</td>
                {esAdmin && <td>{emp.sueldoBase != null ? 'L ' + Number(emp.sueldoBase).toLocaleString() : '—'}</td>}
                <td><span className={'badge ' + (emp.activo === 'S' ? 'badge-ok' : 'badge-off')}>{emp.activo === 'S' ? 'Activo' : 'Inactivo'}</span></td>
                <td className="acciones">
                  <button className="btn btn-outline btn-sm" onClick={() => handleEditarClick(emp)}>Editar</button>
                  <button className="btn btn-outline btn-sm" onClick={() => handleEliminar(emp.idEmpleado)}>Eliminar</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {empleados.length === 0 && <p className="muted">Aún no hay empleados registrados.</p>}
      </div>
    </div>
  );
}
