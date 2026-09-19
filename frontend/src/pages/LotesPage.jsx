import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { soloFecha } from '../util/fechas'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_LOTES = [
  'El código de lote debe ser único por producto.',
  'La fecha de vencimiento determina el orden FEFO: se despachan primero los lotes que vencen antes.',
  'Un lote no se puede vender dentro de los 3 meses previos a su vencimiento (período preventivo) ni después de vencido.',
  "Usa 'Bloquear' para inmovilizar temporalmente un lote y 'Retirar' para darlo de baja definitivamente.",
]

export function LotesPage() {
  const [lotes, setLotes] = useState([])
  const [productos, setProductos] = useState([])
  const [locales, setLocales] = useState([])
  const [mensaje, setMensaje] = useState(null)
  const [form, setForm] = useState({
    codigo: '',
    productoId: '',
    fechaVencimiento: '',
    cantidadRecibida: '',
    localId: '',
  })

  function cargarLotes() {
    api.get('/api/lotes').then(setLotes).catch((e) => setMensaje(e.message))
  }

  useEffect(() => {
    cargarLotes()
    api.get('/api/productos').then(setProductos)
    api.get('/api/locales').then(setLocales)
  }, [])

  function actualizarCampo(campo, valor) {
    setForm((prev) => ({ ...prev, [campo]: valor }))
  }

  async function registrarLote(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      await api.post('/api/lotes', {
        ...form,
        cantidadRecibida: Number(form.cantidadRecibida),
        costo: null,
      })
      setMensaje('Lote registrado.')
      cargarLotes()
    } catch (err) {
      setMensaje(err.message)
    }
  }

  async function bloquear(id) {
    await api.patch(`/api/lotes/${id}/bloquear`)
    cargarLotes()
  }

  async function retirar(id) {
    await api.patch(`/api/lotes/${id}/retirar`)
    cargarLotes()
  }

  return (
    <section>
      <h1>
        Lotes
        <AyudaFormulario titulo="Cómo registrar un lote" pasos={AYUDA_LOTES} />
      </h1>

      <form className="tarjeta" onSubmit={registrarLote}>
        <h3>Nuevo lote</h3>
        <label>
          Codigo de lote
          <input value={form.codigo} onChange={(e) => actualizarCampo('codigo', e.target.value)} required />
        </label>
        <label>
          Producto
          <select value={form.productoId} onChange={(e) => actualizarCampo('productoId', e.target.value)} required>
            <option value="">--</option>
            {productos.map((p) => <option key={p.id} value={p.id}>{p.nombreComercial}</option>)}
          </select>
        </label>
        <label>
          Local
          <select value={form.localId} onChange={(e) => actualizarCampo('localId', e.target.value)} required>
            <option value="">--</option>
            {locales.map((l) => <option key={l.id} value={l.id}>{l.nombre}</option>)}
          </select>
        </label>
        <label>
          Fecha de vencimiento
          <input type="date" value={form.fechaVencimiento} onChange={(e) => actualizarCampo('fechaVencimiento', e.target.value)} required />
        </label>
        <label>
          Cantidad recibida
          <input type="number" value={form.cantidadRecibida} onChange={(e) => actualizarCampo('cantidadRecibida', e.target.value)} required />
        </label>
        <button type="submit">Registrar lote</button>
      </form>

      {mensaje && <p className="aviso">{mensaje}</p>}

      <div className="tarjeta">
        <table>
          <thead>
            <tr><th>Codigo</th><th>Vencimiento</th><th>Recibida</th><th>Disponible</th><th>Estado</th><th></th></tr>
          </thead>
          <tbody>
            {lotes.map((l) => (
              <tr key={l.id}>
                <td>{l.codigo}</td>
                <td>{soloFecha(l.fechaVencimiento)}</td>
                <td>{l.cantidadRecibida}</td>
                <td>{l.cantidadDisponible}</td>
                <td>{l.estado}</td>
                <td>
                  <button onClick={() => bloquear(l.id)}>Bloquear</button>
                  <button onClick={() => retirar(l.id)}>Retirar</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
