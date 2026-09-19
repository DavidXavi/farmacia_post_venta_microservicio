import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_CAJA = [
  'Selecciona la caja para ver si ya tiene una sesión abierta.',
  'Si no hay sesión abierta, ingresa el monto inicial de efectivo y abre la caja: no se puede vender sin una caja abierta.',
  'Al cerrar, ingresa el monto contado en efectivo; el sistema calcula la diferencia contra lo esperado según las ventas confirmadas del turno.',
]

export function CajaPage() {
  const { session } = useAuth()
  const [cajas, setCajas] = useState([])
  const [cajaId, setCajaId] = useState('')
  const [sesion, setSesion] = useState(null)
  const [montoInicial, setMontoInicial] = useState('100')
  const [montoDeclarado, setMontoDeclarado] = useState('')
  const [observacion, setObservacion] = useState('')
  const [mensaje, setMensaje] = useState(null)

  useEffect(() => {
    api.get('/api/cajas').then(setCajas).catch((e) => setMensaje(e.message))
  }, [])

  async function cargarSesion(id) {
    setCajaId(id)
    try {
      const activa = await api.get(`/api/cajas/${id}/sesion-activa`)
      setSesion(activa)
    } catch {
      setSesion(null)
    }
  }

  async function abrirCaja() {
    setMensaje(null)
    try {
      const nueva = await api.post(`/api/cajas/${cajaId}/aperturas`, {
        usuarioId: session.usuarioId,
        montoInicial: Number(montoInicial),
      })
      setSesion(nueva)
      setMensaje('Caja abierta correctamente.')
    } catch (e) {
      setMensaje(e.message)
    }
  }

  async function cerrarCaja() {
    setMensaje(null)
    try {
      const cerrada = await api.post(`/api/cajas/${cajaId}/cierres`, {
        montoDeclarado: Number(montoDeclarado),
        observacion,
      })
      setSesion(cerrada)
      setMensaje('Caja cerrada correctamente.')
    } catch (e) {
      setMensaje(e.message)
    }
  }

  return (
    <section>
      <h1>
        Apertura y cierre de caja
        <AyudaFormulario titulo="Cómo abrir y cerrar caja" pasos={AYUDA_CAJA} />
      </h1>

      <label>
        Caja
        <select value={cajaId} onChange={(e) => cargarSesion(e.target.value)}>
          <option value="">-- Selecciona una caja --</option>
          {cajas.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nombre}
            </option>
          ))}
        </select>
      </label>

      {mensaje && <p className="aviso">{mensaje}</p>}

      {cajaId && !sesion?.id && (
        <div className="tarjeta">
          <h3>Abrir caja</h3>
          <label>
            Monto inicial
            <input type="number" value={montoInicial} onChange={(e) => setMontoInicial(e.target.value)} />
          </label>
          <button onClick={abrirCaja}>Abrir caja</button>
        </div>
      )}

      {sesion?.id && sesion.estado === 'Abierta' && (
        <div className="tarjeta">
          <h3>Sesion abierta</h3>
          <p>Apertura: {new Date(sesion.fechaApertura).toLocaleString()}</p>
          <p>Monto inicial: S/ {sesion.montoInicial}</p>
          <label>
            Monto declarado (cierre)
            <input type="number" value={montoDeclarado} onChange={(e) => setMontoDeclarado(e.target.value)} />
          </label>
          <label>
            Observacion
            <input value={observacion} onChange={(e) => setObservacion(e.target.value)} />
          </label>
          <button onClick={cerrarCaja}>Cerrar caja</button>
        </div>
      )}

      {sesion?.estado === 'Cerrada' && (
        <div className="tarjeta">
          <h3>Ultimo cierre</h3>
          <p>Esperado: S/ {sesion.montoEsperado} — Declarado: S/ {sesion.montoDeclarado}</p>
          <p>Diferencia: S/ {sesion.diferencia}</p>
        </div>
      )}
    </section>
  )
}
