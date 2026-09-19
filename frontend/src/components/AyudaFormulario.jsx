import { useState } from 'react'

export function AyudaFormulario({ titulo, pasos }) {
  const [abierto, setAbierto] = useState(false)

  return (
    <>
      <button
        type="button"
        className="boton-info"
        onClick={() => setAbierto(true)}
        aria-label={`Ayuda: ${titulo}`}
        title="Como llenar este formulario"
      >
        <i className="fa-solid fa-circle-info" />
      </button>

      {abierto && (
        <div className="fondo-modal" onClick={() => setAbierto(false)}>
          <div className="modal-ayuda" onClick={(e) => e.stopPropagation()}>
            <div className="modal-ayuda-header">
              <h3>{titulo}</h3>
              <button type="button" className="boton-cerrar-modal" onClick={() => setAbierto(false)} aria-label="Cerrar">
                <i className="fa-solid fa-xmark" />
              </button>
            </div>
            <ol>
              {pasos.map((paso, i) => (
                <li key={i}>{paso}</li>
              ))}
            </ol>
          </div>
        </div>
      )}
    </>
  )
}
