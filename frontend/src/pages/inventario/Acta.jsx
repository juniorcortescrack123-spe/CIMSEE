import { useEffect, useState } from "react";
import { useParams, useSearchParams, useNavigate } from "react-router-dom";
import { api } from "../../api.js";
import { mensajeError } from "../../auth.js";

const fecha = (v) => (v ? String(v).replace("T", " ").slice(0, 16) : "—");

// Acta digital de entrega (resguardo) o de devolución, lista para imprimir o guardar como PDF
export default function Acta() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const tipo = params.get("tipo") === "DEVOLUCION" ? "DEVOLUCION" : "ENTREGA";
  const [a, setA] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get(`/equipos/asignaciones/${id}/acta`).then((r) => setA(r.data)).catch((e) => setError(mensajeError(e)));
  }, [id]);

  if (error) return <section className="card"><p className="alerta alerta-err">{error}</p></section>;
  if (!a) return <section className="card"><p className="muted">Cargando acta...</p></section>;

  const esDev = tipo === "DEVOLUCION";
  const det = a.detalle || {};
  const marcaModelo = [det.marca, det.modelo, det.calibre, det.color].filter(Boolean).join(" · ");

  return (
    <>
      <div className="no-print" style={{ display: "flex", gap: 8, marginBottom: 12 }}>
        <button className="btn btn-dark" onClick={() => window.print()}>IMPRIMIR / GUARDAR PDF</button>
        <button className="btn btn-outline" onClick={() => navigate("/inventario/equipos")}>VOLVER</button>
      </div>
      <section className="acta">
        <h3 className="acta-empresa">CIMSE — Seguridad Privada</h3>
        <h2>{esDev ? "ACTA DE DEVOLUCIÓN DE EQUIPO" : "ACTA DE ENTREGA Y RESGUARDO DE EQUIPO"}</h2>
        <p className="acta-num">Acta N.º {a.numero} · {esDev ? "Fecha de devolución" : "Fecha de entrega"}: {fecha(esDev ? a.fechaDevolucion : a.fechaEntrega)}</p>

        <h4>Datos del empleado</h4>
        <table><tbody>
          <tr><th>Nombre</th><td>{a.empleado}</td><th>Identidad</th><td>{a.identidad}</td></tr>
          <tr><th>Cargo</th><td>{a.cargo}</td><th>{esDev ? "Recibe" : "Entrega"}</th><td>{(esDev ? a.recibe : a.entrega) || "—"}</td></tr>
        </tbody></table>

        <h4>Datos del equipo</h4>
        <table><tbody>
          <tr><th>Código</th><td>{a.codigo}</td><th>Tipo</th><td>{String(a.tipo).replace("_", " ")}</td></tr>
          <tr><th>Descripción</th><td>{a.activo}</td><th>Serie / Placa</th><td>{a.serieOPlaca || "—"}</td></tr>
          {marcaModelo && <tr><th>Detalle</th><td colSpan={3}>{marcaModelo}</td></tr>}
          <tr><th>Estado al entregar</th><td>{a.estadoEntrega}</td><th>{esDev ? "Estado al devolver" : "Acta firmada"}</th><td>{esDev ? a.estadoDevolucion : (a.firmada ? "Sí" : "No")}</td></tr>
          {a.tipo === "VEHICULO" && (
            <tr><th>Kilometraje inicial</th><td>{a.kmInicial ?? "—"}</td><th>Kilometraje final</th><td>{a.kmFinal ?? "—"}</td></tr>
          )}
        </tbody></table>

        <p className="acta-texto">
          {esDev
            ? "El empleado devuelve el equipo descrito en el estado indicado, y la empresa lo recibe para su revisión y registro en el inventario."
            : "El empleado recibe el equipo descrito en el estado indicado y se compromete a usarlo solo para el servicio, cuidarlo y devolverlo cuando la empresa lo solicite. Responde por su pérdida o daño por mal uso."}
        </p>
        {a.observaciones && <p className="acta-texto"><strong>Observaciones:</strong> {a.observaciones}</p>}

        <div className="firmas">
          <div className="firma">{a.empleado}<br />{esDev ? "Entrega el equipo" : "Recibe el equipo"}</div>
          <div className="firma">{(esDev ? a.recibe : a.entrega) || "Responsable de inventario"}<br />{esDev ? "Recibe el equipo" : "Entrega el equipo"}</div>
        </div>
      </section>
    </>
  );
}
