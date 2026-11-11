import { formatearIdentidad, formatearRtn, formatearTelefono } from "../formato.js";

// Campos con formato automatico. Todos reciben value y onChange(valorYaFormateado) y son obligatorios por defecto.

export function CampoTelefono({ value, onChange, required = true, ...resto }) {
  return (
    <input
      className="input"
      type="tel"
      inputMode="numeric"
      autoComplete="off"
      placeholder="9999-9999"
      maxLength={9}
      pattern="\d{4}-\d{4}"
      title="8 dígitos, con el guion después de los primeros 4 (9999-9999)"
      value={value}
      required={required}
      onChange={(e) => onChange(formatearTelefono(e.target.value, value))}
      {...resto}
    />
  );
}

export function CampoIdentidad({ value, onChange, required = true, ...resto }) {
  return (
    <input
      className="input"
      type="text"
      inputMode="numeric"
      autoComplete="off"
      placeholder="13 dígitos"
      maxLength={13}
      pattern="\d{13}"
      title="Exactamente 13 dígitos, sin guiones"
      value={value}
      required={required}
      onChange={(e) => onChange(formatearIdentidad(e.target.value))}
      {...resto}
    />
  );
}

export function CampoRtn({ value, onChange, required = true, ...resto }) {
  return (
    <input
      className="input"
      type="text"
      inputMode="numeric"
      autoComplete="off"
      placeholder="14 dígitos"
      maxLength={14}
      pattern="\d{14}"
      title="Exactamente 14 dígitos, sin guiones"
      value={value}
      required={required}
      onChange={(e) => onChange(formatearRtn(e.target.value))}
      {...resto}
    />
  );
}
