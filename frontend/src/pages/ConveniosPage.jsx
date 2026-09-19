import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_CONVENIOS = [
  'Primero registra el convenio (nombre del seguro/EPS).',
  'Luego configura la cobertura: qué porcentaje cubre el convenio para cada producto específico.',
  'Por último, afilia al cliente (buscado por DNI) al convenio, con una vigencia opcional.',
  'El copago se calcula automáticamente en la venta solo si la afiliación del cliente está activa y vigente en la fecha de la venta.',
]

export function ConveniosPage() {
  const [convenios, setConvenios] = useState([])
  const [productos, setProductos] = useState([])
  const [mensaje, setMensaje] = useState(null)
  const [nombreConvenio, setNombreConvenio] = useState('')
  const [cobertura, setCobertura] = useState({ convenioId: '', productoId: '', porcentajeCubierto: '' })
  const [afiliacion, setAfiliacion] = useState({ dni: '', convenioId: '', vigenciaInicio: '', vigenciaFin: '' })

  function cargarConvenios() {
    api.get('/api/convenios').then(setConvenios).catch((e) => setMensaje(e.message))
  }

  useEffect(() => {
    cargarConvenios()
    api.get('/api/productos').then(setProductos)
  }, [])

  async function registrarConvenio(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      await api.post('/api/convenios', { nombre: nombreConvenio })
      setMensaje('Convenio registrado.')
      setNombreConvenio('')
      cargarConvenios()
    } catch (err) {
      setMensaje(err.message)
    }
  }

  async function configurarCobertura(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      await api.post(`/api/convenios/${cobertura.convenioId}/coberturas`, {
        productoId: cobertura.productoId,
        porcentajeCubierto: Number(cobertura.porcentajeCubierto),
      })
      setMensaje('Cobertura configurada.')
    } catch (err) {
      setMensaje(err.message)
    }
  }

  async function registrarAfiliacion(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      const cliente = await api.get(`/api/clientes/por-dni/${afiliacion.dni}`)
      await api.post('/api/convenios/afiliaciones', {
        clienteId: cliente.id,
        convenioId: afiliacion.convenioId,
        vigenciaInicio: afiliacion.vigenciaInicio || null,
        vigenciaFin: afiliacion.vigenciaFin || null,
      })
      setMensaje('Afiliacion registrada.')
    } catch (err) {
      setMensaje(err.message || 'No se encontro un cliente con ese DNI.')
    }
  }

  return (
    <section>
      <h1>
        Convenios de seguro
        <AyudaFormulario titulo="Cómo registrar un convenio" pasos={AYUDA_CONVENIOS} />
      </h1>

      <form className="tarjeta" onSubmit={registrarConvenio}>
        <h3>Nuevo convenio</h3>
        <label>
          Nombre
          <input value={nombreConvenio} onChange={(e) => setNombreConvenio(e.target.value)} required />
        </label>
        <button type="submit">Registrar convenio</button>
      </form>

      <form className="tarjeta" onSubmit={configurarCobertura}>
        <h3>Configurar cobertura</h3>
        <label>
          Convenio
          <select value={cobertura.convenioId} onChange={(e) => setCobertura((p) => ({ ...p, convenioId: e.target.value }))} required>
            <option value="">--</option>
            {convenios.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </label>
        <label>
          Producto
          <select value={cobertura.productoId} onChange={(e) => setCobertura((p) => ({ ...p, productoId: e.target.value }))} required>
            <option value="">--</option>
            {productos.map((p) => <option key={p.id} value={p.id}>{p.nombreComercial}</option>)}
          </select>
        </label>
        <label>
          Porcentaje cubierto
          <input type="number" min="0" max="100" step="0.01" value={cobertura.porcentajeCubierto}
            onChange={(e) => setCobertura((p) => ({ ...p, porcentajeCubierto: e.target.value }))} required />
        </label>
        <button type="submit">Guardar cobertura</button>
      </form>

      <form className="tarjeta" onSubmit={registrarAfiliacion}>
        <h3>Afiliar cliente</h3>
        <label>
          DNI del cliente
          <input value={afiliacion.dni} maxLength={8} onChange={(e) => setAfiliacion((p) => ({ ...p, dni: e.target.value }))} required />
        </label>
        <label>
          Convenio
          <select value={afiliacion.convenioId} onChange={(e) => setAfiliacion((p) => ({ ...p, convenioId: e.target.value }))} required>
            <option value="">--</option>
            {convenios.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </label>
        <label>
          Vigencia inicio
          <input type="date" value={afiliacion.vigenciaInicio} onChange={(e) => setAfiliacion((p) => ({ ...p, vigenciaInicio: e.target.value }))} />
        </label>
        <label>
          Vigencia fin
          <input type="date" value={afiliacion.vigenciaFin} onChange={(e) => setAfiliacion((p) => ({ ...p, vigenciaFin: e.target.value }))} />
        </label>
        <button type="submit">Registrar afiliacion</button>
      </form>

      {mensaje && <p className="aviso">{mensaje}</p>}

      <div className="tarjeta">
        <table>
          <thead>
            <tr><th>Nombre</th><th>Activo</th></tr>
          </thead>
          <tbody>
            {convenios.map((c) => (
              <tr key={c.id}>
                <td>{c.nombre}</td>
                <td>{c.activo ? 'Si' : 'No'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
