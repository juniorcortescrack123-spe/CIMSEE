import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { CampoRtn, CampoTelefono } from "../../components/Campos.jsx";
import { ENLACE, ENLACES, esCorreo, esRtn, esTelefono, telefonoGuardado } from "../../formato.js";

const VACIO = { id: null, nombre: "", rtn: "", telefono: "", correo: "", direccion: "", cNombre: "", cCargo: "", cCelular: "", cCorreo: "" };
const ENLACE_VACIO = { nombre: "", cargo: "", telefonoCelular: "", correo: "" };

export default function Clientes() {
  const navigate = useNavigate();
  const [clientes, setClientes] = useState([]);
  const [form, setForm] = useState(VACIO);
  const [detalle, setDetalle] = useState(null);
  const [enlace, setEnlace] = useState(ENLACE_VACIO);
  const [sup, setSup] = useState({ asignados: [], disponibles: [] }); // supervisores a cargo del cliente
  const [idSupNuevo, setIdSupNuevo] = useState("");
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });
  const panelDetalle = useRef(null);

  const cargar = () =>
    api.get("/clientes").then((r) => setClientes(r.data)).catch((e) => avisar("err", mensajeError(e)));
  useEffect(() => { cargar(); }, []);

  const editando = form.id !== null;
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });
  const poner = (campo) => (valor) => setForm({ ...form, [campo]: valor });

  const guardar = async (e) => {
    e.preventDefault();
    if (!esRtn(form.rtn)) return avisar("err", "El RTN debe tener exactamente 14 dígitos.");
    if (!esTelefono(form.telefono)) return avisar("err", "El teléfono celular debe tener 8 dígitos (9999-9999).");
    if (!esCorreo(form.correo)) return avisar("err", "El correo del cliente no es válido.");
    const datos = { nombre: form.nombre, rtn: form.rtn, telefono: form.telefono, correo: form.correo, direccion: form.direccion };
    try {
      if (editando) {
        await api.put(`/clientes/${form.id}`, datos);
        avisar("ok", "Cliente actualizado.");
      } else {
        if (!esTelefono(form.cCelular)) return avisar("err", `El teléfono celular de la ${ENLACE.toLowerCase()} debe tener 8 dígitos (9999-9999).`);
        if (!esCorreo(form.cCorreo)) return avisar("err", `El correo de la ${ENLACE.toLowerCase()} no es válido.`);
        const contactos = [{ nombre: form.cNombre, cargo: form.cCargo, telefonoCelular: form.cCelular, correo: form.cCorreo }];
        await api.post("/clientes/registrar", { ...datos, contactos });
        avisar("ok", "Cliente registrado.");
      }
      setForm(VACIO);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const editar = (c) => {
    setForm({ ...VACIO, id: c.idCliente, nombre: c.nombre || "", rtn: c.rtn || "", telefono: telefonoGuardado(c.telefono), correo: c.correo || "", direccion: c.direccion || "" });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const cargarSupervisores = async (idCliente) => {
    try {
      setSup((await api.get(`/clientes/${idCliente}/supervisores`)).data);
      setIdSupNuevo("");
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const asignarSupervisor = async (e) => {
    e.preventDefault();
    try {
      await api.post(`/clientes/${detalle.id}/supervisores`, { idSupervisor: Number(idSupNuevo) });
      avisar("ok", "Supervisor asignado al cliente.");
      cargarSupervisores(detalle.id);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const quitarSupervisor = async (s) => {
    if (!window.confirm(`¿Quitar a ${s.nombre} como supervisor de este cliente? Ya no podrá hacer visitas a ${detalle.nombre}.`)) return;
    try {
      await api.delete(`/clientes/${detalle.id}/supervisores/${s.id}`);
      cargarSupervisores(detalle.id);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const verDetalle = async (idCliente) => {
    try {
      setDetalle((await api.get(`/clientes/${idCliente}/detalle`)).data);
      cargarSupervisores(idCliente);
      setEnlace(ENLACE_VACIO);
      setTimeout(() => panelDetalle.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const agregarEnlace = async (e) => {
    e.preventDefault();
    if (!esTelefono(enlace.telefonoCelular)) return avisar("err", "El teléfono celular debe tener 8 dígitos (9999-9999).");
    try {
      await api.post(`/clientes/${detalle.id}/contactos`, enlace);
      avisar("ok", `${ENLACE} agregada.`);
      verDetalle(detalle.id);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const quitarEnlace = async (c) => {
    if (!window.confirm(`¿Quitar a ${c.nombre} de las ${ENLACES.toLowerCase()}?`)) return;
    try {
      await api.delete(`/clientes/contactos/${c.id}`);
      verDetalle(detalle.id);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  return (
    <>
      <section className="card">
        <h1>Gestión de clientes</h1>
        <p className="muted">
          El cliente es una persona jurídica (con RTN) y puede tener varias {ENLACES.toLowerCase()} y varios contratos. Todos los campos son obligatorios.
          Un cliente no se elimina porque conserva su historia.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">{editando ? `Editar cliente #${form.id}` : "Registrar cliente"}</h2>
        <form onSubmit={guardar} className="form-col">
          <div className="form-grid">
            <div><label>Nombre o razón social *</label><input className="input" value={form.nombre} onChange={cambiar("nombre")} required maxLength={100} /></div>
            <div><label>RTN * (14 dígitos)</label><CampoRtn value={form.rtn} onChange={poner("rtn")} /></div>
            <div><label>Teléfono celular *</label><CampoTelefono value={form.telefono} onChange={poner("telefono")} /></div>
            <div><label>Correo *</label><input className="input" type="email" value={form.correo} onChange={cambiar("correo")} required maxLength={100} /></div>
            <div><label>Dirección *</label><input className="input" value={form.direccion} onChange={cambiar("direccion")} required maxLength={200} /></div>
          </div>
          {!editando && (
            <>
              <h3 style={{ marginBottom: 4 }}>Primera {ENLACE.toLowerCase()}</h3>
              <p className="muted" style={{ marginTop: 0 }}>Es la persona del cliente con quien se trata el servicio. Después puede agregar más desde el detalle del cliente.</p>
              <div className="form-grid">
                <div><label>Nombre *</label><input className="input" value={form.cNombre} onChange={cambiar("cNombre")} required maxLength={100} /></div>
                <div><label>Cargo *</label><input className="input" value={form.cCargo} onChange={cambiar("cCargo")} required maxLength={60} /></div>
                <div><label>Teléfono celular *</label><CampoTelefono value={form.cCelular} onChange={poner("cCelular")} /></div>
                <div><label>Correo *</label><input className="input" type="email" value={form.cCorreo} onChange={cambiar("cCorreo")} required maxLength={100} /></div>
              </div>
            </>
          )}
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button type="submit" className="btn btn-dark">{editando ? "GUARDAR CAMBIOS" : "REGISTRAR CLIENTE"}</button>
            {editando && <button type="button" className="btn btn-outline" onClick={() => setForm(VACIO)}>CANCELAR</button>}
          </div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Clientes registrados</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Nombre</th><th>RTN</th><th>Teléfono celular</th><th>Correo</th><th>Acciones</th></tr></thead>
            <tbody>
              {clientes.map((c) => (
                <tr key={c.idCliente}>
                  <td>{c.idCliente}</td><td><strong>{c.nombre}</strong></td><td>{c.rtn}</td><td>{telefonoGuardado(c.telefono)}</td><td>{c.correo}</td>
                  <td className="acciones">
                    <button className="btn btn-outline btn-sm" onClick={() => verDetalle(c.idCliente)}>Detalle</button>
                    <button className="btn btn-outline btn-sm" onClick={() => editar(c)}>Editar</button>
                    <button className="btn btn-dark btn-sm" onClick={() => navigate(`/comercial/clientes/${c.idCliente}/guardias`)}>Asignar guardias</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {clientes.length === 0 && <p className="muted">Aún no hay clientes registrados.</p>}
        </div>
      </section>

      {detalle && (
        <section className="card" style={{ marginTop: 16 }} ref={panelDetalle}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <h2 className="section-title" style={{ marginTop: 0 }}>Detalle: {detalle.nombre}</h2>
            <button className="btn btn-outline btn-sm" onClick={() => setDetalle(null)}>Cerrar</button>
          </div>
          <p className="muted">RTN {detalle.rtn || "—"} · {telefonoGuardado(detalle.telefono) || "sin teléfono"} · {detalle.correo || "sin correo"} · {detalle.direccion || "sin dirección"}</p>

          <h3>{ENLACES}</h3>
          {detalle.contactos.length === 0 ? <p className="muted">Sin {ENLACES.toLowerCase()} registradas.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Nombre</th><th>Cargo</th><th>Teléfono celular</th><th>Correo</th><th></th></tr></thead>
              <tbody>{detalle.contactos.map((k) => (
                <tr key={k.id}><td>{k.nombre}</td><td>{k.cargo || "—"}</td><td>{telefonoGuardado(k.telefonoCelular)}</td><td>{k.correo || "—"}</td>
                  <td><button className="btn btn-outline btn-sm" onClick={() => quitarEnlace(k)}>Quitar</button></td></tr>
              ))}</tbody>
            </table></div>
          )}
          <form onSubmit={agregarEnlace} className="form-grid" style={{ marginTop: 10 }}>
            <div><label>Nombre *</label><input className="input" value={enlace.nombre} onChange={(e) => setEnlace({ ...enlace, nombre: e.target.value })} required maxLength={100} /></div>
            <div><label>Cargo *</label><input className="input" value={enlace.cargo} onChange={(e) => setEnlace({ ...enlace, cargo: e.target.value })} required maxLength={60} /></div>
            <div><label>Teléfono celular *</label><CampoTelefono value={enlace.telefonoCelular} onChange={(v) => setEnlace({ ...enlace, telefonoCelular: v })} /></div>
            <div><label>Correo *</label><input className="input" type="email" value={enlace.correo} onChange={(e) => setEnlace({ ...enlace, correo: e.target.value })} required maxLength={100} /></div>
            <div style={{ alignSelf: "end" }}><button type="submit" className="btn btn-dark btn-sm">+ AGREGAR {ENLACE.toUpperCase()}</button></div>
          </form>

          <h3>Supervisores a cargo</h3>
          <p className="muted" style={{ marginTop: 0 }}>Solo los supervisores de esta lista pueden hacer visitas a este cliente y ver a sus guardias.</p>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            {sup.asignados.length === 0 && <span className="muted">Sin supervisor asignado.</span>}
            {sup.asignados.map((s) => (
              <span key={s.id} className="chip">{s.nombre} <button type="button" className="btn btn-outline btn-sm" style={{ marginLeft: 6 }} onClick={() => quitarSupervisor(s)}>Quitar</button></span>
            ))}
          </div>
          <form onSubmit={asignarSupervisor} style={{ display: "flex", gap: 8, marginTop: 8, maxWidth: 520 }}>
            <select className="input" value={idSupNuevo} onChange={(e) => setIdSupNuevo(e.target.value)} required>
              <option value="">— Elegir supervisor —</option>
              {sup.disponibles.map((s) => <option key={s.id} value={s.id}>{s.nombre}</option>)}
            </select>
            <button type="submit" className="btn btn-dark btn-sm" disabled={sup.disponibles.length === 0}>+ ASIGNAR SUPERVISOR</button>
          </form>

          <h3>Contratos</h3>
          {detalle.contratos.length === 0 ? <p className="muted">Sin contratos.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>#</th><th>Inicio</th><th>Vencimiento</th><th>Monto mensual</th><th>Estado</th></tr></thead>
              <tbody>{detalle.contratos.map((k) => (
                <tr key={k.id}><td>{k.id}</td><td>{k.fechaInicio}</td><td>{k.fechaVencimiento}</td><td>L {Number(k.montoMensual).toLocaleString()}</td><td>{k.estado}</td></tr>
              ))}</tbody>
            </table></div>
          )}

          <h3>Puestos y guardias asignados</h3>
          {detalle.puestos.length === 0 ? <p className="muted">Sin puestos. Use "Asignar guardias" para crear el puesto y asignar al personal.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Puesto</th><th>Ubicación</th><th>Turno</th><th>Estado</th><th>Guardias</th></tr></thead>
              <tbody>{detalle.puestos.map((p) => (
                <tr key={p.id}>
                  <td>{p.descripcion}</td><td>{p.ubicacion}</td><td>{p.tipoTurno}</td><td>{p.estado}</td>
                  <td>{p.guardias.length === 0 ? <span className="muted">Sin guardia</span> : p.guardias.map((g, i) => <span key={i} className="chip">{g.nombre} ({g.turno})</span>)}</td>
                </tr>
              ))}</tbody>
            </table></div>
          )}
        </section>
      )}
    </>
  );
}
