# Arquitectura

Este documento describe la estructura. El porqué de cada decisión está en
[`DECISIONES.md`](DECISIONES.md); la explicación sin jerga, en [`cliente/`](cliente/).

## Mapa

```
                  ┌─────────────┐
   caja POS  ───► │ api-gateway │  valida JWT, rate limit, enruta
                  └──────┬──────┘
                         │
                  ┌──────▼──────┐   SÍNCRONO, con presupuesto de latencia
                  │  ms-ventas  │──┬─► ms-catalogo      200 ms
                  │ orquesta la │  ├─► ms-inventario    500 ms  crítico
                  │    saga     │  ├─► ms-clientes      300 ms  degradable
                  └──────┬──────┘  ├─► ms-credito       800 ms  crítico
                         │         └─► ms-promociones   300 ms  degradable
                  ┌──────▼─────────────────┐
                  │  Kafka  (via outbox)   │  ASÍNCRONO, hecho consumado
                  └──┬──────┬──────┬───┬───┘
        ms-inventario │      │      │   └─ ms-reportes      read model CQRS
        confirma FEFO │      │      └───── ms-facturacion   CPE, RabbitMQ, SUNAT
          ms-credito ─┘      └──────────── ms-ventas        cierra la saga

        ms-identidad: emite JWT, publica JWKS, consume pos.auditoria
```

## Capas dentro de cada servicio

Clean Architecture por paquetes. La regla de dependencia apunta siempre hacia adentro.

```
com.posfarmacia.<servicio>/
├── domain/           entidades, value objects, servicios de dominio
│                     cero Spring, cero JPA, cero anotaciones
├── usecases/
│   ├── port/in/      qué se le puede pedir al servicio
│   ├── port/out/     qué necesita el servicio del mundo exterior
│   └── usecase/      la orquestación de cada caso de uso
├── adapters/
│   ├── web/          controladores REST
│   ├── persistence/  implementación de los puertos de salida contra Postgres
│   ├── messaging/    productores (outbox) y consumidores de Kafka
│   ├── clientes/     clientes HTTP hacia otros microservicios
│   └── external/     integraciones externas (SUNAT)
└── infrastructure/   configuración de Spring, wiring de beans
```

`domain` no depende de nada. `usecases` solo depende de `domain` y de sus propios puertos.
Los adaptadores implementan esos puertos. `infrastructure` es el único que conoce las
implementaciones concretas.

La regla la impone un test de ArchUnit, no la buena voluntad del equipo.

## Módulos compartidos

**`contracts`** contiene SOLO eventos de Kafka, nombres de tópicos y DTO de las APIs entre
servicios. Cero lógica de negocio, cero entidades JPA. Es la única dependencia que pueden
compartir dos microservicios sin volverse un monolito distribuido: si aquí entrara una
regla de negocio, cambiarla obligaría a desplegar los nueve a la vez.

**`plataforma`** contiene la infraestructura transversal: outbox, idempotencia, validación
de JWT, cliente HTTP con timeout y circuit breaker, propagación de traza. La regla que la
mantiene sana: si para agregar algo aquí hay que nombrar una entidad del dominio (venta,
lote, receta), ese algo no va aquí.

## Reparto de datos

Nueve bases PostgreSQL, una por servicio, sin GRANT cruzado.

| Base | Tablas |
|---|---|
| pg_identidad | roles, locales, usuarios, usuarios_roles, cajas, sesiones_caja, auditoria_operaciones, refresh_tokens |
| pg_catalogo | categorias, laboratorios, presentaciones, productos |
| pg_inventario | lotes, movimientos_inventario, stock_local, reservas, asignaciones_lote |
| pg_clientes | clientes, convenios_seguro, coberturas_seguro, afiliaciones_cliente, recetas, usos_receta |
| pg_credito | lineas_credito, movimientos_credito, reservas_credito |
| pg_promociones | promociones, promocion_condiciones |
| pg_ventas | ventas, detalles_venta, detalle_venta_lotes, pagos, formas_pago, saga_venta |
| pg_facturacion | comprobantes, devoluciones, detalle_devoluciones, notas_credito, series_comprobante, envios_sunat |
| pg_reportes | reglas_incentivo, incentivos_venta, rm_ventas, rm_venta_lineas, rm_ventas_diarias |

Además, toda base que publique eventos tiene `outbox`, y toda base que los consuma tiene
`evento_procesado`.

**Particionado.** `stock_local` por hash de `local_id` (reparte la contención).
`ventas`, `comprobantes` y `rm_ventas` por rango de fecha mensual (crecen sin parar y casi
todas las consultas son del mes en curso).

**Duplicación deliberada.** `detalles_venta` guarda `nombre_producto` y `precio_unitario`
como copia, no como referencia al catálogo. El evento `VentaConfirmada` lleva adentro el
nombre del local, del vendedor, del cliente y de la categoría. Sin esa copia, ms-reportes
tendría que llamar a tres servicios por cada venta que procesa, e imprimir una boleta de
hace un año dependería de que ms-catalogo esté vivo.

## Mensajería

Kafka para hechos consumados, RabbitMQ para trabajo dirigido a un solo consumidor.

| Tópico | Particiones | Lo consumen |
|---|---|---|
| `pos.ventas.confirmadas` | 12 | inventario, credito, facturacion, reportes |
| `pos.ventas.anuladas` | 12 | inventario, credito, facturacion, reportes |
| `pos.stock.lotes-asignados` | 12 | ventas |
| `pos.stock.movimientos` | 6 | reportes |
| `pos.catalogo.cambios` | 3 | todos, para invalidar caché |
| `pos.comprobantes.emitidos` | 6 | ventas, reportes |
| `pos.auditoria` | 12 | identidad |

Todos con `localId` como clave de partición: orden garantizado por local y paralelismo
natural entre locales, que es el shard real del negocio.

Cada grupo consumidor tiene su cadena de reintentos y su cola muerta:

```
pos.ventas.confirmadas
  → .retry.5s  → .retry.1m  → .retry.10m  → .dlq   (alerta a on-call)
```

RabbitMQ se queda con `pos.comprobantes.emitir`, donde importa el trabajo dirigido a un
consumidor y el reintento con backoff por mensaje.

## Secuencia de una venta

```
POST /ventas                          ms-ventas, transacción local, cero llamadas

POST /ventas/{id}/lineas              ms-ventas
  ├─ GET  /productos?ids=...          ms-catalogo     caché local, ~1 ms
  ├─ POST /reservas                   ms-inventario   UPDATE condicional atómico
  └─ POST /promociones/evaluar        ms-promociones  degradable

POST /ventas/{id}/cliente             ms-clientes     degradable
POST /ventas/{id}/pagos               ms-credito si aplica

POST /ventas/{id}/confirmar           Idempotency-Key obligatorio
  UNA transacción en pg_ventas:
    ├─ ventas.estado = CONFIRMADA
    ├─ INSERT saga_venta
    └─ INSERT outbox (VentaConfirmada)
  ──► responde al cajero, p99 < 500 ms

  PublicadorOutbox (cada 200 ms, FOR UPDATE SKIP LOCKED)
  ──► Kafka pos.ventas.confirmadas, key = localId
        ├─ ms-inventario   FEFO, descuenta lotes, publica LotesAsignados
        ├─ ms-credito      convierte reserva en cargo firme, escribe ledger
        ├─ ms-facturacion  registra comprobante PENDIENTE, job lo envía a SUNAT
        └─ ms-reportes     proyecta rm_ventas, rm_venta_lineas, rm_ventas_diarias

  ms-ventas consume LotesAsignados y ComprobanteEmitido, cierra saga_venta
```

## Compensación

```
POST /ventas/{id}/anular
  ├─ ventas.estado = ANULADA
  ├─ saga_venta.estado = COMPENSANDO
  └─ outbox (VentaAnulada)
       ├─ ms-inventario   devuelve a los lotes EXACTOS de los que salió
       ├─ ms-credito      libera la reserva o registra abono
       └─ ms-facturacion  emite nota de crédito si el comprobante ya salió
```

No hay transacción distribuida. Cada servicio compensa lo suyo cuando recibe el evento.

## Observabilidad

OpenTelemetry vía Micrometer Tracing, con el `traceId` propagado también en los headers de
Kafka. Sin eso, una saga que falla en el cuarto consumidor es imposible de seguir.

- Trazas: OTel Collector → Tempo
- Métricas: Prometheus. Además de las de infraestructura, métricas de negocio:
  `saga.venta.iniciada.total`, `saga.venta.compensada.total`, `outbox.antiguedad.segundos`,
  `reservas.vencidas.liberadas.total`, `comprobantes.pendientes.total`
- Logs: JSON con `traceId`, `localId`, `ventaId` → Loki

Las alertas están en `infra/prometheus/alertas.yml`. Cada una tiene escrito qué hacer
cuando suena.

## Despliegue

Kubernetes en producción, Docker Compose en desarrollo.

- HPA por CPU, con `minReplicas` según el perfil de carga de cada servicio
- PodDisruptionBudget para que un drain de nodo no deje un servicio en cero
- `maxUnavailable: 0` en el rolling update: las cajas no se enteran del despliegue
- `terminationGracePeriodSeconds: 30` y `server.shutdown: graceful`: se termina la venta
  en vuelo antes de morir
- Migraciones Flyway en expand y contract, porque durante el rollout conviven dos versiones
