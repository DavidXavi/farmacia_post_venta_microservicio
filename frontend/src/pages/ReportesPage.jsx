import { useState } from 'react'
import { api } from '../api/client'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_REPORTES = [
  "En 'Ventas diarias', elige una fecha y consulta para ver el resumen de ventas de ese día (si no eliges fecha, se usa el día de hoy).",
  "En 'Lotes próximos a vencer', el reporte revisa un horizonte fijo de 90 días hacia adelante desde hoy.",
]

export function ReportesPage() {
  const [fecha, setFecha] = useState('')
  const [ventas, setVentas] = useState(null)
  const [lotesPorVencer, setLotesPorVencer] = useState(null)
  const [mensaje, setMensaje] = useState(null)

  async function consultarVentas() {
    setMensaje(null)
    try {
      setVentas(await api.get('/api/reportes/ventas-diarias', { fecha: fecha || undefined }))
    } catch (err) {
      setMensaje(err.message)
    }
  }

  async function consultarLotesPorVencer() {
    setMensaje(null)
    try {
      setLotesPorVencer(await api.get('/api/reportes/lotes-proximos-a-vencer', { diasHorizonte: 90 }))
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <section>
      <h1>
        Reportes
        <AyudaFormulario titulo="Cómo usar los reportes" pasos={AYUDA_REPORTES} />
      </h1>
      {mensaje && <p className="aviso">{mensaje}</p>}

      <div className="tarjeta">
        <h3>Ventas diarias</h3>
        <label>
          Fecha
          <input type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        </label>
        <button onClick={consultarVentas}>Consultar</button>
        {ventas && (
          <table>
            <thead><tr><th>Correlativo</th><th>Fecha</th><th>Estado</th><th>Total</th></tr></thead>
            <tbody>
              {ventas.map((v) => (
                <tr key={v.id}>
                  <td>{v.numeroCorrelativo ?? '-'}</td>
                  <td>{new Date(v.fecha).toLocaleString()}</td>
                  <td>{v.estado}</td>
                  <td>S/ {v.total}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="tarjeta">
        <h3>Lotes proximos a vencer (90 dias)</h3>
        <button onClick={consultarLotesPorVencer}>Consultar</button>
        {lotesPorVencer && (
          <table>
            <thead><tr><th>Codigo</th><th>Vencimiento</th><th>Disponible</th></tr></thead>
            <tbody>
              {lotesPorVencer.map((l) => (
                <tr key={l.loteId}>
                  <td>{l.codigo}</td>
                  <td>{l.fechaVencimiento}</td>
                  <td>{l.cantidadDisponible}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  )
}
