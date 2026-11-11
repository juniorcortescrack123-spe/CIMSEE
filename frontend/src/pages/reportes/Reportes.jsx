import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const L = (n) => "L " + Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

function Kpi({ titulo, valor, color }) {
  return (
    <div className={"kpi" + (color ? " kpi-" + color : "")}>
      <small>{titulo}</small>
      <strong>{valor}</strong>
    </div>
  );
}

// Barras horizontales simples (sin librerias externas)
function Barras({ datos }) {
  const max = Math.max(1, ...datos.map((d) => Number(d.valor)));
  if (datos.length === 0) return <p className="muted">Sin datos.</p>;
  return datos.map((d) => (
    <div className="barra-fila" key={d.etiqueta}>
      <span>{d.etiqueta}</span>
      <div className="barra-fondo"><div className="barra-valor" style={{ width: (Number(d.valor) / max) * 100 + "%" }} /></div>
      <strong>{Number(d.valor)}</strong>
    </div>
  ));
}

export default function Reportes() {
  const [r, setR] = useState(null);
  const [venc, setVenc] = useState([]);
  const [error, setError] = useState("");

  const cargar = () => {
    api.get("/reportes/resumen").then((x) => setR(x.data)).catch((e) => setError(mensajeError(e)));
    api.get("/reportes/vencimientos").then((x) => setVenc(x.data)).catch(() => {});
  };
  useEffect(() => { cargar(); }, []);

  if (error) return <section className="card"><p className="alerta alerta-err">{error}</p></section>;
  if (!r) return <section className="card"><p className="muted">Cargando indicadores...</p></section>;

  const { personal: p, comercial: c, operaciones: o, inventario: i, administracion: a } = r;
  const sinAsignar = Math.max(0, Number(p.guardias) - Number(p.guardiasAsignados));

  return (
    <>
      <section className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <h1>Reportes e indicadores</h1>
            <p className="muted">Resumen operativo al {new Date().toLocaleDateString()}. Los datos se calculan al momento desde la base de datos.</p>
          </div>
          <div className="no-imprimir" style={{ display: "flex", gap: 8 }}>
            <button className="btn btn-outline" onClick={cargar}>ACTUALIZAR</button>
            <button className="btn btn-dark" onClick={() => window.print()}>IMPRIMIR</button>
          </div>
        </div>

        <h2 className="section-title">Personal y operaciones</h2>
        <div className="kpi-grid">
          <Kpi titulo="Empleados activos" valor={Number(p.activos)} />
          <Kpi titulo="Guardias asignados hoy" valor={`${Number(p.guardiasAsignados)} de ${Number(p.guardias)}`} color="verde" />
          <Kpi titulo="Guardias sin asignar" valor={sinAsignar} color={sinAsignar > 0 ? "rojo" : "verde"} />
          <Kpi titulo="Puestos activos" valor={Number(o.puestosActivos)} />
          <Kpi titulo="Puestos sin cobertura hoy" valor={Number(o.puestosSinCobertura)} color={Number(o.puestosSinCobertura) > 0 ? "rojo" : "verde"} />
          <Kpi titulo="Visitas de supervisión (30 días)" valor={Number(o.supervisiones30)} />
          <Kpi titulo="Guardia ausente (30 días)" valor={Number(o.guardiaAusente30)} color={Number(o.guardiaAusente30) > 0 ? "rojo" : ""} />
        </div>
        <div className="dos-columnas">
          <div><h3>Personal por cargo</h3><Barras datos={p.porCargo} /></div>
          <div><h3>Incidencias por estado</h3><Barras datos={o.incidenciasPorEstado} /></div>
        </div>
        {o.listaSinCobertura.length > 0 && (
          <>
            <h3>Puestos activos sin guardia asignado hoy</h3>
            <div className="table-wrap"><table>
              <thead><tr><th>Cliente</th><th>Puesto</th><th>Ubicación</th></tr></thead>
              <tbody>{o.listaSinCobertura.map((x, k) => <tr key={k}><td>{x.cliente}</td><td>{x.puesto}</td><td>{x.ubicacion}</td></tr>)}</tbody>
            </table></div>
          </>
        )}
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Comercial</h2>
        <div className="kpi-grid">
          <Kpi titulo="Clientes" valor={Number(c.clientes)} />
          <Kpi titulo="Contratos vigentes" valor={Number(c.contratosVigentes)} color="verde" />
          <Kpi titulo="Vencen en 30 días" valor={Number(c.contratosPorVencer)} color={Number(c.contratosPorVencer) > 0 ? "rojo" : ""} />
          <Kpi titulo="Contratos vencidos" valor={Number(c.contratosVencidos)} color={Number(c.contratosVencidos) > 0 ? "rojo" : ""} />
          <Kpi titulo="Ingreso mensual contratado" valor={L(c.ingresoMensual)} />
        </div>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Inventario</h2>
        <div className="kpi-grid"><Kpi titulo="Valor de los activos" valor={L(i.valorTotal)} /></div>
        <div className="dos-columnas">
          <div><h3>Activos por tipo</h3><Barras datos={i.porTipo} /></div>
          <div><h3>Activos por estado</h3><Barras datos={i.porEstado} /></div>
        </div>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Administración</h2>
        <div className="kpi-grid">
          <Kpi titulo="Compras del mes" valor={`${Number(a.comprasMes)} · ${L(a.montoComprasMes)}`} />
          <Kpi titulo="Compras por pagar" valor={`${Number(a.comprasPendientes)} · ${L(a.montoComprasPendientes)}`} color={Number(a.comprasPendientes) > 0 ? "rojo" : "verde"} />
          <Kpi titulo="Pagos pendientes" valor={`${Number(a.pagosPendientes)} · ${L(a.montoPagosPendientes)}`} />
          <Kpi titulo="Pagos vencidos" valor={Number(a.pagosVencidos)} color={Number(a.pagosVencidos) > 0 ? "rojo" : "verde"} />
          <Kpi titulo="Permisos por vencer (30 días)" valor={Number(a.permisosPorVencer)} color={Number(a.permisosPorVencer) > 0 ? "rojo" : ""} />
          <Kpi titulo="Permisos vencidos" valor={Number(a.permisosVencidos)} color={Number(a.permisosVencidos) > 0 ? "rojo" : "verde"} />
        </div>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="section-title" style={{ marginTop: 0 }}>Vencimientos de los próximos 30 días</h2>
        {venc.length === 0 ? <p className="muted">No hay contratos, permisos ni pagos por vencer.</p> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Tipo</th><th>Descripción</th><th>Fecha</th></tr></thead>
            <tbody>{venc.map((v, k) => <tr key={k}><td>{v.tipo}</td><td>{v.descripcion}</td><td>{String(v.fecha)}</td></tr>)}</tbody>
          </table></div>
        )}
      </section>
    </>
  );
}
