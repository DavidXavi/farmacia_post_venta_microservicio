import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_CATALOGOS = [
  'Categorías, Laboratorios, Presentaciones, Formas de pago y Locales son catálogos simples: solo piden un nombre (y en Presentaciones/Formas de pago un dato adicional).',
  'Estos catálogos alimentan los selectores de Productos, Lotes y Usuarios — créalos aquí antes de usarlos en esos formularios.',
  'Las Reglas de incentivo asocian una comisión por unidad vendida a un producto o a una categoría (no ambos a la vez), dentro de un rango de fechas de vigencia.',
]

const TIPOS_FORMA_PAGO = ['Efectivo', 'TarjetaDebito', 'TarjetaCredito', 'Transferencia', 'BilleteraDigital', 'CopagoSeguro', 'CreditoFarmacia', 'Otro']

function SeccionSimple({ titulo, items, campos, onCrear }) {
  const vacio = Object.fromEntries(campos.map((c) => [c.nombre, c.tipo === 'checkbox' ? false : '']))
  const [form, setForm] = useState(vacio)
  const [mensaje, setMensaje] = useState(null)

  async function crear(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      await onCrear(form)
      setForm(vacio)
      setMensaje(`${titulo} registrado.`)
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <div className="tarjeta">
      <h3>{titulo}</h3>
      <form onSubmit={crear}>
        {campos.map((campo) => (
          <label key={campo.nombre} className={campo.tipo === 'checkbox' ? 'checkbox' : undefined}>
            {campo.tipo !== 'checkbox' && campo.label}
            {campo.tipo === 'select' ? (
              <select value={form[campo.nombre]} onChange={(e) => setForm((p) => ({ ...p, [campo.nombre]: e.target.value }))} required={campo.requerido}>
                <option value="">--</option>
                {campo.opciones.map((o) => <option key={o} value={o}>{o}</option>)}
              </select>
            ) : (
              <input
                type={campo.tipo || 'text'}
                checked={campo.tipo === 'checkbox' ? form[campo.nombre] : undefined}
                value={campo.tipo === 'checkbox' ? undefined : form[campo.nombre]}
                onChange={(e) => setForm((p) => ({ ...p, [campo.nombre]: campo.tipo === 'checkbox' ? e.target.checked : e.target.value }))}
                required={campo.requerido}
              />
            )}
            {campo.tipo === 'checkbox' && campo.label}
          </label>
        ))}
        <button type="submit">Registrar</button>
      </form>
      {mensaje && <p className="aviso">{mensaje}</p>}
      <table>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}><td>{item.nombre}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function CatalogosPage() {
  const [categorias, setCategorias] = useState([])
  const [laboratorios, setLaboratorios] = useState([])
  const [presentaciones, setPresentaciones] = useState([])
  const [formasPago, setFormasPago] = useState([])
  const [locales, setLocales] = useState([])
  const [productos, setProductos] = useState([])
  const [reglasIncentivo, setReglasIncentivo] = useState([])

  function recargarTodo() {
    api.get('/api/categorias').then(setCategorias)
    api.get('/api/laboratorios').then(setLaboratorios)
    api.get('/api/presentaciones').then(setPresentaciones)
    api.get('/api/formas-pago').then(setFormasPago)
    api.get('/api/locales').then(setLocales)
    api.get('/api/productos').then(setProductos)
    api.get('/api/reglas-incentivo').then(setReglasIncentivo)
  }

  useEffect(recargarTodo, [])

  return (
    <section>
      <h1>
        Catalogos
        <AyudaFormulario titulo="Cómo usar los catálogos" pasos={AYUDA_CATALOGOS} />
      </h1>

      <SeccionSimple
        titulo="Categorias"
        items={categorias}
        campos={[{ nombre: 'nombre', label: 'Nombre', requerido: true }]}
        onCrear={async (form) => { await api.post('/api/categorias', { nombre: form.nombre }); recargarTodo() }}
      />

      <SeccionSimple
        titulo="Laboratorios"
        items={laboratorios}
        campos={[{ nombre: 'nombre', label: 'Nombre', requerido: true }]}
        onCrear={async (form) => { await api.post('/api/laboratorios', { nombre: form.nombre }); recargarTodo() }}
      />

      <SeccionSimple
        titulo="Presentaciones"
        items={presentaciones}
        campos={[
          { nombre: 'nombre', label: 'Nombre', requerido: true },
          { nombre: 'unidadMedida', label: 'Unidad de medida', requerido: true },
        ]}
        onCrear={async (form) => { await api.post('/api/presentaciones', form); recargarTodo() }}
      />

      <SeccionSimple
        titulo="Formas de pago"
        items={formasPago}
        campos={[
          { nombre: 'nombre', label: 'Nombre', requerido: true },
          { nombre: 'tipo', label: 'Tipo', tipo: 'select', opciones: TIPOS_FORMA_PAGO, requerido: true },
        ]}
        onCrear={async (form) => { await api.post('/api/formas-pago', form); recargarTodo() }}
      />

      <SeccionSimple
        titulo="Locales"
        items={locales}
        campos={[
          { nombre: 'nombre', label: 'Nombre', requerido: true },
          { nombre: 'direccion', label: 'Direccion', requerido: true },
        ]}
        onCrear={async (form) => { await api.post('/api/locales', form); recargarTodo() }}
      />

      <div className="tarjeta">
        <h3>Reglas de incentivo</h3>
        <ReglaIncentivoForm productos={productos} categorias={categorias} onCrear={async (body) => { await api.post('/api/reglas-incentivo', body); recargarTodo() }} />
        <table>
          <thead><tr><th>Nombre</th><th>Monto/unidad</th><th>Vigencia</th><th>Activa</th></tr></thead>
          <tbody>
            {reglasIncentivo.map((r) => (
              <tr key={r.id}>
                <td>{r.nombre}</td>
                <td>S/ {r.montoPorUnidad}</td>
                <td>{r.fechaInicio} a {r.fechaFin}</td>
                <td>{r.activa ? 'Si' : 'No'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function ReglaIncentivoForm({ productos, categorias, onCrear }) {
  const [form, setForm] = useState({ nombre: '', productoId: '', categoriaId: '', montoPorUnidad: '', fechaInicio: '', fechaFin: '' })
  const [mensaje, setMensaje] = useState(null)

  async function crear(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      await onCrear({
        nombre: form.nombre,
        productoId: form.productoId || null,
        categoriaId: form.categoriaId || null,
        montoPorUnidad: Number(form.montoPorUnidad),
        fechaInicio: form.fechaInicio,
        fechaFin: form.fechaFin,
      })
      setMensaje('Regla registrada.')
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <form onSubmit={crear}>
      <label>Nombre<input value={form.nombre} onChange={(e) => setForm((p) => ({ ...p, nombre: e.target.value }))} required /></label>
      <label>
        Producto (opcional)
        <select value={form.productoId} onChange={(e) => setForm((p) => ({ ...p, productoId: e.target.value }))}>
          <option value="">--</option>
          {productos.map((p) => <option key={p.id} value={p.id}>{p.nombreComercial}</option>)}
        </select>
      </label>
      <label>
        Categoria (opcional)
        <select value={form.categoriaId} onChange={(e) => setForm((p) => ({ ...p, categoriaId: e.target.value }))}>
          <option value="">--</option>
          {categorias.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </select>
      </label>
      <label>Monto por unidad<input type="number" step="0.01" value={form.montoPorUnidad} onChange={(e) => setForm((p) => ({ ...p, montoPorUnidad: e.target.value }))} required /></label>
      <label>Fecha inicio<input type="date" value={form.fechaInicio} onChange={(e) => setForm((p) => ({ ...p, fechaInicio: e.target.value }))} required /></label>
      <label>Fecha fin<input type="date" value={form.fechaFin} onChange={(e) => setForm((p) => ({ ...p, fechaFin: e.target.value }))} required /></label>
      <button type="submit">Registrar regla</button>
      {mensaje && <p className="aviso">{mensaje}</p>}
    </form>
  )
}
