import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { hoy } from "../../formato.js";

const TIPOS = { ALCALDIA: "Alcaldía", ARMAS: "Armería (armas)", OPERACION: "Operación", OTRO: "Otro" };
const COLOR = { VERDE: "badge-ok", AMARILLO: "badge-sis", ROJO: "badge-rojo", VENCIDO: "badge-rojo" };
const VACIO = { id: null, tipo: "", numero: "", fechaEmision: "", fechaVencimiento: "" };

export default function PermisosLegales() {
  const [filas, setFilas] = useState([]);
  const [tipo, setTipo] = useState("");
  const [porVencer, setPorVencer] = useState(false);
  const [form, setForm] = useState(VACIO);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (t, texto) => setMsg({ tipo: t, texto });

  const cargar = () => {
    const pedido = porVencer ? api.get("/permisos-legales/alertas", { params: { dias: 30 } }) : api.get("/permisos-legales", { params: tipo ? { tipo } : {} });
    pedido.then((r) => setFilas(tipo && porVencer ? r.data.filter((f) => f.tipo === tipo) : r.data)).catch((e) => avisar("err", mensajeError(e)));
  };
  useEffect(() => { cargar(); }, [tipo, porVencer]);

  const editando = form.id !== null;
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  const guardar = async (e) => {
    e.preventDefault();
    const datos = { tipo: form.tipo, numero: form.numero, fechaEmision: form.fechaEmision, fechaVencimiento: form.fechaVencimiento };
    try {
      if (editando) {
        await api.put(`/permisos-legales/${form.id}`, datos);
        avisar("ok", "Permiso actualizado.");
      } else {
        await api.post("/permisos-legales", datos);
        avisar("ok", "Permiso registrado.");
      }
      setForm(VACIO);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const editar = (p) => {
    setForm({ id: p.id, tipo: p.tipo, numero: p.numero, fechaEmision: String(p.fechaEmision).slice(0, 10), fechaVencimiento: String(p.fechaVencimiento).slice(0, 10) });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const renovar = async (p) => {
    const fecha = window.prompt(`Nueva fecha de vencimiento del permiso ${p.numero} (AAAA-MM-DD). Debe ser posterior a ${String(p.fechaVencimiento).slice(0, 10)}:`);
    if (!fecha) return;
    try {
      const r = await api.post(`/permisos-legales/${p.id}/renovar`, { fechaVencimiento: fecha.trim() });
      avisar("ok", r.data.mensaje);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  return (
    <>
      <section className="card">
        <h1>Permisos legales</h1>
        <p className="muted">
          Permisos de la alcaldía, de armería y de operación. El semáforo avisa con 30 días de anticipación:{" "}
          <span className="badge badge-ok">VERDE</span> más de 30 días · <span className="badge badge-sis">AMARILLO</span> 30 días o menos ·{" "}
          <span className="badge badge-rojo">ROJO</span> 7 días o menos · <span className="badge badge-rojo">VENCIDO</span> ya venció.
          Todos los campos son obligatorios. Un permiso no se elimina: cuando se renueva se actualiza su fecha.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">{editando ? `Editar permiso #${form.id}` : "Registrar permiso"}</h2>
        <form onSubmit={guardar} className="form-col">
          <div className="form-grid">
            <div>
              <label>Tipo de permiso *</label>
              <select className="input" value={form.tipo} onChange={cambiar("tipo")} required>
                <option value="">— Seleccione —</option>
                {Object.entries(TIPOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <div><label>Número de permiso *</label><input className="input" value={form.numero} onChange={cambiar("numero")} required maxLength={50} /></div>
            <div><label>Fecha de emisión *</label><input className="input" type="date" max={hoy()} value={form.fechaEmision} onChange={cambiar("fechaEmision")} required /></div>
            <div><label>Fecha de vencimiento *</label><input className="input" type="date" min={form.fechaEmision || undefined} value={form.fechaVencimiento} onChange={cambiar("fechaVencimiento")} required /></div>
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button type="submit" className="btn btn-dark">{editando ? "GUARDAR CAMBIOS" : "REGISTRAR PERMISO"}</button>
            {editando && <button type="button" className="btn btn-outline" onClick={() => setForm(VACIO)}>CANCELAR</button>}
          </div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Permisos registrados</h2>
        <div className="filtros" style={{ gridTemplateColumns: "220px auto", marginBottom: 10, alignItems: "center" }}>
          <select className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="">Todos los tipos</option>
            {Object.entries(TIPOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
            <input type="checkbox" checked={porVencer} onChange={(e) => setPorVencer(e.target.checked)} /> Solo los que vencen en 30 días o menos
          </label>
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Tipo</th><th>Número</th><th>Emisión</th><th>Vencimiento</th><th>Días restantes</th><th>Semáforo</th><th>Acciones</th></tr></thead>
            <tbody>
              {filas.map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td><td>{TIPOS[p.tipo] || p.tipo}</td><td><strong>{p.numero}</strong></td>
                  <td>{String(p.fechaEmision)}</td><td>{String(p.fechaVencimiento)}</td><td>{p.diasRestantes}</td>
                  <td><span className={"badge " + (COLOR[p.semaforo] || "badge-off")}>{p.semaforo}</span></td>
                  <td className="acciones">
                    <button className="btn btn-dark btn-sm" onClick={() => renovar(p)}>Renovar</button>
                    <button className="btn btn-outline btn-sm" onClick={() => editar(p)}>Editar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filas.length === 0 && <p className="muted">No hay permisos que mostrar.</p>}
        </div>
      </section>
    </>
  );
}
