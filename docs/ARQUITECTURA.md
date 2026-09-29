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
        confirma FEFO │      │      └───── ms-facturacion   CPE, SUNAT
          ms-credito ─┘      └──────────── ms-ventas        cierra la saga

        ms-identidad: emite JWT, publica JWKS, consume pos.auditoria
```

## Contenedores

Qué corre en cada contenedor del `docker-compose.yml`. Un microservicio es un contenedor;
la infraestructura va aparte y ningún servicio la lleva adentro.

| Contenedor | Qué es | Perfil | Puerto en el host | Base de datos |
|---|---|---|---|---|
| `frontend` | React servido por Nginx | por defecto | 5175 | |
| `api-gateway` | Spring Cloud Gateway: JWT, rate limit, rutas | por defecto | 8080 | |
| `ms-identidad` | Login, JWT, JWKS, auditoría | por defecto | interno | `pg_identidad` |
| `ms-catalogo` | Productos y precios | por defecto | interno | `pg_catalogo` |
| `ms-inventario` | Stock, lotes, FEFO, reservas | por defecto | interno | `pg_inventario` |
| `ms-ventas` | Venta, pagos, caja, orquesta la saga | por defecto | interno | `pg_ventas` |
| `ms-clientes` | Clientes, recetas, convenios | completo | interno | `pg_clientes` |
| `ms-credito` | Líneas de crédito | completo | interno | `pg_credito` |
| `ms-promociones` | Promociones y descuentos | completo | interno | `pg_promociones` |
| `ms-facturacion` | Comprobantes, devoluciones, envío a SUNAT | completo | interno | `pg_facturacion` |
| `ms-reportes` | Read model CQRS para reportes | completo | interno | `pg_reportes` |
| `postgres` | Un Postgres 16 con las nueve bases | por defecto | 5452 | las nueve |
| `redis` | Rate limit del gateway e `Idempotency-Key` | por defecto | 6380 | |
| `kafka` | Broker Kafka en KRaft, sin Zookeeper. Datos en el volumen `kafka-data` | por defecto | 9096 | |
| `kafka-ui` | Consola web para ver tópicos y mensajes | completo | 8092 | |
| `otel-collector`, `tempo` | Trazas | observabilidad | 4318 | |
| `prometheus`, `grafana` | Métricas y tableros | observabilidad | 9090, 3000 | |
| `loki` | Logs | observabilidad | interno | |

Perfil completo: 15 contenedores. Eran 17: salieron RabbitMQ y Schema Registry, que
estaban levantados y ningún servicio usaba.

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

Un solo broker: Kafka. El sistema anterior usaba también RabbitMQ; aquí se sacó porque su
único trabajo ya lo hacía mejor una tabla. El porqué está en la decisión 12 de
[`DECISIONES.md`](DECISIONES.md).

| Tópico | Particiones | Lo publica | Lo consumen |
|---|---|---|---|
| `pos.ventas.confirmadas` | 12 | ventas | inventario, credito, facturacion, reportes |
| `pos.ventas.anuladas` | 12 | ventas | inventario, credito, reportes |
| `pos.stock.lotes-asignados` | 12 | inventario | ventas |
| `pos.stock.movimientos` | 6 | inventario | nadie todavía |
| `pos.catalogo.cambios` | 3 | catalogo | nadie todavía |
| `pos.comprobantes.emitidos` | 6 | facturacion | ventas |
| `pos.auditoria` | 12 | clientes, credito, facturacion, identidad, inventario, promociones | identidad |

La columna de consumidores sale de los `@KafkaListener` del código, no del diseño. Tres
huecos contra lo que se pensó:

- `pos.catalogo.cambios` se publica al cambiar un precio y nadie lo lee. Los demás
  servicios ven el precio nuevo cuando vence el TTL de 60 s de su caché.
- `pos.stock.movimientos` se publica al ajustar lotes y reportes no lo proyecta.
- Facturación no consume `pos.ventas.anuladas`. Anular una venta con comprobante ya
  aceptado no emite nota de crédito sola: hay que registrar la devolución a mano.

### Cuándo aparece cada evento

Kafka no participa mientras el cajero arma la venta. Buscar, agregar líneas, evaluar
promociones y confirmar son HTTP síncrono. Kafka entra después del commit: el caso de uso
escribe en `outbox` y `PublicadorOutbox` lo manda al broker cada 200 ms. Por eso el
mensaje aparece en Kafka UI unos 200 ms después de que la API respondió.

| Acción en el POS | Tópico que se llena | Cuándo |
|---|---|---|
| Confirmar venta | `pos.ventas.confirmadas` | al instante |
| (sigue sola) | `pos.stock.lotes-asignados` | cuando inventario aplicó FEFO, menos de un segundo |
| (sigue sola) | `pos.comprobantes.emitidos` | hasta 5 s después, cuando SUNAT acepta. Con SUNAT caída, no aparece hasta que vuelva |
| Anular venta | `pos.ventas.anuladas` | al instante |
| Cambiar precio | `pos.catalogo.cambios` | al instante |
| Ajustar lotes | `pos.stock.movimientos` y `pos.auditoria` | al instante |
| Administrar usuarios, promociones, recetas, crédito, devoluciones | `pos.auditoria` | al instante |

Una venta confirmada deja tres mensajes en orden: `ventas.confirmadas`,
`lotes-asignados` y `comprobantes.emitidos`. Los tópicos `.dlq` solo aparecen cuando un
consumidor agotó los reintentos; si hay uno, algo se rompió.

Todos con `localId` como clave de partición: orden garantizado por local y paralelismo
natural entre locales, que es el shard real del negocio. Los crea
`plataforma/mensajeria/KafkaConfig` al arrancar cualquier servicio, con esas particiones;
si ya existen con menos, les agrega las que faltan.

Cuando un consumidor falla, el evento no se descarta:

```
pos.ventas.confirmadas
  falla  -> reintenta a los 1, 2, 4, 8, 16 y 30 s (la partición espera, las otras siguen)
  sigue  -> se copia a pos.ventas.confirmadas.dlq con la excepción en los headers
```

Cada tópico tiene su `.dlq`. Lo que cae ahí queda para revisarlo y volver a publicarlo. El
porqué de reintentar en la misma partición y no con tópicos de reintento está en la
decisión 13 de [`DECISIONES.md`](DECISIONES.md).

Kafka guarda sus datos en el volumen `kafka-data`. Sin él, un `docker compose down` borraba
eventos que el outbox ya había dado por publicados.

El envío a SUNAT no pasa por ninguna cola de mensajes. Facturación consume
`pos.ventas.confirmadas`, guarda el comprobante como PENDIENTE y confirma el offset. Un
job cada 5 s toma los pendientes con `SELECT ... FOR UPDATE SKIP LOCKED`, así que varias
réplicas se reparten el trabajo sin pisarse, y lo que SUNAT rechaza sigue PENDIENTE para el
ciclo siguiente. La tabla es la cola, y además es el registro que SUNAT exige llevar.

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
       └─ ms-reportes     descuenta la venta del read model
```

No hay transacción distribuida. Cada servicio compensa lo suyo cuando recibe el evento.
Facturación no escucha la anulación: si el comprobante ya salió, la nota de crédito se
emite registrando la devolución.

## Observabilidad

OpenTelemetry vía Micrometer Tracing, con el `traceId` propagado también en los headers de
Kafka. Sin eso, una saga que falla en el cuarto consumidor es imposible de seguir.

- Trazas: OTel Collector → Tempo
- Métricas: Prometheus. Además de las de infraestructura, métricas de negocio:
  `saga.venta.iniciada.total`, `saga.venta.compensada.total`, `outbox.antiguedad.segundos`,
  `reservas.vencidas.liberadas.total`, `comprobantes.pendientes.total`
- Logs: cada línea lleva `[servicio,traceId,spanId]`, así que `docker compose logs` más un
  `grep` del traceId sigue una venta por los nueve servicios. Loki se levanta con el perfil
  `observabilidad`, pero hoy ningún agente le envía los logs: es el paso siguiente, no algo
  hecho.

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
