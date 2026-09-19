#!/usr/bin/env bash
# Genera el par RSA con el que ms-identidad firma los JWT y lo deja listo para el .env.
#
# Sin claves fijas, el servicio genera un par nuevo en cada arranque. Eso parece cómodo
# hasta que reinicias identidad: todos los tokens emitidos dejan de valer Y el JWKS que
# los otros nueve cachearon queda obsoleto, así que el sistema entero devuelve 401 hasta
# que cada servicio refresque. Con claves fijas, reiniciar identidad no molesta a nadie.
set -euo pipefail

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$TMP/priv.pem" 2>/dev/null
openssl rsa -in "$TMP/priv.pem" -pubout -out "$TMP/pub.pem" 2>/dev/null

# En una sola línea, sin cabeceras: es como las lee ClavesJwtConfig y como caben en un .env.
PRIV=$(grep -v "^-----" "$TMP/priv.pem" | tr -d '\n')
PUB=$(grep -v "^-----" "$TMP/pub.pem" | tr -d '\n')

echo "JWT_CLAVE_PRIVADA=$PRIV"
echo "JWT_CLAVE_PUBLICA=$PUB"
