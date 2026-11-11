import { useEffect, useState } from "react";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";
import { COLOR } from "./Contratos.jsx";

export default function AlertasContratos() {
  const [dias, setDias] = useState(30);
  const [filas, setFilas] = useState([]);
  const [msg, setMsg] = useState({ tipo: "", texto: "" });

  const cargar = () =>
    api.get("/contratos/alertas", { params: { dias } })
      .then((r) => setFilas(r.data))
      .catch((e) => setMsg({ tipo: "err", texto: mensajeError(e) }));
  useEffect(() => { cargar(); }, [dias]);

  const renovar = async (c) => {
    const fecha = window.prompt(`Nueva fecha de vencimiento para el contrato #${c.id} (AAAA-MM-DD). Debe ser posterior a ${c.fechaVencimiento}:`);
    if (!fecha) return;
    try {
      const r = await api.post(`/contratos/${c.id}/renovar`, { fechaVencimiento: fecha });
      setMsg({ tipo: "ok", texto: r.data.mensaje });
    } catch (err) {
      setMsg({ tipo: "err", texto: mensajeError(err) });
    }
    cargar();
  };

  return (
    <section className="card">
      <h1>Alertas de vencimiento de contratos</h1>
      <p className="muted">
        Contratos vigentes que vencen pronto (los que ya vencieron aparecen siempre). <span className="badge badge-ok">VERDE</span> más de 30 días ·{" "}
        <span className="badge badge-sis">AMARILLO</span> 30 días o menos · <span className="badge badge-rojo">ROJO</span> 7 días o menos ·{" "}
        <span className="badge badge-rojo">VENCIDO</span> ya pasó la fecha.
      </p>
      {msg.texto && <p className={"alerta " + (msg.tipo === "ok" ? "alerta-ok" : "alerta-err")}>{msg.texto}</p>}

      <div className="filtros" style={{ gridTemplateColumns: "220px" }}>
        <select className="input" value={dias} onChange={(e) => setDias(Number(e.target.value))}>
          <option value={7}>Próximos 7 días</option>
          <option value={30}>Próximos 30 días</option>
          <option value={60}>Próximos 60 días</option>
          <option value={90}>Próximos 90 días</option>
        </select>
      </div>

      <div className="table-wrap">
        <table>
          <thead><tr><th>#</th><th>Cliente</th><th>Vencimiento</th><th>Días restantes</th><th>Monto mensual</th><th>Semáforo</th><th></th></tr></thead>
          <tbody>
            {filas.map((c) => (
              <tr key={c.id}>
                <td>{c.id}</td>
                <td><strong>{c.cliente}</strong></td>
                <td>{String(c.fechaVencimiento)}</td>
                <td>{c.diasRestantes}</td>
                <td>L {Number(c.montoMensual).toLocaleString()}</td>
                <td><span className={"badge " + (COLOR[c.semaforo] || "badge-off")}>{c.semaforo}</span></td>
                <td><button className="btn btn-dark btn-sm" onClick={() => renovar(c)}>Renovar</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        {filas.length === 0 && <p className="muted">No hay contratos por vencer en ese período.</p>}
      </div>
    </section>
  );
}
