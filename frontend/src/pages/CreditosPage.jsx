import { useState } from 'react'
import { api } from '../api/client'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_CREDITOS = [
  'Busca primero al cliente por DNI: si no existe, regístralo en el módulo Clientes.',
  "Si el cliente no tiene línea de crédito, verás 'Sin línea de crédito registrada' — puedes registrar una nueva abajo.",
  'El monto autorizado es el límite de crédito; el saldo disponible disminuye con cada consumo a crédito en una venta.',
]

export function CreditosPage() {
  const [dni, setDni] = useState('')
  const [cliente, setCliente] = useState(null)
  const [lineaActual, setLineaActual] = useState(null)
  const [mensaje, setMensaje] = useState(null)
  const [form, setForm] = useState({ montoAutorizado: '', vigenciaInicio: '', vigenciaFin: '' })

  async function buscar() {
    setMensaje(null)
    setCliente(null)
    setLineaActual(null)
    let encontrado
    try {
      encontrado = await api.get(`/api/clientes/por-dni/${dni}`)
      setCliente(encontrado)
    } catch (err) {
      setMensaje('No se encontro un cliente con ese DNI.')
      return
    }
    try {
      setLineaActual(await api.get(`/api/creditos/cliente/${encontrado.id}`))
    } catch (err) {
      setLineaActual(null)
    }
  }

  async function registrar(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      const linea = await api.post('/api/lineas-credito', {
        clienteId: cliente.id,
        montoAutorizado: Number(form.montoAutorizado),
        vigenciaInicio: form.vigenciaInicio || null,
        vigenciaFin: form.vigenciaFin || null,
      })
      setLineaActual(linea)
      setMensaje('Linea de credito registrada.')
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <section>
      <h1>
        Lineas de credito
        <AyudaFormulario titulo="Cómo registrar una línea de crédito" pasos={AYUDA_CREDITOS} />
      </h1>

      <div className="tarjeta">
        <h3>Buscar cliente por DNI</h3>
        <label>
          DNI
          <input value={dni} onChange={(e) => setDni(e.target.value)} maxLength={8} />
        </label>
        <button onClick={buscar}>Buscar</button>

        {cliente && (
          <div className="resultado">
            <p><strong>{cliente.nombres} {cliente.apellidos}</strong> — DNI: {cliente.dni}</p>
            {lineaActual ? (
              <p>
                Linea vigente: S/ {lineaActual.saldoDisponible} disponible de S/ {lineaActual.montoAutorizado} — Estado: {lineaActual.estado}
              </p>
            ) : (
              <p>Sin linea de credito registrada.</p>
            )}
          </div>
        )}
      </div>

      {cliente && (
        <form className="tarjeta" onSubmit={registrar}>
          <h3>Registrar linea de credito</h3>
          <label>
            Monto autorizado
            <input type="number" step="0.01" value={form.montoAutorizado}
              onChange={(e) => setForm((p) => ({ ...p, montoAutorizado: e.target.value }))} required />
          </label>
          <label>
            Vigencia inicio
            <input type="date" value={form.vigenciaInicio} onChange={(e) => setForm((p) => ({ ...p, vigenciaInicio: e.target.value }))} />
          </label>
          <label>
            Vigencia fin
            <input type="date" value={form.vigenciaFin} onChange={(e) => setForm((p) => ({ ...p, vigenciaFin: e.target.value }))} />
          </label>
          <button type="submit">Registrar</button>
        </form>
      )}

      {mensaje && <p className="aviso">{mensaje}</p>}
    </section>
  )
}
