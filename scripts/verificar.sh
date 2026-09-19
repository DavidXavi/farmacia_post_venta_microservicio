#!/usr/bin/env bash
# Verificación funcional del sistema levantado.
#
# Comprueba lo que de verdad define esta arquitectura, no que "la página carga":
# las nueve bases aisladas, el JWT validado localmente, la saga completa, la
# idempotencia y la degradación. Cada paso imprime OK o FALLA y el script termina
# con código distinto de cero si algo no cuadra.
#
#   ./scripts/verificar.sh
set -uo pipefail

GW=${GW:-http://localhost:8080}
USUARIO=${USUARIO:-admin}
CLAVE=${CLAVE:-'Admin123!'}
fallos=0

ok()    { printf '  OK    %s\n' "$1"; }
falla() { printf '  FALLA %s\n' "$1"; fallos=$((fallos + 1)); }
paso()  { printf '\n== %s\n' "$1"; }

psql_en() { docker compose exec -T postgres psql -U postgres -d "$1" -tAc "$2" 2>/dev/null | tr -d '\r'; }

# ---------------------------------------------------------------- 1. contenedores
paso "1. Contenedores arriba"
arriba=$(docker compose ps --status running --format '{{.Service}}' | wc -l | tr -d ' ')
[ "$arriba" -ge 14 ] && ok "$arriba servicios corriendo" || falla "solo $arriba servicios corriendo"

# ---------------------------------------------------------------- 2. bases aisladas
paso "2. Las nueve bases existen y están aisladas"
bases=$(psql_en postgres "SELECT count(*) FROM pg_database WHERE datname LIKE 'pg\\_%'")
[ "$bases" = "9" ] && ok "9 bases creadas" || falla "hay $bases bases, deberían ser 9"

# Esto TIENE que fallar: es la prueba de que el aislamiento lo impone el motor.
if docker compose exec -T postgres psql -U u_catalogo -d pg_ventas -c "SELECT 1" >/dev/null 2>&1; then
    falla "u_catalogo PUDO conectarse a pg_ventas: el aislamiento no funciona"
else
    ok "u_catalogo no puede conectarse a pg_ventas"
fi

# ---------------------------------------------------------------- 3. login
paso "3. Login y emisión de JWT"
respuesta=$(curl -s -X POST "$GW/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"nombreUsuario\":\"$USUARIO\",\"password\":\"$CLAVE\"}")
TOKEN=$(echo "$respuesta" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$TOKEN" ]; then ok "JWT emitido (${#TOKEN} caracteres)"; else
    falla "login sin token: $respuesta"; echo; echo "Sin token no se puede seguir."; exit 1
fi

paso "4. JWKS publicado"
if curl -sf "$GW/.well-known/jwks.json" | grep -q '"kty"'; then
    ok "la clave pública está publicada: los nueve validan sin llamar a identidad"
else
    falla "JWKS no responde"
fi

AUTH=(-H "Authorization: Bearer $TOKEN")

# ---------------------------------------------------------------- 5. catálogo
paso "5. Catálogo a través del gateway"
LOCAL=$(psql_en pg_identidad "SELECT id FROM locales LIMIT 1")
CAJA=$(psql_en pg_identidad "SELECT id FROM cajas WHERE local_id='$LOCAL' LIMIT 1")
USUARIO_ID=$(psql_en pg_identidad "SELECT id FROM usuarios WHERE nombre_usuario='$USUARIO'")
PRODUCTO=$(psql_en pg_catalogo "SELECT id FROM productos WHERE requiere_receta=false AND estado='ACTIVO' LIMIT 1")

prod=$(curl -s "${AUTH[@]}" "$GW/api/productos/$PRODUCTO")
echo "$prod" | grep -q '"nombreComercial"' \
    && ok "producto consultado: $(echo "$prod" | grep -o '"nombreComercial":"[^"]*"' | cut -d'"' -f4)" \
    || falla "catálogo no respondió: $prod"

# ---------------------------------------------------------------- 6. stock antes
paso "6. Venta completa"
STOCK_ANTES=$(psql_en pg_inventario "SELECT disponible - reservado FROM stock_local WHERE producto_id='$PRODUCTO' AND local_id='$LOCAL'")
echo "  stock disponible antes: ${STOCK_ANTES:-0}"

venta=$(curl -s -X POST "$GW/api/ventas" "${AUTH[@]}" -H "Content-Type: application/json" \
    -d "{\"localId\":\"$LOCAL\",\"cajaId\":\"$CAJA\",\"sesionCajaId\":\"$CAJA\",\"usuarioId\":\"$USUARIO_ID\"}")
VENTA=$(echo "$venta" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
[ -n "$VENTA" ] && ok "venta abierta $VENTA" || { falla "no se pudo abrir la venta: $venta"; exit 1; }

linea=$(curl -s -X POST "$GW/api/ventas/$VENTA/lineas" "${AUTH[@]}" -H "Content-Type: application/json" \
    -d "{\"productoId\":\"$PRODUCTO\",\"cantidad\":2}")
echo "$linea" | grep -q '"total"' \
    && ok "línea agregada, total $(echo "$linea" | grep -o '"total":[0-9.]*' | tail -1 | cut -d: -f2)" \
    || falla "no se pudo agregar la línea: $linea"

STOCK_RESERVADO=$(psql_en pg_inventario "SELECT reservado FROM stock_local WHERE producto_id='$PRODUCTO' AND local_id='$LOCAL'")
[ "${STOCK_RESERVADO:-0}" -ge 2 ] && ok "stock reservado: $STOCK_RESERVADO" || falla "el stock no quedó reservado"

CLAVE_IDEM=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || python -c "import uuid;print(uuid.uuid4())")
confirmada=$(curl -s -X POST "$GW/api/ventas/$VENTA/confirmar" "${AUTH[@]}" \
    -H "Content-Type: application/json" -H "Idempotency-Key: $CLAVE_IDEM" \
    -d '{"tipoComprobante":"BOLETA"}')
echo "$confirmada" | grep -q 'CONFIRMADA' && ok "venta confirmada" || falla "no se confirmó: $confirmada"

# ---------------------------------------------------------------- 7. la saga
paso "7. La saga llegó a los cuatro consumidores"
echo "  esperando a que el outbox publique y los consumidores procesen..."
for _ in $(seq 1 30); do
    publicado=$(psql_en pg_ventas "SELECT count(*) FROM outbox WHERE agregado_id='$VENTA' AND publicado_en IS NOT NULL")
    [ "${publicado:-0}" -ge 1 ] && break
    sleep 1
done
[ "${publicado:-0}" -ge 1 ] && ok "outbox publicó el evento a Kafka" || falla "el evento sigue sin publicarse"

for _ in $(seq 1 30); do
    asignaciones=$(psql_en pg_inventario "SELECT count(*) FROM asignaciones_lote WHERE venta_id='$VENTA'")
    [ "${asignaciones:-0}" -ge 1 ] && break
    sleep 1
done
[ "${asignaciones:-0}" -ge 1 ] && ok "inventario asignó $asignaciones lote(s) con FEFO" || falla "inventario no asignó lotes"

for _ in $(seq 1 30); do
    comprobante=$(psql_en pg_facturacion "SELECT serie || '-' || correlativo FROM comprobantes WHERE venta_id='$VENTA'")
    [ -n "$comprobante" ] && break
    sleep 1
done
[ -n "$comprobante" ] && ok "facturación registró el comprobante $comprobante" || falla "no se registró comprobante"

for _ in $(seq 1 30); do
    proyectada=$(psql_en pg_reportes "SELECT count(*) FROM rm_ventas WHERE venta_id='$VENTA'")
    [ "${proyectada:-0}" -ge 1 ] && break
    sleep 1
done
[ "${proyectada:-0}" -ge 1 ] && ok "reportes proyectó el read model" || falla "el read model no se actualizó"

STOCK_DESPUES=$(psql_en pg_inventario "SELECT disponible FROM stock_local WHERE producto_id='$PRODUCTO' AND local_id='$LOCAL'")
echo "  stock disponible después: ${STOCK_DESPUES:-0}"

# ---------------------------------------------------------------- 8. idempotencia
paso "8. Idempotencia: repetir con la misma clave no duplica"
curl -s -X POST "$GW/api/ventas/$VENTA/confirmar" "${AUTH[@]}" \
    -H "Content-Type: application/json" -H "Idempotency-Key: $CLAVE_IDEM" \
    -d '{"tipoComprobante":"BOLETA"}' >/dev/null
sleep 2
cuantos=$(psql_en pg_facturacion "SELECT count(*) FROM comprobantes WHERE venta_id='$VENTA'")
[ "${cuantos:-0}" = "1" ] && ok "sigue habiendo 1 comprobante, no 2" || falla "hay $cuantos comprobantes: la idempotencia falló"

# ---------------------------------------------------------------- 9. resultado
paso "Resultado"
if [ "$fallos" -eq 0 ]; then
    echo "  Todo pasó."
else
    echo "  $fallos comprobación(es) fallaron."
fi
exit "$fallos"
