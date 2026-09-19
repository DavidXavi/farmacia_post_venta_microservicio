// Las fechas llegan del backend en ISO ('2026-08-12' o '2026-08-12T00:00:00.000Z') y
// pintarlas crudas deja al usuario leyendo 'T00:00:00.000Z' en una columna de
// vencimientos. Formato peruano: dd/mm/aaaa.

export function soloFecha(valor) {
  if (!valor) return '-'
  const fecha = new Date(valor)
  if (Number.isNaN(fecha.getTime())) return valor
  // UTC y no hora local: una fecha sin hora no tiene zona, y convertirla corre el día
  // hacia atrás para quien está en Lima (UTC-5).
  const dia = String(fecha.getUTCDate()).padStart(2, '0')
  const mes = String(fecha.getUTCMonth() + 1).padStart(2, '0')
  return `${dia}/${mes}/${fecha.getUTCFullYear()}`
}

export function fechaYHora(valor) {
  if (!valor) return '-'
  const fecha = new Date(valor)
  if (Number.isNaN(fecha.getTime())) return valor
  return fecha.toLocaleString('es-PE')
}
