import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { AyudaFormulario } from '../components/AyudaFormulario'

const AYUDA_USUARIOS = [
  'El nombre de usuario y la contraseña son obligatorios para el inicio de sesión.',
  'Selecciona el local donde trabajará el usuario.',
  'Marca uno o más roles: los roles determinan qué acciones y pantallas puede usar (por ejemplo, solo un Administrador puede ver Auditoría).',
]

export function UsuariosPage() {
  const [usuarios, setUsuarios] = useState([])
  const [roles, setRoles] = useState([])
  const [locales, setLocales] = useState([])
  const [mensaje, setMensaje] = useState(null)
  const [form, setForm] = useState({ nombreUsuario: '', password: '', localId: '', roles: [] })

  function cargarUsuarios() {
    api.get('/api/usuarios').then(setUsuarios).catch((e) => setMensaje(e.message))
  }

  useEffect(() => {
    cargarUsuarios()
    api.get('/api/roles').then(setRoles)
    api.get('/api/locales').then(setLocales)
  }, [])

  function actualizarCampo(campo, valor) {
    setForm((prev) => ({ ...prev, [campo]: valor }))
  }

  function alternarRol(nombreRol) {
    setForm((prev) => ({
      ...prev,
      roles: prev.roles.includes(nombreRol)
        ? prev.roles.filter((r) => r !== nombreRol)
        : [...prev.roles, nombreRol],
    }))
  }

  async function registrar(e) {
    e.preventDefault()
    setMensaje(null)
    try {
      await api.post('/api/usuarios', form)
      setMensaje('Usuario registrado.')
      setForm({ nombreUsuario: '', password: '', localId: '', roles: [] })
      cargarUsuarios()
    } catch (err) {
      setMensaje(err.message)
    }
  }

  return (
    <section>
      <h1>
        Usuarios
        <AyudaFormulario titulo="Cómo registrar un usuario" pasos={AYUDA_USUARIOS} />
      </h1>

      <form className="tarjeta" onSubmit={registrar}>
        <h3>Nuevo usuario</h3>
        <label>
          Nombre de usuario
          <input autoComplete="off" value={form.nombreUsuario} onChange={(e) => actualizarCampo('nombreUsuario', e.target.value)} required />
        </label>
        <label>
          Contrasena
          <input type="password" autoComplete="new-password" value={form.password} onChange={(e) => actualizarCampo('password', e.target.value)} required />
        </label>
        <label>
          Local asignado
          <select value={form.localId} onChange={(e) => actualizarCampo('localId', e.target.value)} required>
            <option value="">--</option>
            {locales.map((l) => <option key={l.id} value={l.id}>{l.nombre}</option>)}
          </select>
        </label>
        <label>Roles</label>
        {roles.map((r) => (
          <label key={r.id} className="checkbox">
            <input type="checkbox" checked={form.roles.includes(r.nombre)} onChange={() => alternarRol(r.nombre)} />
            {r.nombre}
          </label>
        ))}
        <button type="submit">Registrar usuario</button>
      </form>

      {mensaje && <p className="aviso">{mensaje}</p>}

      <div className="tarjeta">
        <table>
          <thead>
            <tr><th>Usuario</th><th>Roles</th><th>Estado</th></tr>
          </thead>
          <tbody>
            {usuarios.map((u) => (
              <tr key={u.id}>
                <td>{u.nombreUsuario}</td>
                <td>{u.roles.join(', ')}</td>
                <td>{u.estado}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
