import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import { api } from '../api/client'

export function SeguridadPage() {
  const [habilitado, setHabilitado] = useState(null)
  const [registro, setRegistro] = useState(null)
  const [qr, setQr] = useState(null)
  const [codigo, setCodigo] = useState('')
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [cargando, setCargando] = useState(false)

  useEffect(() => {
    api
      .get('/api/auth/mfa/estado')
      .then((estado) => setHabilitado(estado.habilitado))
      .catch((err) => setError(err.message))
  }, [])

  // El QR se dibuja en el navegador: el secreto nunca sale hacia un servicio externo.
  useEffect(() => {
    if (!registro) return setQr(null)
    QRCode.toDataURL(registro.uriOtpauth, { width: 220, margin: 1 }).then(setQr).catch(() => setQr(null))
  }, [registro])

  async function ejecutar(accion) {
    setError(null)
    setAviso(null)
    setCargando(true)
    try {
      await accion()
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  const iniciar = () => ejecutar(async () => setRegistro(await api.post('/api/auth/mfa/registro')))

  const confirmar = () =>
    ejecutar(async () => {
      await api.post('/api/auth/mfa/registro/confirmar', { codigo })
      setHabilitado(true)
      setRegistro(null)
      setCodigo('')
      setAviso('Verificacion en dos pasos activada. Se pedira el codigo en el proximo inicio de sesion.')
    })

  const deshabilitar = () =>
    ejecutar(async () => {
      await api.del('/api/auth/mfa')
      setHabilitado(false)
      setAviso('Verificacion en dos pasos desactivada.')
    })

  return (
    <section>
      <h1>
        <i className="fa-solid fa-shield-halved" /> Seguridad de la cuenta
      </h1>

      {error && <p className="error">{error}</p>}
      {aviso && <p className="aviso">{aviso}</p>}

      <div className="tarjeta">
        <h2>Verificacion en dos pasos (Google Authenticator)</h2>
        {habilitado === null && <p>Cargando...</p>}

        {habilitado === true && (
          <>
            <p>
              <i className="fa-solid fa-circle-check" /> Activada: al iniciar sesion se pedira un codigo
              de 6 digitos.
            </p>
            <button onClick={deshabilitar} disabled={cargando}>
              <i className="fa-solid fa-shield-slash" /> Desactivar
            </button>
          </>
        )}

        {habilitado === false && !registro && (
          <>
            <p>Desactivada. Agrega un segundo factor para proteger tu cuenta.</p>
            <button onClick={iniciar} disabled={cargando}>
              <i className="fa-solid fa-qrcode" /> Activar
            </button>
          </>
        )}

        {registro && (
          <>
            <p>1. Escanea el codigo con Google Authenticator.</p>
            {qr && <img className="qr-mfa" src={qr} alt="Codigo QR de Google Authenticator" />}
            <p className="ayuda-campo">
              Si no puedes escanear, escribe la clave manualmente: <code>{registro.secreto}</code>
            </p>
            <label>
              2. Escribe el codigo que muestra la app
              <input value={codigo} onChange={(e) => setCodigo(e.target.value)} inputMode="numeric" maxLength={6} />
            </label>
            <button onClick={confirmar} disabled={cargando || codigo.length !== 6}>
              <i className="fa-solid fa-check" /> Confirmar
            </button>
          </>
        )}
      </div>
    </section>
  )
}
