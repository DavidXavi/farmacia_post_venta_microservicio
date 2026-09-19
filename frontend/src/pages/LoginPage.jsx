import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { BASE_URL } from '../api/client'

const PROVEEDORES = [
  { id: 'google', label: 'Continuar con Google', icono: 'fa-google' },
  { id: 'facebook', label: 'Continuar con Facebook', icono: 'fa-facebook-f' },
]

export function LoginPage() {
  const { login, verificarMfa, iniciarSesionConToken } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [nombreUsuario, setNombreUsuario] = useState('admin')
  const [password, setPassword] = useState('')
  const [codigo, setCodigo] = useState('')
  const [mfaToken, setMfaToken] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(false)
  // Con esto activo el proveedor vuelve a preguntar con que cuenta entrar, en vez de
  // reutilizar la sesion que el navegador ya tiene abierta con Google o Facebook.
  const [desdeCero, setDesdeCero] = useState(false)

  // El login social vuelve del backend por redireccion, con el resultado en la URL.
  useEffect(() => {
    const token = params.get('token')
    const pendiente = params.get('mfaToken')
    const errorSocial = params.get('error')

    if (token) {
      iniciarSesionConToken(token)
      navigate('/', { replace: true })
    } else if (pendiente) {
      setMfaToken(pendiente)
    } else if (errorSocial) {
      setError(errorSocial)
    }
  }, [params]) // eslint-disable-line react-hooks/exhaustive-deps

  async function onSubmit(e) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      const resultado = await login(nombreUsuario, password)
      if (resultado.requiereMfa) {
        setMfaToken(resultado.token)
      } else {
        navigate('/')
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  async function onVerificar(e) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      await verificarMfa(mfaToken, codigo)
      navigate('/')
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  if (mfaToken) {
    return (
      <div className="pantalla-centrada">
        <form className="tarjeta" onSubmit={onVerificar}>
          <h1>
            <i className="fa-solid fa-shield-halved" /> Verificacion en dos pasos
          </h1>
          <p className="ayuda-campo">Escribe el codigo de 6 digitos que muestra Google Authenticator.</p>
          <label>
            Codigo
            <input
              value={codigo}
              onChange={(e) => setCodigo(e.target.value)}
              inputMode="numeric"
              maxLength={6}
              autoFocus
              required
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={cargando}>
            <i className={`fa-solid ${cargando ? 'fa-spinner fa-spin' : 'fa-check'}`} />{' '}
            {cargando ? 'Verificando...' : 'Verificar'}
          </button>
          <button
            type="button"
            className="boton-plano"
            onClick={() => {
              setMfaToken(null)
              setCodigo('')
              setError(null)
            }}
          >
            Cancelar
          </button>
        </form>
      </div>
    )
  }

  return (
    <div className="pantalla-centrada">
      <form className="tarjeta" onSubmit={onSubmit}>
        <h1>
          <i className="fa-solid fa-mortar-pestle" /> Sistema POS Farmacia
        </h1>
        <label>
          Usuario
          <input value={nombreUsuario} onChange={(e) => setNombreUsuario(e.target.value)} required />
        </label>
        <label>
          Contrasena
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={cargando}>
          <i className={`fa-solid ${cargando ? 'fa-spinner fa-spin' : 'fa-right-to-bracket'}`} />{' '}
          {cargando ? 'Ingresando...' : 'Ingresar'}
        </button>

        <p className="separador-social">o ingresa con</p>
        <div className="botones-sociales">
          {PROVEEDORES.map((proveedor) => (
            <a
              key={proveedor.id}
              className={`boton-social ${proveedor.id}`}
              href={`${BASE_URL}/oauth2/authorization/${proveedor.id}${desdeCero ? '?desdeCero=1' : ''}`}
            >
              <i className={`fa-brands ${proveedor.icono}`} /> {proveedor.label}
            </a>
          ))}
        </div>
        <button
          type="button"
          className="boton-plano"
          onClick={() => setDesdeCero(!desdeCero)}
        >
          <i className={`fa-solid ${desdeCero ? 'fa-square-check' : 'fa-rotate-left'}`} />{' '}
          {desdeCero ? 'Se pedira elegir cuenta de nuevo' : 'Entrar con otra cuenta desde cero'}
        </button>
      </form>
    </div>
  )
}
