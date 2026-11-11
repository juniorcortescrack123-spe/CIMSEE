import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError, tienePermiso } from "../../auth.js";

const TIPOS_CON_DATOS = ["ARMA", "VEHICULO", "UNIFORME"]; // el resto (equipo de protección y los que agregue el administrador) usa datos generales
const TIPOS_VEHICULO = ["AUTOMOVIL", "CAMIONETA", "MOTOCICLETA", "CAMION", "OTRO"];
const COLOR = { DISPONIBLE: "badge-ok", ASIGNADO: "badge-sis", MANTENIMIENTO: "badge-rojo", BAJA: "badge-off" };
const VACIO = {
  tipo: "ARMA", idCompra: "", descripcion: "", codigo: "", valor: "", ubicacion: "", fechaAdquisicion: "", observaciones: "",
  numeroSerie: "", marca: "", modelo: "", calibre: "", tipoArma: "", numeroRegistro: "",
  placa: "", tipoVehiculo: "CAMIONETA", anio: "", color: "", vin: "", numeroMotor: "", kilometraje: "", vencimientoMatricula: "",
  tipoPrenda: "", talla: "", genero: "", cantidad: "1",
};
const fecha = (v) => String(v || "").replace("T", " ").slice(0, 16);

export default function Activos() {
  const [filas, setFilas] = useState([]);
  const [compras, setCompras] = useState([]);
  const [filtro, setFiltro] = useState({ tipo: "", estado: "", q: "" });
  const [form, setForm] = useState(VACIO);
  const [abierto, setAbierto] = useState(false);
  const esAdmin = tienePermiso("SEGURIDAD");
  const [tipos, setTipos] = useState([]);             // catálogo de tipos de activo
  const [ubicaciones, setUbicaciones] = useState([]); // catálogo de ubicaciones (almacenes)
  const [catalogos, setCatalogos] = useState(false);  // panel del administrador
  const [nuevoTipo, setNuevoTipo] = useState("");
  const [nuevaUbicacion, setNuevaUbicacion] = useState("");
  const [traslado, setTraslado] = useState(null);     // { a, destino, motivo }
  const [detalle, setDetalle] = useState(null);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });
  const avisar = (tipo, texto) => setMsg({ tipo, texto });

  const cargar = () =>
    api.get("/activos", { params: filtro }).then((r) => setFilas(r.data)).catch((e) => avisar("err", mensajeError(e)));
  useEffect(() => { cargar(); }, [filtro.tipo, filtro.estado]);
  useEffect(() => { api.get("/activos/compras").then((r) => setCompras(r.data)).catch(() => {}); }, []);
  const cargarCatalogos = () => {
    api.get("/activos/tipos").then((r) => setTipos(r.data)).catch(() => {});
    api.get("/activos/ubicaciones").then((r) => setUbicaciones(r.data)).catch(() => {});
  };
  useEffect(() => { cargarCatalogos(); }, []);
  const nombreTipo = (codigo) => tipos.find((t) => t.codigo === codigo)?.nombre || String(codigo).replace("_", " ");
  const tiposActivos = tipos.filter((t) => t.activo);
  const ubicacionesActivas = ubicaciones.filter((u) => u.activo);

  const accionCatalogo = async (peticion, ok) => {
    try {
      const r = await peticion();
      avisar("ok", r.data.mensaje || ok);
      cargarCatalogos();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const c = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });

  const registrar = async (e) => {
    e.preventDefault();
    const datos = { ...form, idCompra: Number(form.idCompra) };
    Object.keys(datos).forEach((k) => { if (datos[k] === "") datos[k] = null; });
    try {
      const r = await api.post("/activos", datos);
      avisar("ok", r.data.mensaje);
      setForm(VACIO);
      setAbierto(false);
      cargar();
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const verDetalle = async (id) => {
    try {
      setDetalle((await api.get(`/activos/${id}`)).data);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
  };

  const cambiarEstado = async (a) => {
    const nuevo = a.estado === "MANTENIMIENTO" ? "DISPONIBLE" : "MANTENIMIENTO";
    const motivo = window.prompt(`Motivo para pasar "${a.descripcion}" a ${nuevo}:`);
    if (motivo === null) return;
    try {
      const r = await api.put(`/activos/${a.id}/estado`, { estado: nuevo, motivo });
      avisar("ok", r.data.mensaje);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const confirmarTraslado = async (e) => {
    e.preventDefault();
    try {
      const r = await api.post(`/activos/${traslado.a.id}/trasladar`, { ubicacionDestino: traslado.destino, motivo: traslado.motivo });
      avisar("ok", r.data.mensaje);
      setTraslado(null);
    } catch (err) {
      avisar("err", mensajeError(err));
    }
    cargar();
  };

  const d = detalle?.detalle || {};

  return (
    <>
      <section className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
          <h1 style={{ margin: 0 }}>Inventario de activos fijos</h1>
          <div style={{ display: "flex", gap: 8 }}>
            {esAdmin && <button className="btn btn-outline" onClick={() => setCatalogos(!catalogos)}>{catalogos ? "CERRAR LISTAS" : "TIPOS Y UBICACIONES"}</button>}
            <button className="btn btn-dark" onClick={() => setAbierto(!abierto)}>{abierto ? "CERRAR FORMULARIO" : "+ REGISTRAR ACTIVO"}</button>
          </div>
        </div>
        <p className="muted">Control único: cada arma tiene un <strong>número de serie</strong> y cada vehículo una <strong>placa</strong>, obligatorios y no repetibles.</p>
        {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

        {traslado && (
          <form onSubmit={confirmarTraslado} className="form-col" style={{ background: "#f8f9fa", border: "1px solid #ddd", borderRadius: 8, padding: 16, marginBottom: 16 }}>
            <h2 className="section-title" style={{ marginTop: 0 }}>Trasladar: {traslado.a.descripcion}</h2>
            <p className="muted">Ubicación actual: <strong>{traslado.a.ubicacion || "sin ubicación"}</strong>. Elija el almacén de destino de la lista.</p>
            <div className="form-grid">
              <div>
                <label>Almacén de destino *</label>
                <select className="input" value={traslado.destino} onChange={(e) => setTraslado({ ...traslado, destino: e.target.value })} required>
                  <option value="">— Seleccione —</option>
                  {ubicacionesActivas.filter((u) => u.nombre !== traslado.a.ubicacion).map((u) => <option key={u.id} value={u.nombre}>{u.nombre}</option>)}
                </select>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label>Motivo del traslado *</label>
                <textarea className="input" rows={3} maxLength={200} value={traslado.motivo} onChange={(e) => setTraslado({ ...traslado, motivo: e.target.value })} required />
              </div>
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              <button type="submit" className="btn btn-dark">TRASLADAR</button>
              <button type="button" className="btn btn-outline" onClick={() => setTraslado(null)}>CANCELAR</button>
            </div>
          </form>
        )}

        {esAdmin && catalogos && (
          <div className="dos-columnas" style={{ background: "#f8f9fa", border: "1px solid #ddd", borderRadius: 8, padding: 16, marginBottom: 16 }}>
            <div>
              <h2 className="section-title" style={{ marginTop: 0 }}>Tipos de activo</h2>
              <p className="muted">Los cuatro del sistema no se pueden desactivar. Los que agregue usan datos generales (descripción, serie opcional).</p>
              <div className="table-wrap"><table>
                <thead><tr><th>Tipo</th><th>Activos</th><th></th></tr></thead>
                <tbody>{tipos.map((t) => (
                  <tr key={t.codigo}>
                    <td>{t.nombre} {t.sistema && <span className="badge badge-sis">sistema</span>} {!t.activo && <span className="badge badge-off">inactivo</span>}</td>
                    <td>{t.activos}</td>
                    <td>{!t.sistema && <button className="btn btn-outline btn-sm" onClick={() => accionCatalogo(() => api.put(`/activos/tipos/${t.codigo}/estado`, { activo: !t.activo }))}>{t.activo ? "Desactivar" : "Activar"}</button>}</td>
                  </tr>
                ))}</tbody>
              </table></div>
              <form onSubmit={(e) => { e.preventDefault(); accionCatalogo(() => api.post("/activos/tipos", { nombre: nuevoTipo })); setNuevoTipo(""); }} style={{ display: "flex", gap: 8, marginTop: 8 }}>
                <input className="input" placeholder="Nuevo tipo (ej. Radio de comunicación)" value={nuevoTipo} onChange={(e) => setNuevoTipo(e.target.value)} required maxLength={60} />
                <button type="submit" className="btn btn-dark btn-sm">+ AGREGAR</button>
              </form>
            </div>
            <div>
              <h2 className="section-title" style={{ marginTop: 0 }}>Ubicaciones (almacenes)</h2>
              <p className="muted">Son las únicas opciones al registrar un activo o trasladarlo.</p>
              <div className="table-wrap"><table>
                <thead><tr><th>Ubicación</th><th>Activos</th><th></th></tr></thead>
                <tbody>{ubicaciones.map((u) => (
                  <tr key={u.id}>
                    <td>{u.nombre} {!u.activo && <span className="badge badge-off">inactiva</span>}</td>
                    <td>{u.activos}</td>
                    <td><button className="btn btn-outline btn-sm" onClick={() => accionCatalogo(() => api.put(`/activos/ubicaciones/${u.id}/estado`, { activo: !u.activo }))}>{u.activo ? "Desactivar" : "Activar"}</button></td>
                  </tr>
                ))}</tbody>
              </table></div>
              <form onSubmit={(e) => { e.preventDefault(); accionCatalogo(() => api.post("/activos/ubicaciones", { nombre: nuevaUbicacion })); setNuevaUbicacion(""); }} style={{ display: "flex", gap: 8, marginTop: 8 }}>
                <input className="input" placeholder="Nueva ubicación (ej. Almacén Choluteca)" value={nuevaUbicacion} onChange={(e) => setNuevaUbicacion(e.target.value)} required maxLength={100} />
                <button type="submit" className="btn btn-dark btn-sm">+ AGREGAR</button>
              </form>
            </div>
          </div>
        )}

        {abierto && (
          <form onSubmit={registrar} className="form-col">
            <h2 className="section-title">Registrar activo</h2>
            <div className="form-grid">
              <div>
                <label>Tipo de activo *</label>
                <select className="input" value={form.tipo} onChange={c("tipo")} required>{tiposActivos.map((t) => <option key={t.codigo} value={t.codigo}>{t.nombre}</option>)}</select>
              </div>
              <div>
                <label>Compra de origen *</label>
                <select className="input" value={form.idCompra} onChange={c("idCompra")} required>
                  <option value="">— Seleccione —</option>
                  {compras.map((k) => <option key={k.id} value={k.id}>Factura {k.factura} — {k.proveedor} ({k.categoria})</option>)}
                </select>
              </div>
              <div><label>Descripción *</label><input className="input" value={form.descripcion} onChange={c("descripcion")} maxLength={100} required /></div>
              <div><label>Código de inventario</label><input className="input" value={form.codigo} onChange={c("codigo")} placeholder="Se genera si lo deja vacío" /></div>
              <div><label>Valor de adquisición (L) *</label><input className="input" type="number" min="0" step="0.01" value={form.valor} onChange={c("valor")} required /></div>
              <div>
                <label>Ubicación *</label>
                <select className="input" value={form.ubicacion} onChange={c("ubicacion")} required>
                  <option value="">— Seleccione —</option>
                  {ubicacionesActivas.map((u) => <option key={u.id} value={u.nombre}>{u.nombre}</option>)}
                </select>
              </div>
              <div><label>Fecha de adquisición *</label><input className="input" type="date" max={new Date().toISOString().slice(0, 10)} value={form.fechaAdquisicion} onChange={c("fechaAdquisicion")} required /></div>

              {form.tipo === "ARMA" && (<>
                <div><label>Número de serie *</label><input className="input" value={form.numeroSerie} onChange={c("numeroSerie")} required /></div>
                <div><label>Marca *</label><input className="input" value={form.marca} onChange={c("marca")} required /></div>
                <div><label>Modelo *</label><input className="input" value={form.modelo} onChange={c("modelo")} required /></div>
                <div><label>Calibre *</label><input className="input" value={form.calibre} onChange={c("calibre")} required /></div>
                <div><label>Tipo de arma *</label><input className="input" value={form.tipoArma} onChange={c("tipoArma")} placeholder="PISTOLA, ESCOPETA..." required /></div>
                <div><label>Número de registro *</label><input className="input" value={form.numeroRegistro} onChange={c("numeroRegistro")} required /></div>
              </>)}

              {form.tipo === "VEHICULO" && (<>
                <div><label>Placa *</label><input className="input" value={form.placa} onChange={c("placa")} required /></div>
                <div>
                  <label>Tipo de vehículo *</label>
                  <select className="input" value={form.tipoVehiculo} onChange={c("tipoVehiculo")}>{TIPOS_VEHICULO.map((t) => <option key={t}>{t}</option>)}</select>
                </div>
                <div><label>Marca *</label><input className="input" value={form.marca} onChange={c("marca")} required /></div>
                <div><label>Modelo *</label><input className="input" value={form.modelo} onChange={c("modelo")} required /></div>
                <div><label>Año *</label><input className="input" type="number" value={form.anio} onChange={c("anio")} required /></div>
                <div><label>Color *</label><input className="input" value={form.color} onChange={c("color")} required /></div>
                <div><label>VIN *</label><input className="input" value={form.vin} onChange={c("vin")} required /></div>
                <div><label>Número de motor *</label><input className="input" value={form.numeroMotor} onChange={c("numeroMotor")} required /></div>
                <div><label>Kilometraje actual *</label><input className="input" type="number" min="0" value={form.kilometraje} onChange={c("kilometraje")} required /></div>
                <div><label>Vencimiento de matrícula *</label><input className="input" type="date" value={form.vencimientoMatricula} onChange={c("vencimientoMatricula")} required /></div>
              </>)}

              {form.tipo === "UNIFORME" && (<>
                <div><label>Tipo de prenda *</label><input className="input" value={form.tipoPrenda} onChange={c("tipoPrenda")} required /></div>
                <div><label>Talla *</label><input className="input" value={form.talla} onChange={c("talla")} required /></div>
                <div><label>Género *</label><input className="input" value={form.genero} onChange={c("genero")} required /></div>
                <div><label>Cantidad *</label><input className="input" type="number" min="1" value={form.cantidad} onChange={c("cantidad")} required /></div>
              </>)}

              {!TIPOS_CON_DATOS.includes(form.tipo) && (
                <div><label>Número de serie (opcional)</label><input className="input" value={form.numeroSerie} onChange={c("numeroSerie")} /></div>
              )}
              <div style={{ gridColumn: "1 / -1" }}><label>Observaciones *</label><textarea className="input" rows={4} value={form.observaciones} onChange={c("observaciones")} maxLength={200} required placeholder="Si no hay, escriba: Sin observaciones" /></div>
            </div>
            <div style={{ marginTop: 8 }}><button type="submit" className="btn btn-dark">GUARDAR ACTIVO</button></div>
          </form>
        )}
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="filtros" style={{ gridTemplateColumns: "1fr 1fr 2fr auto" }}>
          <select className="input" value={filtro.tipo} onChange={(e) => setFiltro({ ...filtro, tipo: e.target.value })}>
            <option value="">Todos los tipos</option>{tipos.map((t) => <option key={t.codigo} value={t.codigo}>{t.nombre}</option>)}
          </select>
          <select className="input" value={filtro.estado} onChange={(e) => setFiltro({ ...filtro, estado: e.target.value })}>
            <option value="">Todos los estados</option><option>DISPONIBLE</option><option>ASIGNADO</option><option>MANTENIMIENTO</option><option>BAJA</option>
          </select>
          <input className="input" placeholder="Buscar por descripción, serie, placa o código" value={filtro.q} onChange={(e) => setFiltro({ ...filtro, q: e.target.value })} />
          <button className="btn btn-dark" onClick={cargar}>BUSCAR</button>
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Código</th><th>Tipo</th><th>Descripción</th><th>Serie / Placa</th><th>Estado</th><th>Ubicación</th><th>Responsable</th><th>Acciones</th></tr></thead>
            <tbody>
              {filas.map((a) => (
                <tr key={a.id}>
                  <td>{a.codigo}</td><td>{nombreTipo(a.tipo)}</td><td><strong>{a.descripcion}</strong></td>
                  <td>{a.serieOPlaca || "—"}</td>
                  <td><span className={"badge " + (COLOR[a.estado] || "badge-off")}>{a.estado}</span></td>
                  <td>{a.ubicacion || "—"}</td><td>{a.responsable || "—"}</td>
                  <td className="acciones">
                    <button className="btn btn-outline btn-sm" onClick={() => verDetalle(a.id)}>Detalle</button>
                    {a.estado !== "ASIGNADO" && a.estado !== "BAJA" && (<>
                      <button className="btn btn-outline btn-sm" onClick={() => cambiarEstado(a)}>{a.estado === "MANTENIMIENTO" ? "Habilitar" : "Mantenimiento"}</button>
                      <button className="btn btn-outline btn-sm" onClick={() => { setTraslado({ a, destino: "", motivo: "" }); window.scrollTo({ top: 0, behavior: "smooth" }); }}>Trasladar</button>
                    </>)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filas.length === 0 && <p className="muted">No hay activos con ese filtro.</p>}
        </div>
      </section>

      {detalle && (
        <section className="card" style={{ marginTop: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <h2 className="section-title" style={{ marginTop: 0 }}>{detalle.codigo} — {detalle.descripcion}</h2>
            <button className="btn btn-outline btn-sm" onClick={() => setDetalle(null)}>Cerrar</button>
          </div>
          <p className="muted">
            {nombreTipo(detalle.tipo)} · Estado <strong>{detalle.estado}</strong> · Ubicación {detalle.ubicacion || "—"}
            {detalle.responsable ? ` · Responsable ${detalle.responsable}` : ""}
            {detalle.valor ? ` · Valor L ${Number(detalle.valor).toLocaleString()}` : ""}
          </p>
          {detalle.motivoBaja && <p className="alerta alerta-err">Baja: {detalle.motivoBaja}</p>}
          <div className="chips">
            {Object.entries(d).filter(([, v]) => v !== null && v !== "").map(([k, v]) => <span key={k} className="chip">{k}: {String(v)}</span>)}
          </div>
          <h3>Historial de movimientos</h3>
          {detalle.movimientos.length === 0 ? <p className="muted">Sin movimientos.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Fecha</th><th>Movimiento</th><th>Origen</th><th>Destino</th><th>Motivo</th></tr></thead>
              <tbody>{detalle.movimientos.map((m, i) => (
                <tr key={i}><td>{fecha(m.fechaHora)}</td><td>{m.tipo}</td><td>{m.origen || "—"}</td><td>{m.destino || "—"}</td><td>{m.motivo}</td></tr>
              ))}</tbody>
            </table></div>
          )}
        </section>
      )}
    </>
  );
}
