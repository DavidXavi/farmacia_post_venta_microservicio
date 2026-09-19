import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AyudaFormulario } from '../components/AyudaFormulario'

const TIPOS_BENEFICIO = ['DescuentoPorcentaje', 'DescuentoMonto', 'LlevaNPagaM']

const AYUDA_VALOR_BENEFICIO = {
  DescuentoPorcentaje: 'Valor del beneficio = porcentaje de descuento sobre el precio de la línea. Ej: 10 = 10% de descuento.',
  DescuentoMonto: 'Valor del beneficio = monto fijo en soles que se descuenta de la línea (nunca más que el total de la línea).',
  LlevaNPagaM: 'Valor del beneficio = el N de "lleva N y una unidad sale gratis". Ej: 3 = cada 3 unidades compradas, 1 es gratis.',
}

const AYUDA_PROMOCIONES = [
  "Elige el tipo de beneficio: Descuento por porcentaje, Descuento por monto fijo, o 'Lleva N Paga M' (unidades gratis según cantidad).",
  'Valor del beneficio cambia de significado según el tipo elegido: % de descuento, S/ de descuento, o la N de "lleva N paga N-1". El formulario muestra la explicación exacta al elegir el tipo.',
  'Cantidad mínima = unidades del producto participante que el cliente debe llevar en la venta para que la promoción se active (no soles, unidades).',
  "Marca 'Requiere cliente identificado' solo si la promoción exige tener un cliente asociado a la venta.",
  'Selecciona los productos participantes: la promoción no se aplica a productos fuera de esta lista.',
  'Una venta nunca aplica la misma promoción dos veces, ni más de una promoción por línea.',
]

const FORM_VACIO = {
  nombre: '',
  descripcion: '',
  tipoBeneficio: 'DescuentoPorcentaje',
  valorBeneficio: '',
  requiereCliente: false,
  cantidadMinima: 1,
  vigenciaInicio: '',
  vigenciaFin: '',
  productosParticipantes: [],
}

export function PromocionesPage() {
  const { session } = useAuth()
  const [promociones, setPromociones] = useState([])
  const [productos, setProductos] = useState([])
  const [mensaje, setMensaje] = useState(null)
  const [editandoId, setEditandoId] = useState(null)
  const [form, setForm] = useState(FORM_VACIO)

  function cargarPromociones() {
    api.get('/api/promociones').then(setPromociones).catch((e) => setMensaje(e.message))
  }

  useEffect(() => {
    cargarPromociones()
    api.get('/api/productos').then(setProductos)
  }, [])

  function actualizarCampo(campo, valor) {
    setForm((prev) => ({ ...prev, [campo]: valor }))
  }

  function alternarProducto(productoId) {
    setForm((prev) => ({
      ...prev,
      productosParticipantes: prev.productosParticipantes.includes(productoId)
        ? prev.productosParticipantes.filter((id) => id !== productoId)
        : [...prev.productosParticipantes, productoId],
    }))
  }

  async function registrar(e) {
    e.preventDefault()
    setMensaje(null)
    const payload = {
      ...form,
      valorBeneficio: Number(form.valorBeneficio),
      cantidadMinima: Number(form.cantidadMinima),
      vigenciaInicio: form.vigenciaInicio || null,
      vigenciaFin: form.vigenciaFin || null,
    }
    try {
      if (editandoId) {
        await api.put(`/api/promociones/${editandoId}`, payload)
        setMensaje('Promocion actualizada.')
      } else {
        await api.post('/api/promociones', payload)
        setMensaje('Promocion registrada.')
      }
      cancelarEdicion()
      cargarPromociones()
    } catch (err) {
      setMensaje(err.message)
    }
  }

  function editar(p) {
    setEditandoId(p.id)
    setForm({
      nombre: p.nombre,
      descripcion: p.descripcion,
      tipoBeneficio: p.tipoBeneficio,
      valorBeneficio: p.valorBeneficio,
      requiereCliente: p.requiereCliente,
      cantidadMinima: p.cantidadMinima,
      vigenciaInicio: p.vigenciaInicio ?? '',
      vigenciaFin: p.vigenciaFin ?? '',
      productosParticipantes: p.productosParticipantes ?? [],
    })
  }

  function cancelarEdicion() {
    setEditandoId(null)
    setForm(FORM_VACIO)
  }

  async function desactivar(id) {
    try {
      await api.patch(`/api/promociones/${id}/desactivar`)
      cargarPromociones()
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <section>
      <h1>
        Promociones
        <AyudaFormulario titulo="Cómo registrar una promoción" pasos={AYUDA_PROMOCIONES} />
      </h1>

      <form className="tarjeta" onSubmit={registrar}>
        <h3>{editandoId ? 'Editar promocion' : 'Nueva promocion'}</h3>
        <label>
          Nombre
          <input value={form.nombre} onChange={(e) => actualizarCampo('nombre', e.target.value)} required />
        </label>
        <label>
          Descripcion
          <input value={form.descripcion} onChange={(e) => actualizarCampo('descripcion', e.target.value)} />
        </label>
        <label>
          Tipo de beneficio
          <select value={form.tipoBeneficio} onChange={(e) => actualizarCampo('tipoBeneficio', e.target.value)}>
            {TIPOS_BENEFICIO.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
        <label>
          Valor del beneficio
          <input type="number" step="0.01" value={form.valorBeneficio} onChange={(e) => actualizarCampo('valorBeneficio', e.target.value)} required />
        </label>
        <p className="ayuda-campo">{AYUDA_VALOR_BENEFICIO[form.tipoBeneficio]}</p>
        <label>
          Cantidad minima
          <input type="number" min="1" value={form.cantidadMinima} onChange={(e) => actualizarCampo('cantidadMinima', e.target.value)} required />
        </label>
        <p className="ayuda-campo">Unidades mínimas del producto en la venta para activar la promoción (no es un monto en soles).</p>
        <label>
          Vigencia inicio
          <input type="date" value={form.vigenciaInicio} onChange={(e) => actualizarCampo('vigenciaInicio', e.target.value)} />
        </label>
        <label>
          Vigencia fin
          <input type="date" value={form.vigenciaFin} onChange={(e) => actualizarCampo('vigenciaFin', e.target.value)} />
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={form.requiereCliente} onChange={(e) => actualizarCampo('requiereCliente', e.target.checked)} />
          Requiere cliente identificado
        </label>
        <label>Productos participantes</label>
        <div className="resultado">
          {productos.map((p) => (
            <label key={p.id} className="checkbox">
              <input type="checkbox" checked={form.productosParticipantes.includes(p.id)} onChange={() => alternarProducto(p.id)} />
              {p.nombreComercial}
            </label>
          ))}
        </div>
        <button type="submit">{editandoId ? 'Guardar cambios' : 'Registrar promocion'}</button>
        {editandoId && <button type="button" onClick={cancelarEdicion}>Cancelar edicion</button>}
      </form>

      {mensaje && <p className="aviso">{mensaje}</p>}

      <div className="tarjeta">
        <table>
          <thead>
            <tr><th>Nombre</th><th>Tipo</th><th>Valor</th><th>Vigencia</th><th>Activa</th><th></th></tr>
          </thead>
          <tbody>
            {promociones.map((p) => (
              <tr key={p.id}>
                <td>{p.nombre}</td>
                <td>{p.tipoBeneficio}</td>
                <td>{p.valorBeneficio}</td>
                <td>{p.vigenciaInicio ?? '-'} a {p.vigenciaFin ?? '-'}</td>
                <td>{p.activa ? 'Si' : 'No'}</td>
                <td>
                  <button onClick={() => editar(p)}>Editar</button>
                  {' '}
                  {p.activa && <button onClick={() => desactivar(p.id)}>Desactivar</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
