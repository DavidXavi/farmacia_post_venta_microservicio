import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_VENTA = [
  'Selecciona la caja con turno abierto e ingresa el DNI del cliente (opcional) antes de iniciar la venta.',
  'Agrega productos uno por uno con la cantidad. Si el producto es controlado, indica el Id de una receta ya aprobada.',
  "Usa 'Ver promociones' en cada línea para aplicar automáticamente la primera promoción vigente de ese producto.",
  'Si el cliente tiene un convenio de seguro, identifícalo por DNI y aplica el convenio para calcular el copago.',
  'Registra uno o más pagos hasta cubrir el total de la venta.',
  'Elige el tipo de comprobante y la serie, y confirma la venta: esto descuenta stock por FEFO y genera el comprobante.',
  'El precio del producto no incluye IGV. Cada línea calcula: Base = (Precio × Cantidad) − Descuento, IGV = 18% de la Base, y Total línea = Base + IGV. El "Total" de la venta es la suma de los totales de línea (ya con IGV incluido).',
]

export function VentaPage() {
  const { session } = useAuth()
  const [cajas, setCajas] = useState([])
  const [productos, setProductos] = useState([])
  const [formasPago, setFormasPago] = useState([])
  const [convenios, setConvenios] = useState([])
  const [cajaId, setCajaId] = useState('')
  const [clienteDni, setClienteDni] = useState('')
  const [venta, setVenta] = useState(null)
  // Lo que cubre el seguro. Va aparte de la venta porque el backend lo calcula al
  // aplicar el convenio y no lo guarda en el total: el total sigue siendo el de la
  // venta, y lo que el cliente paga es el copago.
  const [copago, setCopago] = useState(null)
  const [mensaje, setMensaje] = useState(null)

  const [productoId, setProductoId] = useState('')
  const [cantidad, setCantidad] = useState('1')
  const [recetaId, setRecetaId] = useState('')

  const [formaPagoId, setFormaPagoId] = useState('')
  const [montoPago, setMontoPago] = useState('')
  const [convenioId, setConvenioId] = useState('')
  const [serieComprobante, setSerieComprobante] = useState('B001')
  const [tipoComprobante, setTipoComprobante] = useState('Boleta')

  useEffect(() => {
    api.get('/api/cajas').then(setCajas)
    api.get('/api/productos').then(setProductos)
    api.get('/api/formas-pago').then(setFormasPago)
  }, [])

  function mostrarError(err) {
    setMensaje(err.message)
  }

  async function iniciarVenta() {
    setMensaje(null)
    try {
      const sesion = await api.get(`/api/cajas/${cajaId}/sesion-activa`)
      const nueva = await api.post('/api/ventas', {
        cajaId,
        sesionCajaId: sesion.id,
        usuarioId: session.usuarioId,
        clienteDni: clienteDni || null,
      })
      setVenta(nueva)
    } catch (err) {
      mostrarError(err)
    }
  }

  async function agregarProducto() {
    setMensaje(null)
    try {
      const actualizada = await api.post(`/api/ventas/${venta.id}/detalles`, {
        productoId,
        cantidad: Number(cantidad),
        recetaId: recetaId || null,
      })
      setVenta(actualizada)
      setProductoId('')
      setCantidad('1')
      setRecetaId('')
    } catch (err) {
      mostrarError(err)
    }
  }

  async function verPromociones(detalleId) {
    setMensaje(null)
    try {
      const disponibles = await api.get(`/api/ventas/${venta.id}/promociones-disponibles`, { detalleVentaId: detalleId })
      if (disponibles.length === 0) {
        setMensaje('No hay promociones aplicables para esta linea.')
        return
      }
      const elegida = disponibles[0]
      const actualizada = await api.patch(`/api/ventas/${venta.id}/detalles/${detalleId}/promocion`, { promocionId: elegida.id })
      setVenta(actualizada)
      setMensaje(`Promocion aplicada: ${elegida.nombre}`)
    } catch (err) {
      mostrarError(err)
    }
  }

  async function identificarCliente() {
    setMensaje(null)
    try {
      const actualizada = await api.post(`/api/ventas/${venta.id}/cliente`, { dni: clienteDni })
      setVenta(actualizada)
      // El backend degrada a venta anonima si no encuentra el DNI. Degradar esta bien;
      // hacerlo en silencio no: el cajero creia que habia identificado al cliente.
      setMensaje(
        actualizada.clienteId
          ? `Cliente identificado: DNI ${clienteDni}`
          : `No se encontro el DNI ${clienteDni}. La venta sigue como anonima.`,
      )
    } catch (err) {
      mostrarError(err)
    }
  }

  async function aplicarConvenio() {
    setMensaje(null)
    try {
      const copago = await api.post(`/api/ventas/${venta.id}/convenio`, { convenioId })
      setCopago(copago)
      setMensaje(`Cubierto: S/ ${copago.montoCubierto} — Copago del cliente: S/ ${copago.copago}`)
      const actualizada = await api.get(`/api/ventas/${venta.id}`)
      setVenta(actualizada)
    } catch (err) {
      mostrarError(err)
    }
  }

  async function registrarPago() {
    setMensaje(null)
    try {
      const actualizada = await api.post(`/api/ventas/${venta.id}/pagos`, {
        formaPagoId,
        monto: Number(montoPago),
        codigoAutorizacion: null,
      })
      setVenta(actualizada)
      setMontoPago('')
    } catch (err) {
      mostrarError(err)
    }
  }

  async function confirmarVenta() {
    setMensaje(null)
    try {
      const confirmada = await api.post(
        `/api/ventas/${venta.id}/confirmar`,
        { tipoComprobante, serieComprobante },
        // Clave estable, no un UUID nuevo por click: confirmar dos veces la misma
        // venta nunca es legitimo, y asi un doble click devuelve la primera respuesta
        // en vez de emitir un segundo comprobante.
        { idempotencyKey: `confirmar-${venta.id}` },
      )
      setVenta(confirmada)
      // El comprobante se emite de forma asincrona: en este instante genuinamente
      // todavia no existe. Decir "Comprobante null" hacia pensar que algo fallo.
      setMensaje(
        confirmada.numeroComprobante
          ? `Venta confirmada. Comprobante ${confirmada.numeroComprobante}`
          : 'Venta confirmada. El comprobante se esta emitiendo.',
      )
    } catch (err) {
      mostrarError(err)
    }
  }

  function nuevaVenta() {
    setVenta(null)
    setClienteDni('')
    setCopago(null)
    setMensaje(null)
  }

  if (!venta) {
    return (
      <section>
        <h1>
          Punto de venta
          <AyudaFormulario titulo="Cómo registrar una venta" pasos={AYUDA_VENTA} />
        </h1>
        <div className="tarjeta">
          <label>
            Caja
            <select value={cajaId} onChange={(e) => setCajaId(e.target.value)}>
              <option value="">-- Selecciona una caja --</option>
              {cajas
                .filter((c) => c.activa)
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.localNombre ? `${c.localNombre} — ${c.nombre}` : c.nombre}
                  </option>
                ))}
            </select>
          </label>
          <label>
            DNI del cliente (opcional)
            <input value={clienteDni} onChange={(e) => setClienteDni(e.target.value)} maxLength={8} />
          </label>
          <button onClick={iniciarVenta} disabled={!cajaId}>Iniciar venta</button>
          {mensaje && <p className="error">{mensaje}</p>}
        </div>
      </section>
    )
  }

  const ventaConfirmada = venta.estado === 'Confirmada'

  return (
    <section>
      <h1>
        Venta en curso {venta.numeroComprobante ? `— ${venta.numeroComprobante}` : ''}
        <AyudaFormulario titulo="Cómo registrar una venta" pasos={AYUDA_VENTA} />
      </h1>
      <p>
        Estado: <strong>{venta.estado}</strong> — Cliente:{' '}
        {venta.clienteId ? `identificado (DNI ${clienteDni || 'registrado'})` : 'sin identificar'}
      </p>
      {mensaje && <p className="aviso">{mensaje}</p>}

      <div className="tarjeta">
        <h3>Lineas de venta</h3>
        <p className="ayuda-campo">El precio no incluye IGV. Total línea = (Precio × Cant. − Descuento) + IGV (18%).</p>
        <table>
          <thead><tr><th>Producto</th><th>Cant.</th><th>Precio (sin IGV)</th><th>Descuento</th><th>IGV</th><th>Total línea</th><th></th></tr></thead>
          <tbody>
            {venta.detalles.map((d) => (
              <tr key={d.id}>
                <td>{d.nombreProducto}</td>
                <td>{d.cantidad}</td>
                <td>S/ {d.precioUnitario}</td>
                <td>S/ {d.descuentoMonto}</td>
                <td>S/ {d.impuestoMonto}</td>
                <td>S/ {d.subtotal}</td>
                <td>{!ventaConfirmada && <button onClick={() => verPromociones(d.id)}>Ver promociones</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>
          Total sin IGV: S/ {(venta.total - venta.detalles.reduce((s, d) => s + d.impuestoMonto, 0)).toFixed(2)}
          {' '}— IGV (18%): S/ {venta.detalles.reduce((s, d) => s + d.impuestoMonto, 0).toFixed(2)}
          {' '}— Total de la venta: S/ {venta.total}
          {copago && <> {' '}— Cubre el seguro: S/ {copago.montoCubierto}</>}
          {' '}— <strong>A cobrar: S/ {copago ? copago.copago : venta.total}</strong>
          {' '}— Pagado: S/ {venta.totalPagado}
        </p>
      </div>

      {!ventaConfirmada && (
        <>
          <div className="tarjeta">
            <h3>Agregar producto</h3>
            <label>
              Producto
              <select value={productoId} onChange={(e) => setProductoId(e.target.value)}>
                <option value="">--</option>
                {productos.map((p) => <option key={p.id} value={p.id}>{p.nombreComercial} (S/ {p.precioVenta})</option>)}
              </select>
            </label>
            <label>
              Cantidad
              <input type="number" min="1" value={cantidad} onChange={(e) => setCantidad(e.target.value)} />
            </label>
            <label>
              Id de receta (si el producto es controlado)
              <input value={recetaId} onChange={(e) => setRecetaId(e.target.value)} />
            </label>
            <button onClick={agregarProducto} disabled={!productoId}>Agregar</button>
          </div>

          <div className="tarjeta">
            <h3>Cliente / Convenio</h3>
            <label>
              DNI
              <input value={clienteDni} onChange={(e) => setClienteDni(e.target.value)} maxLength={8} />
            </label>
            <button onClick={identificarCliente} disabled={!clienteDni}>Identificar cliente</button>
            <label>
              Id de convenio
              <input value={convenioId} onChange={(e) => setConvenioId(e.target.value)} />
            </label>
            <button onClick={aplicarConvenio} disabled={!convenioId}>Aplicar convenio</button>
          </div>

          <div className="tarjeta">
            <h3>Registrar pago</h3>
            <label>
              Forma de pago
              <select value={formaPagoId} onChange={(e) => setFormaPagoId(e.target.value)}>
                <option value="">--</option>
                {formasPago.map((f) => <option key={f.id} value={f.id}>{f.nombre}</option>)}
              </select>
            </label>
            <label>
              Monto
              <input type="number" step="0.01" value={montoPago} onChange={(e) => setMontoPago(e.target.value)} />
            </label>
            <button onClick={registrarPago} disabled={!formaPagoId || !montoPago}>Registrar pago</button>
          </div>

          <div className="tarjeta">
            <h3>Confirmar venta</h3>
            <label>
              Tipo de comprobante
              <select value={tipoComprobante} onChange={(e) => setTipoComprobante(e.target.value)}>
                <option value="Boleta">Boleta</option>
                <option value="Factura">Factura</option>
                <option value="Ticket">Ticket</option>
              </select>
            </label>
            <label>
              Serie
              <input value={serieComprobante} onChange={(e) => setSerieComprobante(e.target.value)} />
            </label>
            <button onClick={confirmarVenta} disabled={venta.detalles.length === 0}>Confirmar venta</button>
          </div>
        </>
      )}

      <button onClick={nuevaVenta}>Iniciar otra venta</button>
    </section>
  )
}
