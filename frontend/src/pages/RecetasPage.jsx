import { useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AyudaFormulario } from '../components/AyudaFormulario'

// El valor es el que guarda ms-clientes (igual que los datos semilla); la etiqueta, la que lee el usuario.
const TIPOS_RECETA = [
  { valor: 'NORMAL', etiqueta: 'Normal' },
  { valor: 'ESPECIAL', etiqueta: 'Especial' },
  { valor: 'ESPECIAL_RETENIDA', etiqueta: 'Especial retenida' },
]

const AYUDA_RECETAS = [
  'El número de receta debe ser único.',
  'Las recetas de tipo Especial o Especial retenida requieren fecha de vencimiento propia.',
  'Una receta Especial retenida solo puede usarse una vez: tras dispensarse queda retenida y no puede reutilizarse.',
  "La receta debe ser Aprobada por un químico farmacéutico (sección 'Validar receta') antes de poder usarse en una venta.",
]

export function RecetasPage() {
  const { session } = useAuth()
  const [mensaje, setMensaje] = useState(null)
  const [recetaId, setRecetaId] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [form, setForm] = useState({
    numero: '',
    tipo: 'NORMAL',
    fechaEmision: '',
    fechaVencimiento: '',
    productoId: '',
    datosPaciente: '',
    datosProfesional: '',
    dosis: '',
    cantidadAutorizada: '',
  })

  function actualizarCampo(campo, valor) {
    setForm((prev) => ({ ...prev, [campo]: valor }))
  }

  async function registrar(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      const receta = await api.post('/api/recetas', {
        ...form,
        cantidadAutorizada: Number(form.cantidadAutorizada),
        fechaVencimiento: form.fechaVencimiento || null,
        clienteId: null,
        archivoRespaldoUrl: null,
      })
      setMensaje(`Receta registrada con id ${receta.id}`)
      setRecetaId(receta.id)
    } catch (err) {
      setMensaje(err.message)
    }
  }

  async function validar(aprobar) {
    setMensaje(null)
    try {
      await api.post('/api/recetas/validaciones', {
        recetaId,
        usuarioValidadorId: session.usuarioId,
        aprobar,
        observaciones,
      })
      setMensaje(aprobar ? 'Receta aprobada.' : 'Receta rechazada.')
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <section>
      <h1>
        Recetas
        <AyudaFormulario titulo="Cómo registrar y validar una receta" pasos={AYUDA_RECETAS} />
      </h1>

      <form className="tarjeta" onSubmit={registrar}>
        <h3>Registrar receta</h3>
        <label>
          Numero
          <input value={form.numero} onChange={(e) => actualizarCampo('numero', e.target.value)} required />
        </label>
        <label>
          Tipo
          <select value={form.tipo} onChange={(e) => actualizarCampo('tipo', e.target.value)}>
            {TIPOS_RECETA.map((t) => <option key={t.valor} value={t.valor}>{t.etiqueta}</option>)}
          </select>
        </label>
        <label>
          Fecha de emision
          <input type="date" value={form.fechaEmision} onChange={(e) => actualizarCampo('fechaEmision', e.target.value)} required />
        </label>
        {form.tipo !== 'NORMAL' && (
          <label>
            Fecha de vencimiento
            <input type="date" value={form.fechaVencimiento} onChange={(e) => actualizarCampo('fechaVencimiento', e.target.value)} required />
          </label>
        )}
        <label>
          Id del producto (medicamento controlado)
          <input value={form.productoId} onChange={(e) => actualizarCampo('productoId', e.target.value)} required />
        </label>
        <label>
          Datos del paciente
          <input value={form.datosPaciente} onChange={(e) => actualizarCampo('datosPaciente', e.target.value)} required />
        </label>
        <label>
          Datos del profesional
          <input value={form.datosProfesional} onChange={(e) => actualizarCampo('datosProfesional', e.target.value)} required />
        </label>
        <label>
          Dosis indicada
          <input value={form.dosis} onChange={(e) => actualizarCampo('dosis', e.target.value)} required />
        </label>
        <label>
          Cantidad autorizada
          <input type="number" min="1" value={form.cantidadAutorizada} onChange={(e) => actualizarCampo('cantidadAutorizada', e.target.value)} required />
        </label>
        <button type="submit">Registrar</button>
      </form>

      <div className="tarjeta">
        <h3>Validar receta (quimico farmaceutico)</h3>
        <label>
          Id de receta
          <input value={recetaId} onChange={(e) => setRecetaId(e.target.value)} />
        </label>
        <label>
          Observaciones
          <input value={observaciones} onChange={(e) => setObservaciones(e.target.value)} />
        </label>
        <button onClick={() => validar(true)}>Aprobar</button>
        <button onClick={() => validar(false)}>Rechazar</button>
      </div>

      {mensaje && <p className="aviso">{mensaje}</p>}
    </section>
  )
}
