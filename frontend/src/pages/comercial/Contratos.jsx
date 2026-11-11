import { useEffect, useRef, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

export const COLOR = { VERDE: "badge-ok", AMARILLO: "badge-sis", ROJO: "badge-rojo", VENCIDO: "badge-rojo", RENOVADO: "badge-off", VIGENTE: "badge-ok" };

const VACIO = { id: null, idCliente: "", fechaInicio: "", fechaVencimiento: "", montoMensual: "", diaPago: "15", cai: "", observaciones: "", estado: "VIGENTE" };
const fecha = (v) => (v ? String(v).replace("T", " ").slice(0, 16) : "—");

export default function Contratos() {
  const [contratos, setContratos] = useState([]);
  const [clientes, setClientes] = useState([]);
  const [form, setForm] = useState(VACIO);
  const [detalle, setDetalle] = useState(null); // { contrato, documentos }
  const [archivo, setArchivo] = useState(null);
  const [pdfNuevo, setPdfNuevo] = useState(null); // PDF que se adjunta al registrar el contrato
  const [claveArchivo, setClaveArchivo] = useState(0); // para limpiar el campo de archivo
  const panelDetalle = useRef(null);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () => {
    api.get("/contratos").then((r) => setContratos(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/contratos/clientes").then((r) => setClientes(r.data)).catch(() => {});
  };
  useEffect(() => { cargar(); }, []);

  const editando = form.id !== null;
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  const guardar = async (e) => {
    e.preventDefault();
    const datos = {
      idCliente: Number(form.idCliente), fechaInicio: form.fechaInicio, fechaVencimiento: form.fechaVencimiento,
      montoMensual: form.montoMensual, diaPago: Number(form.diaPago), cai: form.cai, observaciones: form.observaciones, estado: form.estado,
    };
    if (!editando && !pdfNuevo) return avisar("err", "Adjunte el PDF del contrato escaneado.");
    try {
      if (editando) {
        await api.put(`/contratos/${form.id}`, datos);
        avisar("ok", "Contrato actualizado.");
      } else {
        const r = await api.post("/contratos", datos);
        try {
          const archivoPdf = new FormData();
          archivoPdf.append("archivo", pdfNuevo);
          await api.post(`/contratos/${r.data.id}/documentos`, archivoPdf);
          avisar("ok", "Contrato registrado y PDF guardado en el servidor.");
        } catch (errPdf) {
          avisar("err", "El contrato se registró, pero el PDF no se pudo guardar: " + mensajeError(errPdf) + " Súbalo desde el Detalle del contrato.");
        }
      }
      setForm(VACIO);
      setPdfNuevo(null);
      setClaveArchivo((n) => n + 1);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const editar = (c) => {
    setForm({
      id: c.id, idCliente: String(c.idCliente), fechaInicio: String(c.fechaInicio), fechaVencimiento: String(c.fechaVencimiento),
      montoMensual: String(c.montoMensual), diaPago: String(c.diaPago), cai: c.cai || "", observaciones: c.observaciones || "", estado: c.estado,
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const renovar = async (c) => {
    const f = window.prompt(`Nueva fecha de vencimiento para el contrato #${c.id} (AAAA-MM-DD). Debe ser posterior a ${c.fechaVencimiento}:`);
    if (!f) return;
    try {
      const r = await api.post(`/contratos/${c.id}/renovar`, { fechaVencimiento: f });
      avisar("ok", r.data.mensaje);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const verDetalle = async (id) => {
    try {
      const [c, d] = await Promise.all([api.get(`/contratos/${id}`), api.get(`/contratos/${id}/documentos`)]);
      setDetalle({ contrato: c.data, documentos: d.data });
      setArchivo(null);
      setTimeout(() => panelDetalle.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const subir = async (e) => {
    e.preventDefault();
    if (!archivo) return avisar("err", "Seleccione el PDF del contrato escaneado.");
    const datos = new FormData();
    datos.append("archivo", archivo);
    try {
      const r = await api.post(`/contratos/${detalle.contrato.id}/documentos`, datos);
      avisar("ok", r.data.mensaje);
      verDetalle(detalle.contrato.id);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  // el PDF se pide con el token y se abre en una pestaña nueva
  const abrirPdf = async (d) => {
    try {
      const r = await api.get(`/contratos/documentos/${d.id}`, { responseType: "blob" });
      window.open(URL.createObjectURL(new Blob([r.data], { type: "application/pdf" })), "_blank");
    } catch (err) {
      avisar("err", "No se pudo abrir el documento.");
    }
  };

  const c = detalle?.contrato;

  return (
    <>
      <section className="card">
        <h1>Gestión de contratos</h1>
        <p className="muted">Un cliente puede tener varios contratos. Contratos de 1 o 2 años; el semáforo avisa cuándo vencen. Todos los campos son obligatorios; el contrato escaneado (PDF) se guarda en una carpeta fija del servidor.</p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">{editando ? `Editar contrato #${form.id}` : "Registrar contrato"}</h2>
        <form onSubmit={guardar} className="form-col">
          <div className="form-grid">
            <div>
              <label>Cliente *</label>
              <select className="input" value={form.idCliente} onChange={cambiar("idCliente")} required>
                <option value="">— Seleccione —</option>
                {clientes.map((k) => <option key={k.idCliente} value={k.idCliente}>{k.nombre}</option>)}
              </select>
            </div>
            <div><label>Fecha de inicio *</label><input className="input" type="date" value={form.fechaInicio} onChange={cambiar("fechaInicio")} required /></div>
            <div><label>Fecha de vencimiento *</label><input className="input" type="date" value={form.fechaVencimiento} onChange={cambiar("fechaVencimiento")} required /></div>
            <div><label>Monto mensual (L) *</label><input className="input" type="number" min="0" step="0.01" value={form.montoMensual} onChange={cambiar("montoMensual")} required /></div>
            <div>
              <label>Día de pago *</label>
              <select className="input" value={form.diaPago} onChange={cambiar("diaPago")}><option value="15">15</option><option value="30">30</option></select>
            </div>
            <div><label>CAI de factura *</label><input className="input" value={form.cai} onChange={cambiar("cai")} required maxLength={60} /></div>
            {editando && (
              <div>
                <label>Estado</label>
                <select className="input" value={form.estado} onChange={cambiar("estado")}><option>VIGENTE</option><option>VENCIDO</option><option>RENOVADO</option></select>
              </div>
            )}
            <div style={{ gridColumn: "1 / -1" }}><label>Observaciones *</label><textarea className="input" rows={4} value={form.observaciones} onChange={cambiar("observaciones")} required maxLength={200} placeholder="Si no hay, escriba: Sin observaciones" /></div>
            {!editando && (
              <div>
                <label>Contrato escaneado (PDF) *</label>
                <input key={claveArchivo} className="input" type="file" accept="application/pdf,.pdf" required onChange={(e) => setPdfNuevo(e.target.files[0] || null)} />
              </div>
            )}
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button type="submit" className="btn btn-dark">{editando ? "GUARDAR CAMBIOS" : "REGISTRAR CONTRATO"}</button>
            {editando && <button type="button" className="btn btn-outline" onClick={() => setForm(VACIO)}>CANCELAR</button>}
          </div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Contratos</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Cliente</th><th>Inicio</th><th>Vencimiento</th><th>Días</th><th>Monto mensual</th><th>Puestos</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              {contratos.map((k) => (
                <tr key={k.id}>
                  <td>{k.id}</td><td><strong>{k.cliente}</strong></td><td>{String(k.fechaInicio)}</td><td>{String(k.fechaVencimiento)}</td>
                  <td>{k.estado === "VIGENTE" ? k.diasRestantes : "—"}</td><td>L {Number(k.montoMensual).toLocaleString()}</td>
                  <td>{Number(k.puestos) === 0 ? <span className="badge badge-sis">Sin puestos</span> : k.puestos}</td>
                  <td><span className={"badge " + (COLOR[k.semaforo] || "badge-off")}>{k.semaforo}</span></td>
                  <td className="acciones">
                    <button className="btn btn-outline btn-sm" onClick={() => verDetalle(k.id)}>Detalle</button>
                    <button className="btn btn-outline btn-sm" onClick={() => editar(k)}>Editar</button>
                    {k.estado === "VIGENTE" && <button className="btn btn-outline btn-sm" onClick={() => renovar(k)}>Renovar</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {contratos.length === 0 && <p className="muted">Aún no hay contratos registrados.</p>}
        </div>
      </section>

      {c && (
        <section className="card" style={{ marginTop: 16 }} ref={panelDetalle}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <h2 className="section-title" style={{ marginTop: 0 }}>Contrato #{c.id} — {c.cliente}</h2>
            <button className="btn btn-outline btn-sm" onClick={() => setDetalle(null)}>Cerrar</button>
          </div>
          <div className="table-wrap"><table><tbody>
            <tr><th>Vigencia</th><td>{String(c.fechaInicio)} al {String(c.fechaVencimiento)} ({c.duracionAnios} año/s)</td></tr>
            <tr><th>Monto mensual</th><td>L {Number(c.montoMensual).toLocaleString()} — día de pago {c.diaPago}</td></tr>
            <tr><th>CAI de factura</th><td>{c.cai || "—"}</td></tr>
            <tr><th>Estado</th><td><span className={"badge " + (COLOR[c.semaforo] || "badge-off")}>{c.semaforo}</span> {c.estado === "VIGENTE" ? `(${c.diasRestantes} días)` : ""}</td></tr>
            <tr><th>Observaciones</th><td>{c.observaciones || "—"}</td></tr>
          </tbody></table></div>

          <h3>Contrato escaneado (PDF)</h3>
          {detalle.documentos.length === 0 ? <p className="muted">Todavía no se ha subido el contrato escaneado.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Archivo</th><th>Subido</th><th>Por</th><th>Tamaño</th><th></th></tr></thead>
              <tbody>{detalle.documentos.map((d) => (
                <tr key={d.id}><td>{d.nombre}</td><td>{fecha(d.fecha)}</td><td>{d.subio || "—"}</td><td>{Math.round(Number(d.tamanio) / 1024)} KB</td>
                  <td><button className="btn btn-dark btn-sm" onClick={() => abrirPdf(d)}>Ver PDF</button></td></tr>
              ))}</tbody>
            </table></div>
          )}
          <form onSubmit={subir} style={{ display: "flex", gap: 8, alignItems: "center", marginTop: 10, flexWrap: "wrap" }}>
            <input type="file" accept="application/pdf,.pdf" onChange={(e) => setArchivo(e.target.files[0] || null)} />
            <button type="submit" className="btn btn-dark btn-sm">SUBIR CONTRATO ESCANEADO</button>
            <span className="muted">Solo PDF, máximo 10 MB.</span>
          </form>
        </section>
      )}
    </>
  );
}
