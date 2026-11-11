import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { tienePermiso, mensajeError } from '../auth.js';
import { CampoIdentidad, CampoTelefono } from '../components/Campos.jsx';
import { esCorreo, esIdentidad, esTelefono, hace18Anios } from '../formato.js';

const CARGOS = ['GUARDIA', 'SUPERVISOR', 'GERENTE_OPERACIONES', 'ADMINISTRATIVO', 'GERENTE_GENERAL'];
const VACIO = {
  identidad: '', nombreCompleto: '', fechaNacimiento: '', direccion: '',
  telefono: '', correo: '', cargo: '', sueldoBase: ''
};

// Registro de un empleado nuevo. TODOS los campos son obligatorios (el sueldo solo lo ve y lo escribe el administrador).
export default function FormularioEmpleado() {
  const navigate = useNavigate();
  const esAdmin = tienePermiso('SEGURIDAD');
  const [formData, setFormData] = useState(VACIO);
  const [mensaje, setMensaje] = useState({ tipo: '', texto: '' });

  const poner = (campo) => (valor) => setFormData({ ...formData, [campo]: valor });
  const handleChange = (e) => poner(e.target.name)(e.target.value);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMensaje({ tipo: '', texto: '' });

    // Validaciones de formato (también se validan en el servidor)
    if (!esIdentidad(formData.identidad)) return setMensaje({ tipo: 'error', texto: 'La identidad debe tener exactamente 13 dígitos.' });
    if (formData.nombreCompleto.trim().split(/\s+/).length < 2) return setMensaje({ tipo: 'error', texto: 'Escriba el nombre completo (nombre y apellido).' });
    if (!esTelefono(formData.telefono)) return setMensaje({ tipo: 'error', texto: 'El teléfono debe tener 8 dígitos (9999-9999).' });
    if (!esCorreo(formData.correo)) return setMensaje({ tipo: 'error', texto: 'El correo electrónico es obligatorio y debe ser válido.' });

    try {
      await api.post('/personal', { ...formData, sueldoBase: esAdmin ? formData.sueldoBase : null });
      setMensaje({ tipo: 'success', texto: '¡Empleado registrado con éxito! Ya puede crearle su usuario en Seguridad > Usuarios.' });
      setFormData(VACIO);
    } catch (error) {
      console.error(error);
      setMensaje({ tipo: 'error', texto: mensajeError(error, 'Error al registrar el empleado.') });
    }
  };

  return (
    <section className="card" style={{ maxWidth: 720, margin: '0 auto' }}>
      <h1>Registrar nuevo empleado</h1>
      <p className="muted">Todos los campos son obligatorios. El correo se usa para enviarle su clave temporal.</p>

      {mensaje.texto && (
        <p className={'alerta ' + (mensaje.tipo === 'success' ? 'alerta-ok' : 'alerta-err')}>{mensaje.texto}</p>
      )}

      <form onSubmit={handleSubmit} className="form-col">
        <div className="form-grid">
          <div>
            <label>Identidad * (13 dígitos)</label>
            <CampoIdentidad value={formData.identidad} onChange={poner('identidad')} />
          </div>
          <div>
            <label>Nombre completo *</label>
            <input className="input" type="text" name="nombreCompleto" value={formData.nombreCompleto} onChange={handleChange} required minLength={5} maxLength={100} />
          </div>
          <div>
            <label>Fecha de nacimiento *</label>
            <input className="input" type="date" name="fechaNacimiento" value={formData.fechaNacimiento} onChange={handleChange} required max={hace18Anios()} />
          </div>
          <div>
            <label>Teléfono celular *</label>
            <CampoTelefono value={formData.telefono} onChange={poner('telefono')} />
          </div>
          <div>
            <label>Correo electrónico *</label>
            <input className="input" type="email" name="correo" value={formData.correo} onChange={handleChange} required maxLength={100} />
          </div>
          <div>
            <label>Cargo *</label>
            <select className="input" name="cargo" value={formData.cargo} onChange={handleChange} required>
              <option value="">— Seleccione —</option>
              {CARGOS.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          {esAdmin && (
            <div>
              <label>Sueldo base (L) *</label>
              <input className="input" type="number" min="1" step="0.01" name="sueldoBase" value={formData.sueldoBase} onChange={handleChange} required />
            </div>
          )}
          <div>
            <label>Dirección *</label>
            <input className="input" type="text" name="direccion" value={formData.direccion} onChange={handleChange} required minLength={5} maxLength={200} />
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
          <button type="submit" className="btn btn-dark">GUARDAR EMPLEADO</button>
          <button type="button" className="btn btn-outline" onClick={() => navigate('/personal/gestion')}>VOLVER A LA LISTA</button>
        </div>
      </form>
    </section>
  );
}
