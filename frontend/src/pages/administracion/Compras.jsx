import { useEffect, useRef, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { hoy } from "../../formato.js";

const CATEGORIAS = ["UNIFORMES", "ARMAS", "VEHICULOS", "PAPELERIA", "MATERIALES"];
const UNIDADES = ["UNIDAD", "CAJA", "PAR", "RESMA", "LIBRA", "LITRO", "METRO", "OTRO"];
const LINEA = { descripcion: "", cantidad: "1", unidad: "UNIDAD", precio: "" };
const COLOR_PAGO = { PAGADO: "badge-ok", PENDIENTE: "badge-sis" };
const L = (n) => "L " + Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export default function Compras() {
  const [proveedores, setProveedores] = useState([]);
  const [compras, setCompras] = useState([]);
  const [existencias, setExistencias] = useState([]);
  const [filtro, setFiltro] = useState({ idProveedor: "", estadoPago: "" });
  const [form, setForm] = useState({ idProveedor: "", categoria: "", factura: "", fecha: hoy(), observaciones: "" });
  const [lineas, setLineas] = useState([{ ...LINEA }]);
  const [detalle, setDetalle] = useState(null);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });
  const panel = useRef(null);

  const cargar = () => {
    const params = {};
    if (filtro.idProveedor) params.idProveedor = filtro.idProveedor;
    if (filtro.estadoPago) params.estadoPago = filtro.estadoPago;
    api.get("/compras", { params }).then((r) => setCompras(r.data)).catch((e) => avisar("err", mensajeError(e)));
    api.get("/compras/existencias").then((r) => setExistencias(r.data)).catch(() => {});
  };
  useEffect(() => { api.get("/compras/proveedores").then((r) => setProveedores(r.data)).catch(() => {}); }, []);
  useEffect(() => { cargar(); }, [filtro]);

  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });
  const cambiarLinea = (i, campo, valor) => setLineas(lineas.map((l, k) => (k === i ? { ...l, [campo]: valor } : l)));
  const subtotal = (l) => (Number(l.cantidad) || 0) * (Number(l.precio) || 0);
  const total = lineas.reduce((s, l) => s + subtotal(l), 0);

  const registrar = async (e) => {
    e.preventDefault();
    try {
      const r = await api.post("/compras", { ...form, detalle: lineas });
      avisar("ok", r.data.mensaje);
      setForm({ idProveedor: "", categoria: "", factura: "", fecha: hoy(), observaciones: "" });
      setLineas([{ ...LINEA }]);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const verDetalle = async (id) => {
    try {
      setDetalle((await api.get(`/compras/${id}`)).data);
      setTimeout(() => panel.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const pagar = async (c) => {
    if (!window.confirm(`¿Marcar como PAGADA la factura ${c.factura} de ${c.proveedor} por ${L(c.monto)}? No se puede deshacer.`)) return;
    try {
      const r = await api.put(`/compras/${c.id}/pagar`);
      avisar("ok", r.data.mensaje);
      cargar();
      if (detalle && detalle.id === c.id) verDetalle(c.id);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  return (
    <>
      <section className="card">
        <h1>Compras e insumos</h1>
        <p className="muted">
          Cada compra pertenece a un proveedor y tiene su detalle de insumos; el total se calcula solo. Las compras de <strong>PAPELERIA</strong> y{" "}
          <strong>MATERIALES</strong> suben solas las existencias del catálogo. Las armas, vehículos y uniformes se registran después como activos
          en Inventario &gt; Activos fijos, usando esta compra. Todos los campos son obligatorios. Una compra no se edita ni se elimina: solo se marca como pagada.
        </p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        <h2 className="section-title">Registrar compra</h2>
        <form onSubmit={registrar} className="form-col">
          <div className="form-grid">
            <div>
              <label>Proveedor *</label>
              <select className="input" value={form.idProveedor} onChange={cambiar("idProveedor")} required>
                <option value="">— Seleccione —</option>
                {proveedores.map((p) => <option key={p.id} value={p.id}>{p.nombre} ({p.tipo})</option>)}
              </select>
            </div>
            <div>
              <label>Categoría del insumo *</label>
              <select className="input" value={form.categoria} onChange={cambiar("categoria")} required>
                <option value="">— Seleccione —</option>
                {CATEGORIAS.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <div><label>Número de factura *</label><input className="input" value={form.factura} onChange={cambiar("factura")} required maxLength={50} /></div>
            <div><label>Fecha de compra *</label><input className="input" type="date" max={hoy()} value={form.fecha} onChange={cambiar("fecha")} required /></div>
            <div style={{ gridColumn: "1 / -1" }}><label>Observaciones *</label><textarea className="input" rows={4} value={form.observaciones} onChange={cambiar("observaciones")} required maxLength={200} placeholder="Si no hay, escriba: Sin observaciones" /></div>
          </div>

          <h3 style={{ marginBottom: 4 }}>Detalle de insumos</h3>
          <div className="fila-linea muted" style={{ fontSize: 12, fontWeight: 700 }}>
            <span>Descripción *</span><span>Cantidad *</span><span>Unidad *</span><span>Precio unit. (L) *</span><span></span>
          </div>
          {lineas.map((l, i) => (
            <div key={i} className="fila-linea">
              <input className="input" value={l.descripcion} onChange={(e) => cambiarLinea(i, "descripcion", e.target.value)} required maxLength={100} placeholder="Ej. Resma de papel" />
              <input className="input" type="number" min="0.01" step="0.01" value={l.cantidad} onChange={(e) => cambiarLinea(i, "cantidad", e.target.value)} required />
              <select className="input" value={l.unidad} onChange={(e) => cambiarLinea(i, "unidad", e.target.value)} required>
                {UNIDADES.map((u) => <option key={u} value={u}>{u}</option>)}
              </select>
              <input className="input" type="number" min="0" step="0.01" value={l.precio} onChange={(e) => cambiarLinea(i, "precio", e.target.value)} required />
              <button type="button" className="btn btn-outline btn-sm" title="Quitar línea" disabled={lineas.length === 1}
                onClick={() => setLineas(lineas.filter((_, k) => k !== i))}>✕</button>
            </div>
          ))}
          <div style={{ display: "flex", gap: 12, alignItems: "center", marginTop: 8 }}>
            <button type="button" className="btn btn-outline btn-sm" onClick={() => setLineas([...lineas, { ...LINEA }])}>+ AGREGAR INSUMO</button>
            <strong>Total de la compra: {L(total)}</strong>
          </div>
          <div style={{ marginTop: 10 }}><button type="submit" className="btn btn-dark">REGISTRAR COMPRA</button></div>
        </form>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Compras registradas</h2>
        <div className="filtros" style={{ gridTemplateColumns: "260px 200px", marginBottom: 10 }}>
          <select className="input" value={filtro.idProveedor} onChange={(e) => setFiltro({ ...filtro, idProveedor: e.target.value })}>
            <option value="">Todos los proveedores</option>
            {proveedores.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
          </select>
          <select className="input" value={filtro.estadoPago} onChange={(e) => setFiltro({ ...filtro, estadoPago: e.target.value })}>
            <option value="">Todos los pagos</option>
            <option value="PENDIENTE">Pendientes de pago</option>
            <option value="PAGADO">Pagadas</option>
          </select>
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Fecha</th><th>Proveedor</th><th>Factura</th><th>Categoría</th><th>Monto</th><th>Pago</th><th>Acciones</th></tr></thead>
            <tbody>
              {compras.map((c) => (
                <tr key={c.id}>
                  <td>{c.id}</td><td>{String(c.fecha)}</td><td><strong>{c.proveedor}</strong></td><td>{c.factura}</td><td>{c.categoria}</td>
                  <td>{L(c.monto)}</td>
                  <td><span className={"badge " + (COLOR_PAGO[c.estadoPago] || "badge-off")}>{c.estadoPago}</span></td>
                  <td className="acciones">
                    <button className="btn btn-outline btn-sm" onClick={() => verDetalle(c.id)}>Detalle</button>
                    {c.estadoPago === "PENDIENTE" && <button className="btn btn-dark btn-sm" onClick={() => pagar(c)}>Marcar pagada</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {compras.length === 0 && <p className="muted">No hay compras registradas con ese filtro.</p>}
        </div>
      </section>

      {detalle && (
        <section className="card" style={{ marginTop: 16 }} ref={panel}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <h2 className="section-title" style={{ marginTop: 0 }}>Detalle de la compra #{detalle.id} · factura {detalle.factura}</h2>
            <button className="btn btn-outline btn-sm" onClick={() => setDetalle(null)}>Cerrar</button>
          </div>
          <p className="muted">{detalle.proveedor} · {detalle.categoria} · {String(detalle.fecha)} · Autorizó: {detalle.autorizo || "—"} · {detalle.observaciones}</p>
          <div className="table-wrap"><table>
            <thead><tr><th>Insumo</th><th>Cantidad</th><th>Unidad</th><th>Precio unit.</th><th>Subtotal</th></tr></thead>
            <tbody>{detalle.detalle.map((d, i) => (
              <tr key={i}><td>{d.descripcion}</td><td>{Number(d.cantidad)}</td><td>{d.unidad}</td><td>{L(d.precio)}</td><td>{L(d.subtotal)}</td></tr>
            ))}</tbody>
            <tfoot><tr><td colSpan={4} style={{ textAlign: "right" }}><strong>Total</strong></td><td><strong>{L(detalle.monto)}</strong></td></tr></tfoot>
          </table></div>
        </section>
      )}

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Existencias del catálogo de insumos</h2>
        <p className="muted">Se actualiza solo con cada compra de papelería o materiales.</p>
        <div className="table-wrap"><table>
          <thead><tr><th>Insumo</th><th>Unidad</th><th>Total adquirido</th><th>Existencia actual</th><th>Última compra</th></tr></thead>
          <tbody>{existencias.map((x) => (
            <tr key={x.descripcion}><td><strong>{x.descripcion}</strong></td><td>{x.unidad}</td><td>{Number(x.adquirido)}</td><td>{Number(x.existencia)}</td><td>{String(x.ultimaCompra)}</td></tr>
          ))}</tbody>
        </table>
        {existencias.length === 0 && <p className="muted">Aún no hay insumos en el catálogo.</p>}</div>
      </section>
    </>
  );
}
