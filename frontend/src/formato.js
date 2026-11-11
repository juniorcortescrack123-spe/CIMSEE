// Formato de los campos numericos (revision del 27 de octubre).
// Cada funcion recibe lo que escribio la persona y devuelve el texto que debe mostrar el campo.

// Nombre que se muestra en pantalla para las personas que atienden al cliente.
// Se usa "persona de enlace" y no "contacto" porque "contacto" se confunde con un numero de telefono.
// Para cambiarlo en todo el sistema basta con editar estas dos lineas.
export const ENLACE = "Persona de enlace";
export const ENLACES = "Personas de enlace";

const soloDigitos = (v) => String(v ?? "").replace(/\D/g, "");

// Identidad: solo numeros, maximo 13. Al llegar a 13 ya no deja escribir mas.
export const formatearIdentidad = (v) => soloDigitos(v).slice(0, 13);

// RTN: solo numeros, maximo 14.
export const formatearRtn = (v) => soloDigitos(v).slice(0, 14);

// Telefono: 8 digitos con el guion despues de los 4 primeros (9999-9999).
// El guion aparece solo al escribir el cuarto digito y desaparece solo al borrar.
export function formatearTelefono(nuevo, anterior = "") {
  const d = soloDigitos(nuevo).slice(0, 8);
  if (d.length > 4) return d.slice(0, 4) + "-" + d.slice(4);
  if (d.length === 4 && String(nuevo).length > String(anterior).length) return d + "-";
  return d;
}

// Telefonos que ya vienen de la base de datos (con o sin guion) para mostrarlos igual en pantalla
export const telefonoGuardado = (v) => {
  const d = soloDigitos(v).slice(0, 8);
  return d.length > 4 ? d.slice(0, 4) + "-" + d.slice(4) : d;
};

export const esTelefono = (v) => /^\d{4}-\d{4}$/.test(String(v ?? ""));
export const esIdentidad = (v) => /^\d{13}$/.test(String(v ?? ""));
export const esRtn = (v) => /^\d{14}$/.test(String(v ?? ""));
export const esCorreo = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(v ?? "").trim());

// Fecha de hoy (AAAA-MM-DD) y la fecha limite para ser mayor de 18 anios
export const hoy = () => new Date().toISOString().slice(0, 10);
export const hace18Anios = () => {
  const d = new Date();
  d.setFullYear(d.getFullYear() - 18);
  return d.toISOString().slice(0, 10);
};

// Tipos de incidencia (se usan en Incidencias y en las Visitas de supervision)
export const TIPOS_INCIDENCIA = {
  AUSENCIA: "Ausencia", LLEGADA_TARDIA: "Llegada tardía", PROBLEMA_CLIENTE: "Problema con el cliente",
  INCIDENTE_SEGURIDAD: "Incidente de seguridad", DANO_PROPIEDAD: "Daño a la propiedad (vidrio roto, etc.)",
  PERDIDA_ROBO_EQUIPO: "Pérdida o robo de equipo / arma", ASALTO: "Asalto", OTRO: "Otro",
};
