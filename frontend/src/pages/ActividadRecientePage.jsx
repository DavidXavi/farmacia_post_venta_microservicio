import { useEffect, useState } from 'react'
import { api } from '../api/client'

const INTERVALO_MS = 4000

export function ActividadRecientePage() {
  const [actividades, setActividades] = useState([])
  const [mensaje, setMensaje] = useState(null)

  useEffect(() => {
    let activo = true

    function cargar() {
      api
        .get('/api/actividad-reciente', { limite: 30 })
        .then((datos) => {
          if (activo) setActividades(datos)
        })
        .catch((e) => activo && setMensaje(e.message))
    }

    cargar()
    const intervalo = setInterval(cargar, INTERVALO_MS)
    return () => {
      activo = false
      clearInterval(intervalo)
    }
  }, [])

  return (
    <section>
      <h1>Actividad reciente (Kafka y RabbitMQ)</h1>
      <p>
        Cada venta confirmada publica un evento en Apache Kafka y encola la emision del comprobante en RabbitMQ.
        Esta lista se actualiza sola y muestra lo que sus consumidores ya procesaron; la auditoria de operaciones
        sensibles (por ejemplo una nota de credito) tambien llega por Kafka y queda en la pagina Auditoria.
      </p>
      {mensaje && <p className="aviso">{mensaje}</p>}
      <table>
        <thead>
          <tr>
            <th>Cuando</th>
            <th>Origen</th>
            <th>Tipo</th>
            <th>Descripcion</th>
          </tr>
        </thead>
        <tbody>
          {actividades.map((a, indice) => (
            <tr key={`${a.ocurridoEn}-${indice}`}>
              <td>{new Date(a.ocurridoEn).toLocaleString()}</td>
              <td>{a.origen}</td>
              <td>{a.tipo}</td>
              <td>{a.descripcion}</td>
            </tr>
          ))}
          {actividades.length === 0 && !mensaje && (
            <tr>
              <td colSpan={4}>Todavia no hay actividad. Confirme una venta para generar el primer evento.</td>
            </tr>
          )}
        </tbody>
      </table>
    </section>
  )
}
