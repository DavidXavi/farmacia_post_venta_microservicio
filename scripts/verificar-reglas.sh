#!/usr/bin/env bash
# Segunda tanda de verificación: lo que verificar.sh no cubre.
#
# verificar.sh prueba el camino feliz de punta a punta. Este prueba lo otro: que una
# regla de negocio se conteste con su código y su mensaje en vez de un 500 mudo, que
# anular dispare la compensación, y que el convenio de seguro descuente de verdad.
# Los tres fallaban y ninguno se veía al compilar.
#
#   GW=http://localhost:8095 ./scripts/verificar-reglas.sh
set -uo pipefail

GW=${GW:-http://localhost:8080}
USUARIO=${USUARIO:-admin}
CLAVE=${CLAVE:-'Admin123!'}
fallos=0

ok()    { printf '  OK    %s\n' "$1"; }
falla() { printf '  FALLA %s\n' "$1"; fallos=$((fallos + 1)); }
paso()  { printf '\n== %s\n' "$1"; }
uuid()  { python -c 'import uuid;print(uuid.uuid4())'; }

# El </dev/null no sobra: docker compose exec se come el stdin, y dentro de un
# while read eso deja el bucle sin las filas que faltaban leer.
psql_en() { docker compose exec -T postgres psql -U postgres -d "$1" -tAc "$2" </dev/null 2>/dev/null | tr -d '\r'; }

TOKEN=$(curl -s -X POST "$GW/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"nombreUsuario\":\"$USUARIO\",\"password\":\"$CLAVE\"}" |
    grep -o '"token":"[^"]*"' | cut -d'"' -f4)
[ -n "$TOKEN" ] || { echo "Sin token no se puede seguir."; exit 1; }
A=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

LOCAL=$(psql_en pg_identidad "SELECT id FROM locales LIMIT 1")
CAJA=$(psql_en pg_identidad "SELECT id FROM cajas WHERE local_id='$LOCAL' LIMIT 1")
USR=$(psql_en pg_identidad "SELECT id FROM usuarios WHERE nombre_usuario='$USUARIO'")
LIBRE=$(psql_en pg_catalogo "SELECT id FROM productos WHERE requiere_receta=false AND estado='ACTIVO' LIMIT 1")
CONRECETA=$(psql_en pg_catalogo "SELECT id FROM productos WHERE requiere_receta=true AND estado='ACTIVO' LIMIT 1")

abrir_venta() {
    curl -s -X POST "$GW/api/ventas" "${A[@]}" \
        -d "{\"localId\":\"$LOCAL\",\"cajaId\":\"$CAJA\",\"sesionCajaId\":\"$CAJA\",\"usuarioId\":\"$USR\"}" |
        grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4
}

# Devuelve "codigo|cuerpo" de un POST de línea.
linea() {
    curl -s -w '|%{http_code}' -X POST "$GW/api/ventas/$1/detalles" "${A[@]}" \
        -d "{\"productoId\":\"$2\",\"cantidad\":$3}"
}

# ------------------------------------------------- 1. las reglas se contestan, no explotan
paso "1. Una regla de negocio responde con código y mensaje, no con 500"
V=$(abrir_venta)

# El esperado es una lista: importa que sea 4xx con mensaje, no si la regla eligio 400
# (la peticion no valia) o 409 (no se puede en este estado).
for caso in "receta|$CONRECETA|1|400 409" "cantidad cero|$LIBRE|0|400" \
            "cantidad negativa|$LIBRE|-3|400" "sin stock|$LIBRE|99999|400 409"; do
    IFS='|' read -r nombre prod cant esperado <<< "$caso"
    r=$(linea "$V" "$prod" "$cant")
    codigo=${r##*|}
    cuerpo=${r%|*}
    mensaje=$(echo "$cuerpo" | grep -o '"detail":"[^"]*"' | cut -d'"' -f4)
    if [[ " $esperado " == *" $codigo "* ]] && [ -n "$mensaje" ]; then
        ok "$nombre -> $codigo: $mensaje"
    else
        falla "$nombre -> $codigo (se esperaba $esperado con mensaje): $cuerpo"
    fi
done

paso "2. Confirmar una venta vacía se rechaza con motivo"
r=$(curl -s -w '|%{http_code}' -X POST "$GW/api/ventas/$V/confirmar" "${A[@]}" \
    -H "Idempotency-Key: $(uuid)" -d '{"tipoComprobante":"BOLETA"}')
codigo=${r##*|}
[ "$codigo" = "400" ] || [ "$codigo" = "409" ] \
    && ok "venta vacía rechazada con $codigo: $(echo "${r%|*}" | grep -o '"detail":"[^"]*"' | cut -d'"' -f4)" \
    || falla "venta vacía devolvió $codigo"

# ------------------------------------------------- 3. anular y compensar
paso "3. Anular una venta confirmada dispara la compensación"
V2=$(abrir_venta)
STOCK0=$(psql_en pg_inventario "SELECT disponible FROM stock_local WHERE producto_id='$LIBRE' AND local_id='$LOCAL'")
# Unidades de ese producto en el reporte de hoy (dia de Lima). Tras anular tienen que volver aqui.
reporte_hoy() { psql_en pg_reportes "SELECT COALESCE(SUM(unidades),0) FROM rm_ventas_diarias WHERE producto_id='$LIBRE' AND local_id='$LOCAL' AND dia=(now() AT TIME ZONE 'America/Lima')::date"; }
REP0=$(reporte_hoy)
curl -s -X POST "$GW/api/ventas/$V2/detalles" "${A[@]}" -d "{\"productoId\":\"$LIBRE\",\"cantidad\":4}" >/dev/null
curl -s -X POST "$GW/api/ventas/$V2/confirmar" "${A[@]}" -H "Idempotency-Key: $(uuid)" \
    -d '{"tipoComprobante":"BOLETA"}' >/dev/null

for _ in $(seq 1 30); do
    asignado=$(psql_en pg_inventario "SELECT count(*) FROM asignaciones_lote WHERE venta_id='$V2'")
    [ "${asignado:-0}" -ge 1 ] && break
    sleep 1
done
[ "${asignado:-0}" -ge 1 ] && ok "lotes asignados antes de anular" || falla "la venta no llegó a asignar lotes"

# La salida tiene que haberse aplicado ANTES de anular. Si no, el stock ya estaría en
# su valor original y la comprobación de abajo pasaría sin que nadie compensara nada.
for _ in $(seq 1 30); do
    STOCKV=$(psql_en pg_inventario "SELECT disponible FROM stock_local WHERE producto_id='$LIBRE' AND local_id='$LOCAL'")
    [ "${STOCKV:-0}" != "${STOCK0:-0}" ] && break
    sleep 1
done
[ "${STOCKV:-0}" = "$((STOCK0 - 4))" ]     && ok "la salida se aplicó: $STOCK0 -> $STOCKV"     || falla "tras vender 4 el disponible quedó en $STOCKV, se esperaba $((STOCK0 - 4))"

codigo=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GW/api/ventas/$V2/anular" "${A[@]}" \
    -H "Idempotency-Key: $(uuid)" -d "{\"usuarioId\":\"$USR\",\"motivo\":\"verificacion automatica\",\"comprobanteEmitido\":true}")
[ "$codigo" = "202" ] && ok "anulación aceptada ($codigo)" || falla "anular devolvió $codigo"

for _ in $(seq 1 30); do
    estado=$(psql_en pg_ventas "SELECT estado FROM ventas WHERE id='$V2'")
    [ "$estado" = "ANULADA" ] && break
    sleep 1
done
[ "$estado" = "ANULADA" ] && ok "la venta quedó ANULADA" || falla "la venta quedó en $estado"

for _ in $(seq 1 30); do
    publicado=$(psql_en pg_ventas "SELECT count(*) FROM outbox WHERE agregado_id='$V2' AND tipo LIKE '%nulada%' AND publicado_en IS NOT NULL")
    [ "${publicado:-0}" -ge 1 ] && break
    sleep 1
done
[ "${publicado:-0}" -ge 1 ] && ok "VentaAnulada publicada por outbox" || falla "el evento de anulación no se publicó"

for _ in $(seq 1 30); do
    STOCK1=$(psql_en pg_inventario "SELECT disponible FROM stock_local WHERE producto_id='$LIBRE' AND local_id='$LOCAL'")
    [ "${STOCK1:-0}" = "${STOCK0:-0}" ] && break
    sleep 1
done
[ "${STOCK1:-0}" = "${STOCK0:-0}" ] \
    && ok "inventario devolvió el stock: $STOCK0 -> $STOCK1" \
    || falla "el stock no volvió: antes $STOCK0, después $STOCK1"

for _ in $(seq 1 30); do
    REP1=$(reporte_hoy)
    [ "${REP1:-0}" = "${REP0:-0}" ] && break
    sleep 1
done
[ "${REP1:-0}" = "${REP0:-0}" ]     && ok "reportes descontó la venta anulada: $REP0 unidades hoy, igual que antes"     || falla "reportes sigue contando la venta anulada: antes $REP0 unidades, después $REP1"

# ------------------------------------------------- 4. convenio de seguro
paso "4. El convenio de seguro descuenta de verdad"
COB=$(psql_en pg_clientes "
    SELECT c.convenio_id || ' ' || c.producto_id || ' ' || c.porcentaje_cubierto
      FROM coberturas_seguro c LIMIT 50")
encontrado=""
while read -r conv prod pct; do
    [ -z "${prod:-}" ] && continue
    receta=$(psql_en pg_catalogo "SELECT requiere_receta FROM productos WHERE id='$prod'")
    [ "$receta" = "f" ] && { encontrado="$conv $prod $pct"; break; }
done <<< "$COB"

if [ -z "$encontrado" ]; then
    echo "  (omitido: no hay cobertura sembrada sobre un producto de venta libre)"
else
    read -r conv prod pct <<< "$encontrado"

    # Calentamiento. La primera llamada de ms-ventas a ms-clientes tras arrancar se
    # pasa del presupuesto de 300 ms con la JVM fria, y la degradacion es silenciosa:
    # devuelve cobertura cero y el cliente paga el total. Aqui se mide el estado
    # estable; el arranque en frio esta anotado aparte en docs/ESTADO.md.
    curl -s "$GW/api/clientes/coberturas?convenioId=$conv&productoIds=$prod" "${A[@]}" >/dev/null

    V3=$(abrir_venta)
    total=$(curl -s -X POST "$GW/api/ventas/$V3/detalles" "${A[@]}" \
        -d "{\"productoId\":\"$prod\",\"cantidad\":1}" | grep -o '"total":[0-9.]*' | tail -1 | cut -d: -f2)
    copago=$(curl -s -X POST "$GW/api/ventas/$V3/convenio" "${A[@]}" -d "{\"convenioId\":\"$conv\"}")
    cubierto=$(echo "$copago" | grep -o '"montoCubierto":[0-9.]*' | cut -d: -f2)
    # sin bc: basta con que no sea cero en ninguna de sus formas
    if [ -n "$cubierto" ] && [ "$cubierto" != "0" ] && [ "$cubierto" != "0.00" ]; then
        ok "convenio al ${pct}% sobre $total: cubre $cubierto"
    else
        falla "el convenio al ${pct}% cubrió $cubierto sobre un total de $total"
    fi
fi

# ------------------------------------------------- 5. arqueo de caja
paso "5. El cierre de caja cuenta el efectivo cobrado"
CAJA5=$(psql_en pg_identidad "SELECT id FROM cajas WHERE local_id='$LOCAL' AND activa
    AND id NOT IN (SELECT caja_id FROM sesiones_caja WHERE estado='ABIERTA') LIMIT 1")
SES=$(curl -s -X POST "$GW/api/cajas/$CAJA5/aperturas" "${A[@]}" -H "Idempotency-Key: $(uuid)"     -d "{\"usuarioId\":\"$USR\",\"montoInicial\":100}" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
V5=$(curl -s -X POST "$GW/api/ventas" "${A[@]}"     -d "{\"localId\":\"$LOCAL\",\"cajaId\":\"$CAJA5\",\"sesionCajaId\":\"$SES\",\"usuarioId\":\"$USR\"}" |
    grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
linea "$V5" "$LIBRE" 2 >/dev/null
TOTAL5=$(curl -s "$GW/api/ventas/$V5" "${A[@]}" | grep -o '"total":[0-9.]*' | tail -1 | cut -d: -f2)
curl -s -X POST "$GW/api/ventas/$V5/pagos" "${A[@]}" -H "Idempotency-Key: $(uuid)"     -d "{\"formaPagoId\":\"00000000-0000-0000-0000-000000000001\",\"monto\":$TOTAL5}" >/dev/null
curl -s -X POST "$GW/api/ventas/$V5/confirmar" "${A[@]}" -H "Idempotency-Key: $(uuid)"     -d '{"tipoComprobante":"BOLETA"}' >/dev/null
DECLARADO=$(python -c "print(100 + $TOTAL5)")
cierre=$(curl -s -X POST "$GW/api/cajas/$CAJA5/cierres" "${A[@]}" -H "Idempotency-Key: $(uuid)"     -d "{\"montoDeclarado\":$DECLARADO,\"observacion\":\"verificar-reglas\"}")
esperado=$(echo "$cierre" | grep -o '"montoEsperado":[0-9.]*' | cut -d: -f2)
diferencia=$(echo "$cierre" | grep -o '"diferencia":[-0-9.]*' | cut -d: -f2)
python -c "import sys; sys.exit(0 if abs(float('${esperado:-0}') - $DECLARADO) < 0.001 and abs(float('${diferencia:-1}')) < 0.001 else 1)"     && ok "esperado S/ $esperado = 100 iniciales + S/ $TOTAL5 en efectivo, diferencia 0"     || falla "el arqueo no cuadra: esperado $esperado, diferencia $diferencia (se cobraron $TOTAL5 en efectivo): $cierre"

paso "Resultado"
if [ "$fallos" -eq 0 ]; then
    echo "  Todo pasó."
else
    echo "  $fallos comprobación(es) fallaron."
fi
exit "$fallos"
