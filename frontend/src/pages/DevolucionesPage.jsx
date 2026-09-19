import { useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_DEVOLUCIONES = [
  'Busca la venta original por su Id (visible en la pantalla de Venta o Reportes) para ver sus líneas.',
  'Solo se pueden devolver ventas Confirmadas, dentro de los 30 días de la venta, y productos no controlados.',
  'La cantidad a devolver no puede superar lo vendido menos lo ya devuelto en devoluciones previas de esa misma venta.',
  'El stock devuelto se reintegra a los mismos lotes que surtieron la venta originalmente.',
]

export function DevolucionesPage() {
  const { session } = useAuth()
  const [ventaId, setVentaId] = useState('')
  const [venta, setVenta] = useState(null)
  const [devoluciones, setDevoluciones] = useState([])
  const [cantidades, setCantidades] = useState({})
  const [motivo, setMotivo] = useState('')
  const [mensaje, setMensaje] = useState(null)

  async function cargarDevoluciones(id) {
    setDevoluciones(await api.get('/api/devoluciones', { ventaId: id }))
  }

  async function buscar() {
    setMensaje(null)
    setVenta(null)
    try {
      const encontrada = await api.get(`/api/ventas/${ventaId}`)
      setVenta(encontrada)
      setCantidades({})
      await cargarDevoluciones(encontrada.id)
    } catch (err) {
      setMensaje('No se encontro una venta con ese ID.')
    }
  }

  async function registrar(e) {
    e.preventDefault()
    setMensaje(null)
    const lineas = Object.entries(cantidades)
      .filter(([, cantidad]) => Number(cantidad) > 0)
      .map(([detalleVentaId, cantidad]) => ({ detalleVentaId, cantidad: Number(cantidad) }))

    if (lineas.length === 0) {
      setMensaje('Indica al menos una cantidad a devolver.')
      return
    }

    try {
      await api.post('/api/devoluciones', {
        ventaId: venta.id,
        usuarioId: session.usuarioId,
        motivo,
        lineas,
      })
      setMensaje('Devolucion registrada.')
      setCantidades({})
      setMotivo('')
      await cargarDevoluciones(venta.id)
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <section>
      <h1>
        Devoluciones
        <AyudaFormulario titulo="Cómo registrar una devolución" pasos={AYUDA_DEVOLUCIONES} />
      </h1>

      <div className="tarjeta">
        <h3>Buscar venta</h3>
        <label>
          ID de venta
          <input value={ventaId} onChange={(e) => setVentaId(e.target.value)} placeholder="Copia el ID desde Reportes" />
        </label>
        <button onClick={buscar}>Buscar</button>
      </div>

      {mensaje && <p className="aviso">{mensaje}</p>}

      {venta && (
        <form className="tarjeta" onSubmit={registrar}>
          <h3>Venta {venta.numeroComprobante ?? venta.id} — Estado: {venta.estado}</h3>
          <table>
            <thead>
              <tr><th>Producto</th><th>Cantidad vendida</th><th>Cantidad a devolver</th></tr>
            </thead>
            <tbody>
              {venta.detalles.map((d) => (
                <tr key={d.id}>
                  <td>{d.nombreProducto}</td>
                  <td>{d.cantidad}</td>
                  <td>
                    <input
                      type="number"
                      min="0"
                      max={d.cantidad}
                      value={cantidades[d.id] ?? ''}
                      onChange={(e) => setCantidades((prev) => ({ ...prev, [d.id]: e.target.value }))}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <label>
            Motivo
            <input value={motivo} onChange={(e) => setMotivo(e.target.value)} required />
          </label>
          <button type="submit">Registrar devolucion</button>
        </form>
      )}

      {devoluciones.length > 0 && (
        <div className="tarjeta">
          <h3>Devoluciones registradas</h3>
          <table>
            <thead><tr><th>Fecha</th><th>Motivo</th><th>Total</th></tr></thead>
            <tbody>
              {devoluciones.map((d) => (
                <tr key={d.id}>
                  <td>{new Date(d.fecha).toLocaleString()}</td>
                  <td>{d.motivo}</td>
                  <td>S/ {d.total}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
