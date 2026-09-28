// El gateway es lo unico que el frontend conoce: los nueve servicios no exponen puerto.
export const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

let currentToken = null
let onUnauthorized = null

export function setAuthToken(token) {
  currentToken = token
}

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

async function request(path, { method = 'GET', body, query, idempotencyKey } = {}) {
  const url = new URL(path, BASE_URL)
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value)
    })
  }

  const headers = { 'Content-Type': 'application/json' }
  if (currentToken) headers.Authorization = `Bearer ${currentToken}`
  // La clave de idempotencia la genera el POS, no el servidor: tiene que ser la MISMA
  // si el cajero reintenta la misma operacion. Una clave nueva por click convertiria
  // un doble click en dos cobros.
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey

  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 204) return null

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    // Un codigo TOTP equivocado tambien responde 401, pero ahi la sesion sigue siendo buena:
    // solo expira la sesion por los otros 401.
    if (response.status === 401 && data?.codigo !== 'MFA_REQUERIDA' && onUnauthorized) onUnauthorized()
    const message = data?.detail || data?.mensaje || data?.error || data?.title || data?.message || `Error ${response.status}`
    const error = new Error(message)
    // Codigo funcional del backend (ErrorResponse.codigo), p. ej. MFA_REQUERIDA.
    error.codigo = data?.codigo
    error.status = response.status
    throw error
  }

  return data
}

export const api = {
  get: (path, query) => request(path, { method: 'GET', query }),
  post: (path, body, opciones = {}) => request(path, { method: 'POST', body, ...opciones }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
  del: (path) => request(path, { method: 'DELETE' }),
}
