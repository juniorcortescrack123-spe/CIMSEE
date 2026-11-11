import { useEffect, useRef, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { CampoRtn, CampoTelefono } from "../../components/Campos.jsx";
import { esCorreo, esRtn, esTelefono, telefonoGuardado } from "../../formato.js";

const TIPOS = ["Uniformes", "Armamento", "Vehículos", "Papelería", "Materiales", "Servicios", "Otro"];
const VACIO = { id: null, tipo: "", nombre: "", rtn: "", telefono: "", correo: "", direccion: "", personaEnlace: "" };
const COLOR_PAGO = { PAGADO: "badge-ok", PENDIENTE: "badge-sis" };

export default function Proveedores() {
  const [lista, setLista] = useState([]);
  const [q, setQ] = useState("");
  const [form, setForm] = useState(VACIO);
  const [detalle, setDetalle] = useState(null);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });
  const panel = useRef(null);

  const cargar = () =>
    api.get("/proveedores", { params: q ? { q } : {} }).then((r) => setLista(r.data)).catch((e) => avisar("err", mensajeError(e)));
  useEffect(() => { cargar(); }, [q]);

  const editando = form.id !== null;
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });
  const poner = (campo) => (valor) => setForm({ ...form, [campo]: valor });

  const guardar = async (e) => {
    e.preventDefault();
    if (!esRtn(form.rtn)) return avisar("err", "El RTN debe tener exactamente 14 dígitos.");
    if (!esTelefono(form.telefono)) return avisar("err", "El teléfono celular debe tener 8 dígitos (9999-9999).");
    if (!esCorreo(form.correo)) return avisar("err", "El correo no es válido.");
    const datos = { tipo: form.tipo, nombre: form.nombre, rtn: form.rtn, telefono: form.telefono, correo: form.correo, direccion: form.direccion, personaEnlace: form.personaEnlace };
    try {
      if (editando) {
        await api.put(`/proveedores/${form.id}`, datos);
        avisar("ok", "Proveedor actualizado.");
      } else {
        await api.post("/proveedores", datos);
        avisar("ok", "Proveedor registrado.");
      }
      setForm(VACIO);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const editar = (p) => {
    setForm({ id: p.id, tipo: p.tipo || "", nombre: p.nombre || "", rtn: p.rtn || "", telefono: telefonoGuardado(p.telefono), correo: p.correo || "", direccion: p.direccion || "", personaEnlace: p.personaEnlace || "" });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const verDetalle = async (id) => {
    try {
      setDetalle((await api.get(`/proveedores/${id}`)).data);
      setTimeout(() => panel.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const tipos = form.tipo && !TIPOS.includes(form.tipo) ? [form.tipo, ...TIPOS] : TIPOS;

  return (
    <>
      <section className="card">
        <h1>Proveedores</h1>
        <p className="muted">
          Registro de los proveedores de uniformes, armamento, vehículos, papelería y otros insumos. Todos los campos son obligatorios.
          Un proveedor con compras no se elimina porque conserva su historia.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">{editando ? `Editar proveedor #${form.id}` : "Registrar proveedor"}</h2>
        <form onSubmit={guardar} className="form-col">
          <div className="form-grid">
            <div><label>Nombre o razón social *</label><input className="input" value={form.nombre} onChange={cambiar("nombre")} required maxLength={100} /></div>
            <div>
              <label>Tipo de proveedor *</label>
              <select className="input" value={form.tipo} onChange={cambiar("tipo")} required>
                <option value="">— Seleccione —</option>
                {tipos.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <div><label>RTN * (14 dígitos)</label><CampoRtn value={form.rtn} onChange={poner("rtn")} /></div>
            <div><label>Teléfono celular *</label><CampoTelefono value={form.telefono} onChange={poner("telefono")} /></div>
            <div><label>Correo *</label><input className="input" type="email" value={form.correo} onChange={cambiar("correo")} required maxLength={100} /></div>
            <div><label>Dirección *</label><input className="input" value={form.direccion} onChange={cambiar("direccion")} required maxLength={200} /></div>
            <div><label>Persona de enlace *</label><input className="input" value={form.personaEnlace} onChange={cambiar("personaEnlace")} required maxLength={100} placeholder="Quien nos atiende en el proveedor" /></div>
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button type="submit" className="btn btn-dark">{editando ? "GUARDAR CAMBIOS" : "REGISTRAR PROVEEDOR"}</button>
            {editando && <button type="button" className="btn btn-outline" onClick={() => setForm(VACIO)}>CANCELAR</button>}
          </div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Proveedores registrados</h2>
        <div className="filtros" style={{ gridTemplateColumns: "320px", marginBottom: 10 }}>
          <input className="input" placeholder="Buscar por nombre, RTN o tipo" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Nombre</th><th>Tipo</th><th>RTN</th><th>Teléfono celular</th><th>Persona de enlace</th><th>Compras</th><th>Acciones</th></tr></thead>
            <tbody>
              {lista.map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td><td><strong>{p.nombre}</strong></td><td>{p.tipo}</td><td>{p.rtn}</td>
                  <td>{telefonoGuardado(p.telefono)}</td><td>{p.personaEnlace}</td><td>{p.compras}</td>
                  <td className="acciones">
                    <button className="btn btn-outline btn-sm" onClick={() => verDetalle(p.id)}>Detalle</button>
                    <button className="btn btn-outline btn-sm" onClick={() => editar(p)}>Editar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {lista.length === 0 && <p className="muted">No hay proveedores que coincidan.</p>}
        </div>
      </section>

      {detalle && (
        <section className="card" style={{ marginTop: 16 }} ref={panel}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <h2 className="section-title" style={{ marginTop: 0 }}>Detalle: {detalle.nombre}</h2>
            <button className="btn btn-outline btn-sm" onClick={() => setDetalle(null)}>Cerrar</button>
          </div>
          <p className="muted">
            {detalle.tipo} · RTN {detalle.rtn} · {telefonoGuardado(detalle.telefono)} · {detalle.correo} · {detalle.direccion} · Persona de enlace: {detalle.personaEnlace}
          </p>
          <h3>Compras a este proveedor</h3>
          {detalle.listaCompras.length === 0 ? <p className="muted">Sin compras registradas.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Factura</th><th>Categoría</th><th>Fecha</th><th>Monto</th><th>Pago</th></tr></thead>
              <tbody>{detalle.listaCompras.map((c) => (
                <tr key={c.id}><td>{c.factura}</td><td>{c.categoria}</td><td>{String(c.fecha)}</td><td>L {Number(c.monto).toLocaleString()}</td>
                  <td><span className={"badge " + (COLOR_PAGO[c.estadoPago] || "badge-off")}>{c.estadoPago}</span></td></tr>
              ))}</tbody>
            </table></div>
          )}
        </section>
      )}
    </>
  );
}
