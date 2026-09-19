import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_CLIENTES = [
  'El DNI debe tener exactamente 8 dígitos y es único por cliente.',
  "Usa 'Buscar por DNI' antes de registrar, para evitar duplicar un cliente ya existente.",
  'Teléfono y correo son opcionales.',
  "Usa 'Editar' en el listado para actualizar teléfono, correo o dirección de un cliente ya registrado.",
]

export function ClientesPage() {
  const [dni, setDni] = useState('')
  const [cliente, setCliente] = useState(null)
  const [clientes, setClientes] = useState([])
  const [mensaje, setMensaje] = useState(null)
  const [form, setForm] = useState({ dni: '', nombres: '', apellidos: '', telefono: '', correo: '' })
  const [editandoId, setEditandoId] = useState(null)
  const [formEdicion, setFormEdicion] = useState({ telefono: '', correo: '', direccion: '' })

  function cargarClientes() {
    api.get('/api/clientes').then(setClientes).catch((e) => setMensaje(e.message))
  }

  useEffect(() => {
    cargarClientes()
  }, [])

  async function buscar() {
    setMensaje(null)
    setCliente(null)
    try {
      const encontrado = await api.get(`/api/clientes/por-dni/${dni}`)
      setCliente(encontrado)
    } catch (err) {
      setMensaje('No se encontro un cliente con ese DNI.')
    }
  }

  function actualizarCampo(campo, valor) {
    setForm((prev) => ({ ...prev, [campo]: valor }))
  }

  async function registrar(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      const nuevo = await api.post('/api/clientes', {
        ...form,
        fechaNacimiento: null,
        direccion: null,
      })
      setMensaje('Cliente registrado.')
      setCliente(nuevo)
      cargarClientes()
    } catch (err) {
      setMensaje(err.message)
    }
  }

  function editar(c) {
    setEditandoId(c.id)
    setFormEdicion({ telefono: c.telefono || '', correo: c.correo || '', direccion: c.direccion || '' })
  }

  function cancelarEdicion() {
    setEditandoId(null)
    setFormEdicion({ telefono: '', correo: '', direccion: '' })
  }

  async function guardarEdicion(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      await api.patch(`/api/clientes/${editandoId}`, formEdicion)
      setMensaje('Cliente actualizado.')
      cancelarEdicion()
      cargarClientes()
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <section>
      <h1>
        Clientes
        <AyudaFormulario titulo="Cómo registrar un cliente" pasos={AYUDA_CLIENTES} />
      </h1>

      <div className="tarjeta">
        <h3>Buscar por DNI</h3>
        <label>
          DNI
          <input value={dni} onChange={(e) => setDni(e.target.value)} maxLength={8} />
        </label>
        <button onClick={buscar}>Buscar</button>

        {cliente && (
          <div className="resultado">
            <p><strong>{cliente.nombres} {cliente.apellidos}</strong></p>
            <p>DNI: {cliente.dni} — Estado: {cliente.estado}</p>
            <p>Telefono: {cliente.telefono || '-'} — Correo: {cliente.correo || '-'}</p>
          </div>
        )}
      </div>

      <form className="tarjeta" onSubmit={registrar}>
        <h3>Registrar cliente</h3>
        <label>
          DNI
          <input value={form.dni} onChange={(e) => actualizarCampo('dni', e.target.value)} maxLength={8} required />
        </label>
        <label>
          Nombres
          <input value={form.nombres} onChange={(e) => actualizarCampo('nombres', e.target.value)} required />
        </label>
        <label>
          Apellidos
          <input value={form.apellidos} onChange={(e) => actualizarCampo('apellidos', e.target.value)} required />
        </label>
        <label>
          Telefono
          <input value={form.telefono} onChange={(e) => actualizarCampo('telefono', e.target.value)} />
        </label>
        <label>
          Correo
          <input value={form.correo} onChange={(e) => actualizarCampo('correo', e.target.value)} />
        </label>
        <button type="submit">Registrar</button>
      </form>

      {editandoId && (
        <form className="tarjeta" onSubmit={guardarEdicion}>
          <h3>Editar cliente</h3>
          <label>
            Telefono
            <input value={formEdicion.telefono} onChange={(e) => setFormEdicion((prev) => ({ ...prev, telefono: e.target.value }))} />
          </label>
          <label>
            Correo
            <input value={formEdicion.correo} onChange={(e) => setFormEdicion((prev) => ({ ...prev, correo: e.target.value }))} />
          </label>
          <label>
            Direccion
            <input value={formEdicion.direccion} onChange={(e) => setFormEdicion((prev) => ({ ...prev, direccion: e.target.value }))} />
          </label>
          <button type="submit">Guardar cambios</button>
          <button type="button" onClick={cancelarEdicion}>Cancelar</button>
        </form>
      )}

      {mensaje && <p className="aviso">{mensaje}</p>}

      <div className="tarjeta">
        <h3>Listado de clientes</h3>
        <table>
          <thead>
            <tr><th>DNI</th><th>Nombre</th><th>Telefono</th><th>Correo</th><th>Direccion</th><th>Estado</th><th></th></tr>
          </thead>
          <tbody>
            {clientes.map((c) => (
              <tr key={c.id}>
                <td>{c.dni}</td>
                <td>{c.nombres} {c.apellidos}</td>
                <td>{c.telefono || '-'}</td>
                <td>{c.correo || '-'}</td>
                <td>{c.direccion || '-'}</td>
                <td>{c.estado}</td>
                <td><button onClick={() => editar(c)}>Editar</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
