import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from './api';
import { guardarSesion, mensajeError } from './auth';

// Recuerda en el navegador que la cuenta quedo bloqueada, para que la pantalla siga bloqueada aunque se recargue
const CLAVE_BLOQUEO = 'cimse_bloqueo';
const leerBloqueo = () => {
  try {
    return JSON.parse(localStorage.getItem(CLAVE_BLOQUEO));
  } catch {
    return null;
  }
};

export default function Login() {
  const [modo, setModo] = useState('login'); // 'login' o 'recuperar'
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [bloqueo, setBloqueo] = useState(leerBloqueo()); // { usuario, mensaje } cuando la cuenta esta bloqueada
  const navigate = useNavigate(); // Para redirigir al usuario luego de entrar

  const bloquearPantalla = (usuario, mensaje) => {
    const b = { usuario, mensaje };
    localStorage.setItem(CLAVE_BLOQUEO, JSON.stringify(b));
    setBloqueo(b);
    setPassword('');
    setError('');
  };

  const liberarPantalla = () => {
    localStorage.removeItem(CLAVE_BLOQUEO);
    setBloqueo(null);
    setPassword('');
    setError('');
    setInfo('');
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');

    try {
      // Hacemos la petición a tu backend en Spring Boot
      const response = await api.post('/auth/login', {
        username: username.trim(),
        password: password
      });

      // Si es exitoso, guardamos el token y los datos del usuario en el navegador
      localStorage.removeItem(CLAVE_BLOQUEO);
      guardarSesion(response.data);
      localStorage.setItem('rol', response.data.rol);

      // Si es su primer ingreso (o le restablecieron la clave) debe cambiarla
      navigate(response.data.debeCambiarClave ? '/cambiar-clave' : '/dashboard');

    } catch (err) {
      if (err.response && err.response.status === 401) {
        const datos = err.response.data || {};
        if (datos.bloqueado) {
          // 3 intentos fallidos (o cuenta ya bloqueada): se bloquea la pantalla de inicio de sesión
          bloquearPantalla(username.trim(), datos.error);
        } else {
          setPassword('');
          setError(datos.error && datos.error !== 'Credenciales incorrectas' ? datos.error : 'Usuario o contraseña incorrectos.');
        }
      } else {
        setError('Error de conexión con el servidor.');
      }
    }
  };

  // Recuperación de contraseña: deja una solicitud para el administrador
  const handleRecuperar = async (e) => {
    e.preventDefault();
    setError('');
    setInfo('');
    try {
      const r = await api.post('/auth/recuperar', { username: username.trim() });
      setInfo(r.data.mensaje);
    } catch (err) {
      setError(mensajeError(err));
    }
  };

  const cambiarModo = (m) => {
    setModo(m);
    setError('');
    setInfo('');
  };

  const estiloInput = { width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box' };
  const estiloBoton = { padding: '10px', backgroundColor: '#0056b3', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', marginTop: '10px', fontSize: '16px' };
  const estiloLink = { background: 'none', border: 'none', color: '#0056b3', cursor: 'pointer', fontSize: '13px' };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', backgroundColor: '#f4f4f4' }}>
      <div style={{ padding: '40px', backgroundColor: 'white', borderRadius: '8px', boxShadow: '0 4px 8px rgba(0,0,0,0.1)', width: '300px' }}>

        <h2 style={{ textAlign: 'center', marginBottom: '20px', color: '#333' }}>CIMSE</h2>
        <h4 style={{ textAlign: 'center', marginBottom: '20px', color: '#666' }}>Sistema de Gestión</h4>

        {bloqueo ? (
          /* PANTALLA BLOQUEADA: no se puede escribir usuario ni contraseña hasta que el administrador intervenga */
          <div style={{ textAlign: 'center' }} role="alert">
            <div style={{ fontSize: '48px', lineHeight: 1 }}>🔒</div>
            <h3 style={{ color: '#991b1b', margin: '10px 0' }}>Cuenta bloqueada</h3>
            <p style={{ color: '#991b1b', fontSize: '14px' }}>
              {bloqueo.mensaje || 'Se bloqueó después de 3 intentos fallidos.'}
            </p>
            <p style={{ color: '#666', fontSize: '13px' }}>
              Comuníquese con el administrador del sistema para que desbloquee su cuenta o le restablezca la contraseña.
            </p>
            <button type="button" style={estiloLink} onClick={liberarPantalla}>
              Ya me desbloquearon, intentar de nuevo
            </button>
          </div>
        ) : (
          <>
            {error && <p style={{ color: 'red', textAlign: 'center', fontSize: '14px' }}>{error}</p>}
            {info && <p style={{ color: '#166534', textAlign: 'center', fontSize: '14px' }}>{info}</p>}

            {modo === 'login' ? (
              <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                <div>
                  <label style={{ display: 'block', marginBottom: '5px', fontSize: '14px' }}>Usuario:</label>
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="Ej. admin"
                    autoComplete="username"
                    required
                    style={estiloInput}
                  />
                </div>

                <div>
                  <label style={{ display: 'block', marginBottom: '5px', fontSize: '14px' }}>Contraseña:</label>
                  <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="********"
                    autoComplete="current-password"
                    required
                    style={estiloInput}
                  />
                </div>

                <button type="submit" style={estiloBoton}>
                  Iniciar Sesión
                </button>
                <button type="button" style={estiloLink} onClick={() => cambiarModo('recuperar')}>
                  ¿Olvidó su contraseña?
                </button>
              </form>
            ) : (
              <form onSubmit={handleRecuperar} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                <p style={{ fontSize: '13px', color: '#666', margin: 0 }}>
                  Escriba su usuario. El administrador recibirá la solicitud y le enviará una clave temporal a su correo.
                </p>
                <div>
                  <label style={{ display: 'block', marginBottom: '5px', fontSize: '14px' }}>Usuario:</label>
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                    style={estiloInput}
                  />
                </div>
                <button type="submit" style={estiloBoton}>
                  Solicitar recuperación
                </button>
                <button type="button" style={estiloLink} onClick={() => cambiarModo('login')}>
                  Volver al inicio de sesión
                </button>
              </form>
            )}
          </>
        )}
      </div>
    </div>
  );
}
