import { Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layout/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { VentaPage } from './pages/VentaPage'
import { CajaPage } from './pages/CajaPage'
import { ProductosPage } from './pages/ProductosPage'
import { LotesPage } from './pages/LotesPage'
import { ClientesPage } from './pages/ClientesPage'
import { RecetasPage } from './pages/RecetasPage'
import { ReportesPage } from './pages/ReportesPage'
import { AuditoriaPage } from './pages/AuditoriaPage'
import { ActividadRecientePage } from './pages/ActividadRecientePage'
import { UsuariosPage } from './pages/UsuariosPage'
import { SeguridadPage } from './pages/SeguridadPage'
import { PromocionesPage } from './pages/PromocionesPage'
import { ConveniosPage } from './pages/ConveniosPage'
import { CreditosPage } from './pages/CreditosPage'
import { CatalogosPage } from './pages/CatalogosPage'
import { InventarioPage } from './pages/InventarioPage'
import { DevolucionesPage } from './pages/DevolucionesPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<VentaPage />} />
        <Route path="caja" element={<CajaPage />} />
        <Route path="productos" element={<ProductosPage />} />
        <Route path="lotes" element={<LotesPage />} />
        <Route path="inventario" element={<InventarioPage />} />
        <Route path="devoluciones" element={<DevolucionesPage />} />
        <Route path="clientes" element={<ClientesPage />} />
        <Route path="recetas" element={<RecetasPage />} />
        <Route path="promociones" element={<PromocionesPage />} />
        <Route path="convenios" element={<ConveniosPage />} />
        <Route path="creditos" element={<CreditosPage />} />
        <Route path="catalogos" element={<CatalogosPage />} />
        <Route path="usuarios" element={<UsuariosPage />} />
        <Route path="seguridad" element={<SeguridadPage />} />
        <Route path="reportes" element={<ReportesPage />} />
        <Route path="auditoria" element={<AuditoriaPage />} />
        <Route path="actividad-reciente" element={<ActividadRecientePage />} />
      </Route>
    </Routes>
  )
}

export default App
