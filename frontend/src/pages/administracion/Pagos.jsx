import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { hoy } from "../../formato.js";

const CATEGORIAS = { RAP: "RAP", IHSS: "IHSS", CANON: "Canon", ALCALDIA: "Alcaldía", OTRO: "Otro" };
const COLOR = { VERDE: "badge-ok", AMARILLO: "badge-sis", ROJO: "badge-rojo", VENCIDO: "badge-rojo", PAGADO: "badge-ok" };
const VACIO = { id: null, concepto: "", categoria: "", idPermiso: "", fechaVencimiento: "", monto: "", observaciones: "" };
const L = (n) => "L " + Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export default function Pagos() {
  const [filas, setFilas] = useState([]);
  const [permisos, setPermisos] = useState([]);
  const [filtro, setFiltro] = useState({ estado: "", categoria: "" });
  const [form, setForm] = useState(VACIO);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (t, texto) => setMsg({ tipo: t, texto });

  const cargar = () => {
    const params = {};
    if (filtro.estado) params.estado = filtro.estado;
    if (filtro.categoria) params.categoria = filtro.categoria;
    api.get("/pagos", { params }).then((r) => setFilas(r.data)).catch((e) => avisar("err", mensajeError(e)));
  };
  useEffect(() => { api.get("/pagos/permisos").then((r) => setPermisos(r.data)).catch(() => {}); }, []);
  useEffect(() => { cargar(); }, [filtro]);

  const editando = form.id !== null;
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  const guardar = async (e) => {
    e.preventDefault();
    const datos = { concepto: form.concepto, categoria: form.categoria, idPermiso: form.idPermiso || null, fechaVencimiento: form.fechaVencimiento, monto: form.monto, observaciones: form.observaciones };
    try {
      if (editando) {
        await api.put(`/pagos/${form.id}`, datos);
        avisar("ok", "Pago actualizado.");
      } else {
        await api.post("/pagos", datos);
        avisar("ok", "Pago registrado.");
      }
      setForm(VACIO);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const editar = (p) => {
    setForm({ id: p.id, concepto: p.concepto, categoria: p.categoria, idPermiso: p.idPermiso || "", fechaVencimiento: String(p.fechaVencimiento).slice(0, 10), monto: p.monto, observaciones: p.observaciones || "" });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const pagar = async (p) => {
    const fecha = window.prompt(`Fecha en que se pagó "${p.concepto}" por ${L(p.monto)} (AAAA-MM-DD):`, hoy());
    if (!fecha) return;
    try {
      const r = await api.put(`/pagos/${p.id}/pagar`, { fechaPago: fecha.trim() });
      avisar("ok", r.data.mensaje);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const pendiente = filas.filter((f) => f.estado !== "PAGADO").reduce((s, f) => s + Number(f.monto), 0);

  return (
    <>
      <section className="card">
        <h1>Pagos administrativos</h1>
        <p className="muted">
          Obligaciones de la empresa con fecha límite (RAP, IHSS, canon, alcaldía y otros). Un pago registrado no se elimina: se marca como pagado.
          Todos los campos son obligatorios, menos el permiso legal, que solo se elige cuando el pago corresponde a un permiso.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">{editando ? `Editar pago #${form.id}` : "Registrar pago"}</h2>
        <form onSubmit={guardar} className="form-col">
          <div className="form-grid">
            <div><label>Concepto *</label><input className="input" value={form.concepto} onChange={cambiar("concepto")} required maxLength={100} placeholder="Ej. IHSS noviembre" /></div>
            <div>
              <label>Categoría *</label>
              <select className="input" value={form.categoria} onChange={cambiar("categoria")} required>
                <option value="">— Seleccione —</option>
                {Object.entries(CATEGORIAS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <div><label>Fecha límite de pago *</label><input className="input" type="date" value={form.fechaVencimiento} onChange={cambiar("fechaVencimiento")} required /></div>
            <div><label>Monto (L) *</label><input className="input" type="number" min="0.01" step="0.01" value={form.monto} onChange={cambiar("monto")} required /></div>
            <div>
              <label>Permiso legal relacionado (solo si aplica)</label>
              <select className="input" value={form.idPermiso} onChange={cambiar("idPermiso")}>
                <option value="">— No aplica —</option>
                {permisos.map((p) => <option key={p.id} value={p.id}>{p.tipo} {p.numero}</option>)}
              </select>
            </div>
            <div style={{ gridColumn: "1 / -1" }}><label>Observaciones *</label><textarea className="input" rows={4} value={form.observaciones} onChange={cambiar("observaciones")} required maxLength={200} placeholder="Si no hay, escriba: Sin observaciones" /></div>
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button type="submit" className="btn btn-dark">{editando ? "GUARDAR CAMBIOS" : "REGISTRAR PAGO"}</button>
            {editando && <button type="button" className="btn btn-outline" onClick={() => setForm(VACIO)}>CANCELAR</button>}
          </div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Pagos registrados</h2>
        <div className="filtros" style={{ gridTemplateColumns: "200px 200px", marginBottom: 10 }}>
          <select className="input" value={filtro.estado} onChange={(e) => setFiltro({ ...filtro, estado: e.target.value })}>
            <option value="">Todos los estados</option>
            <option value="PENDIENTE">Pendientes</option>
            <option value="VENCIDO">Vencidos</option>
            <option value="PAGADO">Pagados</option>
          </select>
          <select className="input" value={filtro.categoria} onChange={(e) => setFiltro({ ...filtro, categoria: e.target.value })}>
            <option value="">Todas las categorías</option>
            {Object.entries(CATEGORIAS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <p className="muted">Por pagar en esta lista: <strong>{L(pendiente)}</strong></p>
        <div className="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Concepto</th><th>Categoría</th><th>Permiso</th><th>Fecha límite</th><th>Monto</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              {filas.map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td><td><strong>{p.concepto}</strong></td><td>{CATEGORIAS[p.categoria] || p.categoria}</td><td>{p.permiso || "—"}</td>
                  <td>{String(p.fechaVencimiento)}{p.estado === "PAGADO" && p.fechaPago ? <div className="muted">pagado el {String(p.fechaPago)}</div> : null}</td>
                  <td>{L(p.monto)}</td>
                  <td><span className={"badge " + (COLOR[p.semaforo] || "badge-off")}>{p.semaforo === "PAGADO" ? "PAGADO" : p.semaforo}</span></td>
                  <td className="acciones">
                    {p.estado !== "PAGADO" && <button className="btn btn-dark btn-sm" onClick={() => pagar(p)}>Registrar pago</button>}
                    {p.estado !== "PAGADO" && <button className="btn btn-outline btn-sm" onClick={() => editar(p)}>Editar</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filas.length === 0 && <p className="muted">No hay pagos que mostrar.</p>}
        </div>
      </section>
    </>
  );
}
