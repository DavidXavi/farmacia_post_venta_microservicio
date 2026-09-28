# Cómo ejecutar

## Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker Desktop | 24+ | Levantar todo |
| Java | 17 | Solo si compilas fuera de Docker |
| Node | 20+ | Solo si trabajas el frontend fuera de Docker |
| kubectl | 1.28+ | Solo para la parte de Kubernetes |

Maven no hace falta: va por wrapper (`./mvnw`).

## Cuánta RAM necesitas

| Perfil | Qué levanta | RAM |
|---|---|---|
| por defecto | Postgres, Kafka, Redis, gateway + 4 servicios del camino de venta, frontend | ~3.8 GB |
| `completo` | + los nueve servicios, Schema Registry, Kafka UI | **~4.2 GB** (4.5 medidos con RabbitMQ) |
| `completo` + `observabilidad` | + Prometheus, Grafana, Tempo, Loki, OTel Collector | ~7.5 GB |
| todo + Kubernetes | + plano de control de Docker Desktop | ~9.5 GB |

Cada servicio Spring Boot pesa unos 400 MB en su contenedor: diez JVM son 4 GB y ahí se va
la mayor parte. Kafka en KRaft con un broker es otro giga.

Medido con `docker stats` sobre los 17 contenedores del perfil completo: **4.5 GB**. Eso
fue antes de sacar RabbitMQ (límite de 320 MB); hoy son 16 contenedores.
Sumando la VM de WSL2, el total ronda los 6 GB.

Con 8 GB de RAM se puede, justo y sin mucho más abierto. Con 16 GB es cómodo.
Con 32 GB sobra para todo más el IDE y el navegador.

### Límite de memoria de Docker en Windows

Docker Desktop toma la RAM a través de WSL2. Para fijar el techo, crear
`C:\Users\<usuario>\.wslconfig`:

```ini
[wsl2]
memory=16GB
processors=8
swap=4GB
```

Después `wsl --shutdown` y volver a abrir Docker Desktop.

## Arrancar

```bash
cp .env.example .env
```

Completar al menos:

```
DB_PASSWORD=algo-que-no-sea-esto
```

Y levantar:

```bash
docker compose up -d --build
```

La primera vez tarda: compila diez imágenes. Las siguientes reutilizan capas.

```bash
docker compose ps                    # estado
docker compose logs -f ms-ventas     # seguir un servicio
docker compose down                  # bajar (los datos quedan en el volumen)
docker compose down -v               # bajar y borrar los datos
```

## Puertos

| Servicio | URL | Notas |
|---|---|---|
| Frontend | http://localhost:5175 | |
| Gateway | http://localhost:8080 | el único que el frontend conoce |
| Kafka UI | http://localhost:8092 | perfil `completo` |
| Schema Registry | http://localhost:8091 | perfil `completo` |
| Grafana | http://localhost:3000 | perfil `observabilidad` |
| Prometheus | http://localhost:9090 | perfil `observabilidad` |
| Postgres | localhost:5452 | usuario `postgres` |
| Kafka | localhost:9096 | desde el host |

Los nueve servicios no exponen puerto al host: se llega a ellos por el gateway. Es
deliberado y es como corre en producción.

Si ya tienes algo ocupando el 8080 o el 5175 (otro proyecto, otro contenedor), no toques el
`docker-compose.yml`: cámbialo en tu `.env`.

```
GATEWAY_PORT=8095
FRONTEND_API_URL=http://localhost:8095
```

`FRONTEND_API_URL` hay que cambiarlo junto con el puerto porque Vite inyecta esa URL en
tiempo de build, no en runtime. Después de tocarlo, `docker compose build frontend`.

## Verificación funcional

Hay un script que corre todo lo de abajo de una vez y termina con código distinto de cero
si algo no cuadra:

```bash
./scripts/verificar.sh
```

Comprueba lo que de verdad define esta arquitectura, no que la página cargue: las nueve
bases aisladas, el JWT validado localmente, la saga llegando a los cuatro consumidores, la
idempotencia y el stock reservado y confirmado.

Lo que sigue es el mismo recorrido paso a paso, por si hay que depurar un punto concreto.

### 1. Las nueve bases existen y están aisladas

```bash
docker compose exec postgres psql -U postgres -c "\l" | grep pg_
```

Deben aparecer las nueve. Y esto tiene que fallar, que es la prueba de que el aislamiento
funciona:

```bash
docker compose exec postgres psql -U u_catalogo -d pg_ventas -c "SELECT 1"
# FATAL: permission denied for database "pg_ventas"
```

### 2. Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"nombreUsuario":"admin","password":"Admin123!"}'
```

Devuelve un JWT. Guardarlo:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"nombreUsuario":"admin","password":"Admin123!"}' | jq -r .token)
```

### 3. La clave pública está publicada

```bash
curl -s http://localhost:8080/.well-known/jwks.json | jq
```

Esto es lo que hace que los nueve servicios validen el token sin llamar a identidad.

### 4. Catálogo, y la caché funcionando

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/productos?buscar=paracetamol" | jq '.[0]'
```

Repetir la llamada: la segunda sale del caché en memoria del pod y se nota en el tiempo.

### 5. Una venta completa

```bash
LOCAL=$(docker compose exec -T postgres psql -U postgres -d pg_identidad -tAc \
  "SELECT id FROM locales LIMIT 1")
CAJA=$(docker compose exec -T postgres psql -U postgres -d pg_identidad -tAc \
  "SELECT id FROM cajas WHERE local_id='$LOCAL' LIMIT 1")

# abrir venta
VENTA=$(curl -s -X POST http://localhost:8080/api/ventas \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"localId\":\"$LOCAL\",\"cajaId\":\"$CAJA\",\"sesionCajaId\":\"$CAJA\",\"usuarioId\":\"$CAJA\"}" | jq -r .id)

# agregar una línea: esto llama a catálogo, reserva stock en inventario y evalúa promociones
PRODUCTO=$(docker compose exec -T postgres psql -U postgres -d pg_catalogo -tAc \
  "SELECT id FROM productos WHERE requiere_receta = false LIMIT 1")

curl -s -X POST "http://localhost:8080/api/ventas/$VENTA/lineas" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"productoId\":\"$PRODUCTO\",\"cantidad\":2}" | jq

# confirmar: acá se abre la saga y se escribe el evento en el outbox
curl -s -X POST "http://localhost:8080/api/ventas/$VENTA/confirmar" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" -d '{"tipoComprobante":"BOLETA"}' | jq
```

### 6. La saga corrió

El evento pasó por el outbox, por Kafka y llegó a los cuatro consumidores.

```bash
# el outbox ya lo publicó
docker compose exec postgres psql -U postgres -d pg_ventas \
  -c "SELECT tipo, publicado_en FROM outbox ORDER BY creado_en DESC LIMIT 3;"

# inventario asignó lotes con FEFO
docker compose exec postgres psql -U postgres -d pg_inventario \
  -c "SELECT venta_id, lote_id, cantidad FROM asignaciones_lote ORDER BY fecha DESC LIMIT 5;"

# facturación registró el comprobante
docker compose exec postgres psql -U postgres -d pg_facturacion \
  -c "SELECT serie, correlativo, estado_sunat FROM comprobantes ORDER BY fecha_emision DESC LIMIT 3;"

# reportes proyectó el read model
docker compose exec postgres psql -U postgres -d pg_reportes \
  -c "SELECT dia, unidades, importe FROM rm_ventas_diarias ORDER BY dia DESC LIMIT 5;"

# la saga se cerró
docker compose exec postgres psql -U postgres -d pg_ventas \
  -c "SELECT venta_id, estado, stock_confirmado, comprobante_emitido FROM saga_venta ORDER BY iniciada_en DESC LIMIT 3;"
```

### 7. La idempotencia funciona

Repetir la confirmación con la MISMA `Idempotency-Key`: devuelve la respuesta original sin
volver a ejecutar nada, y no aparece un segundo comprobante.

### 8. SUNAT caída no para las cajas

El simulador de SUNAT falla el 15% de los envíos por defecto. Para verlo más seguido:

```bash
docker compose stop ms-facturacion
# hacer una venta completa: funciona igual
docker compose start ms-facturacion
# el comprobante sale solo cuando el servicio vuelve
```

### 9. La degradación es real

```bash
docker compose stop ms-promociones
# agregar una línea: la venta sigue, sin descuento
docker compose logs ms-ventas | grep "no respondio"
```

## Kubernetes

Con Docker Desktop: Settings, Kubernetes, Enable Kubernetes, Apply & Restart. Tarda unos
minutos la primera vez.

```bash
kubectl config use-context docker-desktop
kubectl apply -k k8s/base
kubectl get pods -n pos-farmacia -w
kubectl port-forward -n pos-farmacia svc/api-gateway 8080:8080
```

Los manifiestos asumen que las imágenes ya están construidas localmente
(`pos-farmacia/ms-*:1.0.0`). Para construirlas:

```bash
docker compose --profile completo build
for s in api-gateway ms-identidad ms-catalogo ms-inventario ms-clientes ms-credito \
         ms-promociones ms-ventas ms-facturacion ms-reportes; do
  docker tag pos-farmacia-micro-$s pos-farmacia/$s:1.0.0
done
```

Los manifiestos base no incluyen Postgres, Kafka ni Redis: en producción son servicios
gestionados. Para probar en local, lo más simple es dejar la infraestructura en Compose y
apuntar los ConfigMap al host.

## Problemas comunes

**Un servicio se reinicia en bucle.** Casi siempre es Flyway contra una base que no existe
todavía. `docker compose logs <servicio> | head -40` lo dice en la primera línea del error.

**`FATAL: password authentication failed`.** El volumen de Postgres se creó con otra clave.
`docker compose down -v` y volver a levantar.

**Kafka no arranca.** El `CLUSTER_ID` cambió respecto al volumen existente.
`docker compose down -v`.

**El frontend no ve el backend.** Revisar que `CORS_ORIGENES` en el gateway coincida con el
puerto del frontend (5175 por defecto).

**Todo va lentísimo.** Docker está corto de memoria. Revisar el `.wslconfig` de arriba o
levantar solo el perfil por defecto.
