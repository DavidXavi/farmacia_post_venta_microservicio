import { useEffect, useState } from 'react'
import { api } from '../api/client'

export function AuditoriaPage() {
  const [registros, setRegistros] = useState([])
  const [mensaje, setMensaje] = useState(null)

  useEffect(() => {
    api.get('/api/auditoria').then(setRegistros).catch((e) => setMensaje(e.message))
  }, [])

  return (
    <section>
      <h1>Auditoria</h1>
      {mensaje && <p className="aviso">{mensaje}</p>}
      <table>
        <thead><tr><th>Fecha</th><th>Accion</th><th>Entidad</th><th>Detalle</th></tr></thead>
        <tbody>
          {registros.map((r) => (
            <tr key={r.id}>
              <td>{new Date(r.fecha).toLocaleString()}</td>
              <td>{r.accion}</td>
              <td>{r.entidad}</td>
              <td>{r.detalle}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
