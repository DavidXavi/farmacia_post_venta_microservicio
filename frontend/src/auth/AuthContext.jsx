import { createContext, useContext, useEffect, useState } from 'react'
import { api, setAuthToken, setUnauthorizedHandler } from '../api/client'

const AuthContext = createContext(null)

function loadStoredSession() {
  const raw = localStorage.getItem('posfarmacia.session')
  const session = raw ? JSON.parse(raw) : null
  setAuthToken(session?.token ?? null)
  return session
}

/**
 * El login social vuelve del backend con el JWT en la URL, no con el cuerpo del login. El token
 * ya trae la identidad firmada (sub, roles, permisos), asi que se lee de ahi en vez de pedirla otra vez.
 */
export function sesionDesdeToken(token) {
  const datos = JSON.parse(atob(token.split('.')[1]))
  return {
    token,
    usuarioId: datos.sub,
    nombreUsuario: datos.nombreUsuario,
    roles: datos.roles ?? [],
    permisos: datos.permisos ?? [],
    localId: datos.localId,
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(loadStoredSession)

  useEffect(() => {
    setAuthToken(session?.token ?? null)
  }, [session])

  function guardar(data) {
    localStorage.setItem('posfarmacia.session', JSON.stringify(data))
    setSession(data)
    return data
  }

  /**
   * Devuelve la sesion, o { requiereMfa, token } si falta el codigo de Google
   * Authenticator.
   *
   * Los nombres son los que manda el backend: requiereMfa y token. Antes se leian
   * mfaRequerido y mfaToken, que no existen, asi que el token PENDIENTE se guardaba
   * como si fuera una sesion valida. El usuario entraba, veia el menu completo y todo
   * le respondia 403, porque ese token viaja con la lista de roles vacia.
   */
  async function login(nombreUsuario, password) {
    const data = await api.post('/api/auth/login', { nombreUsuario, password })
    return data.requiereMfa ? data : guardar(data)
  }

  /** El backend espera tokenPendiente, no mfaToken. */
  async function verificarMfa(tokenPendiente, codigo) {
    return guardar(await api.post('/api/auth/mfa/verificar', { tokenPendiente, codigo }))
  }

  function iniciarSesionConToken(token) {
    return guardar(sesionDesdeToken(token))
  }

  function logout() {
    localStorage.removeItem('posfarmacia.session')
    setSession(null)
  }

  useEffect(() => {
    setUnauthorizedHandler(logout)
  }, [])

  function tieneRol(...roles) {
    return session ? roles.some((r) => session.roles.includes(r)) : false
  }

  return (
    <AuthContext.Provider
      value={{ session, login, verificarMfa, iniciarSesionConToken, logout, tieneRol }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
