#!/usr/bin/env bash
# Que ninguna venta se pierda cuando algo se cae.
#
# Cinco escenarios, cada uno rompe una pieza a proposito y comprueba que la venta
# termina con su comprobante igual:
#
#   1. Facturacion apagada mientras se vende
#   2. La base de facturacion falla unos segundos (reintento con espera)
#   3. Un evento corrupto (va a la cola muerta y no frena a los demas)
#   4. Kafka apagado mientras se vende (el outbox guarda el evento)
#   5. Cuadre: toda venta confirmada en esta corrida tiene comprobante, saga y reporte
#
# Tarda unos cuatro minutos. Necesita el perfil completo levantado.
#
#   GW=http://localhost:8095 ./scripts/verificar-resiliencia.sh

set -u
cd "$(dirname "$0")/.."

GW=${GW:-http://localhost:8080}
USUARIO=${USUARIO:-admin}
CLAVE=${CLAVE:-Admin123!}
fallos=0

ok()    { printf '  OK    %s\n' "$1"; }
falla() { printf '  FALLA %s\n' "$1"; fallos=$((fallos + 1)); }
paso()  { printf '\n== %s\n' "$1"; }
uuid()  { python -c 'import uuid;print(uuid.uuid4())'; }
psql_en() { docker compose exec -T postgres psql -U postgres -d "$1" -tAc "$2" </dev/null 2>/dev/null | tr -d '\r'; }
kafka_en() { docker compose exec -T kafka "$@" </dev/null 2>/dev/null | tr -d '\r'; }

token() {
    curl -s -X POST "$GW/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"nombreUsuario\":\"$USUARIO\",\"password\":\"$CLAVE\"}" \
        | grep -o '"token":"[^"]*"' | cut -d'"' -f4
}

LOCAL=$(psql_en pg_identidad "SELECT id FROM locales WHERE nombre='Sede Principal'")
CAJA=$(psql_en pg_identidad "SELECT id FROM cajas WHERE local_id='$LOCAL' AND activa LIMIT 1")
USR=$(psql_en pg_identidad "SELECT id FROM usuarios WHERE nombre_usuario='$USUARIO'")
PRODUCTO=$(psql_en pg_inventario "SELECT producto_id FROM stock_local WHERE local_id='$LOCAL' ORDER BY disponible - reservado DESC LIMIT 1")
INICIO=$(psql_en pg_ventas "SELECT now()")
TOKEN=$(token)
[ -n "$TOKEN" ] || { echo "Sin token: revisa GW=$GW"; exit 1; }

# Vende una unidad y devuelve el id de la venta si quedo CONFIRMADA.
vender() {
    local A=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")
    local v
    v=$(curl -s -X POST "$GW/api/ventas" "${A[@]}" \
        -d "{\"localId\":\"$LOCAL\",\"cajaId\":\"$CAJA\",\"sesionCajaId\":\"$CAJA\",\"usuarioId\":\"$USR\"}" \
        | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    curl -s -X POST "$GW/api/ventas/$v/lineas" "${A[@]}" -d "{\"productoId\":\"$PRODUCTO\",\"cantidad\":1}" >/dev/null
    curl -s -X POST "$GW/api/ventas/$v/confirmar" "${A[@]}" -H "Idempotency-Key: $(uuid)" \
        -d '{"tipoComprobante":"BOLETA"}' | grep -q CONFIRMADA && echo "$v"
}

# Espera hasta 150 s a que la venta tenga comprobante.
esperar_comprobante() {
    for _ in $(seq 1 75); do
        [ "$(psql_en pg_facturacion "SELECT count(*) FROM comprobantes WHERE venta_id='$1'")" = "1" ] && return 0
        sleep 2
    done
    return 1
}

esperar_servicio() {
    for _ in $(seq 1 60); do
        docker compose logs --since 3m "$1" 2>&1 | grep -q "Started .*Application" && return 0
        sleep 2
    done
    return 1
}

# ------------------------------------------------------------------------ 1
paso "1. Facturación apagada mientras se vende"
docker compose stop ms-facturacion >/dev/null 2>&1
V1=(); for _ in 1 2 3; do V1+=("$(vender)"); done
[ -n "${V1[2]}" ] && ok "las cajas siguieron vendiendo: 3 ventas confirmadas sin facturación" \
                  || falla "no se pudo vender con facturación apagada"
docker compose start ms-facturacion >/dev/null 2>&1
esperar_servicio ms-facturacion
todas=1; for v in "${V1[@]}"; do esperar_comprobante "$v" || todas=0; done
[ $todas = 1 ] && ok "al volver, facturación emitió los 3 comprobantes pendientes" \
               || falla "faltan comprobantes de las ventas hechas con facturación apagada"

# ------------------------------------------------------------------------ 2
paso "2. La base de facturación falla unos segundos"
psql_en pg_facturacion "ALTER TABLE comprobantes RENAME TO comprobantes_en_pausa" >/dev/null
V2=$(vender)
sleep 8
psql_en pg_facturacion "ALTER TABLE comprobantes_en_pausa RENAME TO comprobantes" >/dev/null
reintentos=$(docker compose logs --since 1m ms-facturacion 2>&1 | grep -c "se reintenta")
[ "$reintentos" -ge 1 ] && ok "el consumidor reintentó con espera ($reintentos intento(s) en el log)" \
                        || falla "no hubo reintentos en el log"
esperar_comprobante "$V2" && ok "cuando la base volvió, el comprobante se emitió: nada se descartó" \
                          || falla "la venta $V2 se quedó sin comprobante"

# ------------------------------------------------------------------------ 3
paso "3. Un evento corrupto va a la cola muerta y no frena a los demás"
dlq_total() {
    kafka_en kafka-get-offsets --bootstrap-server localhost:29092 --topic pos.ventas.confirmadas.dlq \
        | awk -F: '{s+=$3} END {print s+0}'
}
DLQ0=$(dlq_total)
echo "clave-veneno:esto no es un evento" | docker compose exec -T kafka kafka-console-producer \
    --bootstrap-server localhost:29092 --topic pos.ventas.confirmadas \
    --property parse.key=true --property key.separator=: >/dev/null 2>&1
V3=$(vender)
esperar_comprobante "$V3" && ok "una venta normal se facturó mientras el evento corrupto se reintentaba" \
                          || falla "la venta $V3 quedó frenada detrás del evento corrupto"
for _ in $(seq 1 60); do [ "$(dlq_total)" -gt "$DLQ0" ] && break; sleep 2; done
[ "$(dlq_total)" -gt "$DLQ0" ] && ok "el evento corrupto quedó guardado en pos.ventas.confirmadas.dlq" \
                               || falla "el evento corrupto no llegó a la cola muerta"

# ------------------------------------------------------------------------ 4
paso "4. Kafka apagado mientras se vende"
docker compose stop kafka >/dev/null 2>&1
V4=$(vender)
[ -n "$V4" ] && ok "la venta se confirmó con Kafka apagado" || falla "sin Kafka no se pudo vender"
pendiente=$(psql_en pg_ventas "SELECT count(*) FROM outbox WHERE agregado_id='$V4' AND publicado_en IS NULL")
[ "${pendiente:-0}" -ge 1 ] && ok "el evento quedó guardado en el outbox, pendiente de publicar" \
                            || falla "el evento no está en el outbox"
docker compose start kafka >/dev/null 2>&1
esperar_comprobante "$V4" && ok "al volver Kafka, el outbox publicó y la venta se facturó" \
                          || falla "la venta $V4 se quedó sin comprobante"

# ------------------------------------------------------------------------ 5
paso "5. Cuadre de todo lo vendido en esta corrida"
ventas=$(psql_en pg_ventas "SELECT id FROM ventas WHERE estado='CONFIRMADA' AND fecha >= '$INICIO' ORDER BY id")
n=$(echo "$ventas" | grep -c .)
lista=$(echo "$ventas" | sed "s/.*/'&'/" | paste -sd, -)
comprobantes=$(psql_en pg_facturacion "SELECT count(*) FROM comprobantes WHERE venta_id IN ($lista)")
reportes=$(psql_en pg_reportes "SELECT count(*) FROM rm_ventas WHERE venta_id IN ($lista)")
# La saga cierra cuando SUNAT acepta, y el simulador rechaza el 15%: puede tardar unos ciclos.
for _ in $(seq 1 45); do
    sagas=$(psql_en pg_ventas "SELECT count(*) FROM saga_venta WHERE venta_id IN ($lista) AND estado='COMPLETADA'")
    [ "$sagas" = "$n" ] && break
    sleep 2
done
echo "  ventas confirmadas: $n, comprobantes: $comprobantes, en reportes: $reportes, sagas completas: $sagas"
[ "$n" -ge 6 ] && [ "$comprobantes" = "$n" ] && [ "$reportes" = "$n" ] && [ "$sagas" = "$n" ] \
    && ok "cuadra: ninguna venta se perdió en ningún servicio" \
    || falla "no cuadra: alguna venta no llegó a todos los servicios"

paso "Resultado"
if [ $fallos -eq 0 ]; then echo "  Todo pasó."; else echo "  $fallos comprobación(es) fallaron."; exit 1; fi
