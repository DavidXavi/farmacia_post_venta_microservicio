import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const ENLACES = [
  { to: '/', label: 'Venta (POS)', icono: 'fa-cart-shopping' },
  { to: '/caja', label: 'Caja', icono: 'fa-cash-register' },
  { to: '/productos', label: 'Productos', icono: 'fa-pills' },
  { to: '/lotes', label: 'Lotes', icono: 'fa-boxes-stacked' },
  { to: '/inventario', label: 'Inventario', icono: 'fa-warehouse' },
  { to: '/devoluciones', label: 'Devoluciones', icono: 'fa-rotate-left' },
  { to: '/clientes', label: 'Clientes', icono: 'fa-users' },
  { to: '/recetas', label: 'Recetas', icono: 'fa-file-medical' },
  { to: '/promociones', label: 'Promociones', icono: 'fa-tags' },
  { to: '/convenios', label: 'Convenios', icono: 'fa-handshake' },
  { to: '/creditos', label: 'Lineas de credito', icono: 'fa-credit-card' },
  { to: '/catalogos', label: 'Catalogos', icono: 'fa-list-ul' },
  { to: '/usuarios', label: 'Usuarios', icono: 'fa-user-gear' },
  { to: '/seguridad', label: 'Seguridad (MFA)', icono: 'fa-shield-halved' },
  { to: '/reportes', label: 'Reportes', icono: 'fa-chart-line' },
  { to: '/auditoria', label: 'Auditoria', icono: 'fa-clipboard-list' },
  { to: '/actividad-reciente', label: 'Actividad (Kafka/RabbitMQ)', icono: 'fa-diagram-project' },
]

export function AppLayout() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const [menuAbierto, setMenuAbierto] = useState(false)

  function onLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="app-shell">
      <header className="barra-superior">
        <button
          className="boton-menu"
          aria-label={menuAbierto ? 'Cerrar menu' : 'Abrir menu'}
          onClick={() => setMenuAbierto((v) => !v)}
        >
          <i className={`fa-solid ${menuAbierto ? 'fa-xmark' : 'fa-bars'}`} />
        </button>
        <span className="marca-movil">
          <i className="fa-solid fa-mortar-pestle" /> POS Farmacia
        </span>
      </header>

      {menuAbierto && <div className="fondo-menu" onClick={() => setMenuAbierto(false)} />}

      <aside className={`barra-lateral ${menuAbierto ? 'abierta' : ''}`}>
        <h2>
          <i className="fa-solid fa-mortar-pestle" /> POS Farmacia
        </h2>
        <nav>
          {ENLACES.map((enlace) => (
            <NavLink
              key={enlace.to}
              to={enlace.to}
              end={enlace.to === '/'}
              onClick={() => setMenuAbierto(false)}
            >
              <i className={`fa-solid ${enlace.icono}`} />
              <span>{enlace.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sesion-info">
          <p>
            <i className="fa-solid fa-user" /> {session?.nombreUsuario}
          </p>
          <p className="roles">{session?.roles?.join(', ')}</p>
          <button className="boton-salir" onClick={onLogout}>
            <i className="fa-solid fa-right-from-bracket" /> Cerrar sesion
          </button>
        </div>
      </aside>
      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}
