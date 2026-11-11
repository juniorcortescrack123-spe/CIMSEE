import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api.js";
import { actualizarSesion, obtenerSesion, mensajeError } from "../auth.js";

// Reglas de la política de contraseñas (se validan también en el servidor)
function reglas(clave) {
  return [
    { texto: "Mínimo 8 caracteres", ok: clave.length >= 8 },
    { texto: "Una letra mayúscula", ok: /[A-ZÁÉÍÓÚÑ]/.test(clave) },
    { texto: "Un número", ok: /\d/.test(clave) },
    { texto: "Un carácter especial (por ejemplo @ # $ !)", ok: /[^A-Za-zÁÉÍÓÚáéíóúÑñ0-9\s]/.test(clave) },
  ];
}

export default function CambiarClave() {
  const sesion = obtenerSesion();
  const navigate = useNavigate();
  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [confirmar, setConfirmar] = useState("");
  const [error, setError] = useState("");
  const [ok, setOk] = useState("");

  const enviar = async (e) => {
    e.preventDefault();
    setError("");
    setOk("");
    if (nueva !== confirmar) return setError("La confirmación no coincide con la nueva contraseña.");
    try {
      const r = await api.post("/me/clave", { actual, nueva });
      actualizarSesion({ debeCambiarClave: false });
      setOk(r.data.mensaje);
      setActual("");
      setNueva("");
      setConfirmar("");
      setTimeout(() => navigate("/"), 1200);
    } catch (err) {
      setError(mensajeError(err));
    }
  };

  return (
    <section className="card" style={{ maxWidth: 480 }}>
      <h1>Cambiar contraseña</h1>
      {sesion?.debeCambiarClave && (
        <p className="alerta alerta-info">Por seguridad debe cambiar su contraseña antes de continuar.</p>
      )}
      {error && <p className="alerta alerta-err">{error}</p>}
      {ok && <p className="alerta alerta-ok">{ok}</p>}

      <form onSubmit={enviar} className="form-col">
        <label>Contraseña actual</label>
        <input className="input" type="password" value={actual} onChange={(e) => setActual(e.target.value)} required />
        <label>Nueva contraseña</label>
        <input className="input" type="password" value={nueva} onChange={(e) => setNueva(e.target.value)} required />
        <ul className="reglas">
          {reglas(nueva).map((r) => (
            <li key={r.texto} className={r.ok ? "regla-ok" : ""}>{r.ok ? "✔" : "○"} {r.texto}</li>
          ))}
        </ul>
        <label>Confirmar nueva contraseña</label>
        <input className="input" type="password" value={confirmar} onChange={(e) => setConfirmar(e.target.value)} required />
        <button type="submit" className="btn btn-dark" style={{ marginTop: 8 }}>GUARDAR CONTRASEÑA</button>
      </form>
    </section>
  );
}
