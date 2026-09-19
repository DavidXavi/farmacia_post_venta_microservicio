import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { AyudaFormulario } from '../components/AyudaFormulario'

const TIPOS_PRODUCTO = ['Medicamento', 'Otc', 'Otro']

const AYUDA_PRODUCTOS = [
  'Código interno y nombre comercial son obligatorios y deben ser únicos.',
  'Selecciona categoría, laboratorio y presentación ya existentes — créalos primero en Catálogos si faltan en la lista.',
  "Marca 'Producto controlado' solo para medicamentos que exigen receta médica; si lo marcas, activa también 'Requiere receta'.",
  'El precio de venta no incluye IGV: el sistema lo calcula automáticamente al momento de la venta.',
]

export function ProductosPage() {
  const [productos, setProductos] = useState([])
  const [categorias, setCategorias] = useState([])
  const [laboratorios, setLaboratorios] = useState([])
  const [presentaciones, setPresentaciones] = useState([])
  const [texto, setTexto] = useState('')
  const [mensaje, setMensaje] = useState(null)
  const [form, setForm] = useState({
    codigoInterno: '',
    nombreComercial: '',
    descripcion: '',
    tipoProducto: 'Otc',
    categoriaId: '',
    laboratorioId: '',
    presentacionId: '',
    precioVenta: '',
    esControlado: false,
    requiereReceta: false,
  })

  function cargarProductos(filtro) {
    api.get('/api/productos', { buscar: filtro }).then(setProductos).catch((e) => setMensaje(e.message))
  }

  useEffect(() => {
    cargarProductos('')
    api.get('/api/categorias').then(setCategorias)
    api.get('/api/laboratorios').then(setLaboratorios)
    api.get('/api/presentaciones').then(setPresentaciones)
  }, [])

  function actualizarCampo(campo, valor) {
    setForm((prev) => ({ ...prev, [campo]: valor }))
  }

  async function registrarProducto(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      await api.post('/api/productos', {
        ...form,
        precioVenta: Number(form.precioVenta),
        codigoBarras: null,
        tipoRecetaRequerida: form.requiereReceta ? 'Normal' : null,
      })
      setMensaje('Producto registrado.')
      cargarProductos(texto)
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <section>
      <h1>
        Productos
        <AyudaFormulario titulo="Cómo registrar un producto" pasos={AYUDA_PRODUCTOS} />
      </h1>

      <form className="tarjeta" onSubmit={registrarProducto}>
        <h3>Nuevo producto</h3>
        <label>
          Codigo interno
          <input value={form.codigoInterno} onChange={(e) => actualizarCampo('codigoInterno', e.target.value)} required />
        </label>
        <label>
          Nombre comercial
          <input value={form.nombreComercial} onChange={(e) => actualizarCampo('nombreComercial', e.target.value)} required />
        </label>
        <label>
          Descripcion
          <input value={form.descripcion} onChange={(e) => actualizarCampo('descripcion', e.target.value)} />
        </label>
        <label>
          Tipo
          <select value={form.tipoProducto} onChange={(e) => actualizarCampo('tipoProducto', e.target.value)}>
            {TIPOS_PRODUCTO.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </label>
        <label>
          Categoria
          <select value={form.categoriaId} onChange={(e) => actualizarCampo('categoriaId', e.target.value)} required>
            <option value="">--</option>
            {categorias.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </label>
        <label>
          Laboratorio
          <select value={form.laboratorioId} onChange={(e) => actualizarCampo('laboratorioId', e.target.value)} required>
            <option value="">--</option>
            {laboratorios.map((l) => <option key={l.id} value={l.id}>{l.nombre}</option>)}
          </select>
        </label>
        <label>
          Presentacion
          <select value={form.presentacionId} onChange={(e) => actualizarCampo('presentacionId', e.target.value)} required>
            <option value="">--</option>
            {presentaciones.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
          </select>
        </label>
        <label>
          Precio de venta
          <input type="number" step="0.01" value={form.precioVenta} onChange={(e) => actualizarCampo('precioVenta', e.target.value)} required />
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={form.esControlado} onChange={(e) => actualizarCampo('esControlado', e.target.checked)} />
          Producto controlado
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={form.requiereReceta} onChange={(e) => actualizarCampo('requiereReceta', e.target.checked)} />
          Requiere receta
        </label>
        <button type="submit">Registrar producto</button>
      </form>

      {mensaje && <p className="aviso">{mensaje}</p>}

      <div className="tarjeta">
        <label>
          Buscar
          <input value={texto} onChange={(e) => { setTexto(e.target.value); cargarProductos(e.target.value) }} placeholder="Nombre del producto" />
        </label>
        <table>
          <thead>
            <tr><th>Codigo</th><th>Nombre</th><th>Precio</th><th>Controlado</th><th>Estado</th></tr>
          </thead>
          <tbody>
            {productos.map((p) => (
              <tr key={p.id}>
                <td>{p.codigoInterno}</td>
                <td>{p.nombreComercial}</td>
                <td>S/ {p.precioVenta}</td>
                <td>{p.esControlado ? 'Si' : 'No'}</td>
                <td>{p.estado}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
