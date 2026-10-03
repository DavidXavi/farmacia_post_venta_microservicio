# Decisiones de arquitectura

Cada entrada dice qué se decidió, contra qué alternativa, y qué haría falta para
cambiarla. Una decisión sin condición de reversión es un dogma.

Todas se justifican contra los mismos supuestos de carga:

| Dimensión | Objetivo |
|---|---|
| Locales y terminales | 500 locales, 1500 cajas |
| Ventas en pico | 200 por segundo |
| Lecturas de catálogo | 5000 req/s |
| p99 agregar línea | < 200 ms |
| p99 confirmar venta | < 500 ms |
| Disponibilidad del cobro | 99.9% |

Si estos números fueran otros, varias de estas decisiones serían distintas.

---

## 1. Nueve servicios, no cuatro ni quince

**Decisión.** identidad, catálogo, inventario, clientes, crédito, promociones, ventas,
facturación, reportes. Más el gateway.

**Alternativa descartada: cuatro.** Fue la primera propuesta y agrupaba por cohesión
transaccional (qué cambia junto). Correcta para el criterio de entonces. Al fijar el
objetivo de carga, el criterio tuvo que incluir perfil de carga y radio de falla, y eso
produjo tres cortes más:

- Catálogo sale de inventario: lectura masiva cacheable contra escritura con contención.
  Juntos, los locks de stock se comen el pool que atendía las lecturas.
- Facturación sale de ventas: es el único que habla con un tercero lento y caído.
- Reportes sale de ventas: los escaneos analíticos no pueden tocar la base transaccional.

**Alternativa descartada: uno por contexto (13 a 15).** Empeoraría el sistema. Cada salto
síncrono se paga dos veces: la cola de latencia se amplifica (con 5 saltos, el p99 pasa a
ser el p95) y la disponibilidad se multiplica hacia abajo (0.999⁵ = 99.5%).

**Qué la cambiaría.** Si las pruebas de carga muestran que promociones no aporta
aislamiento medible, vuelve a ser una librería dentro de ventas y quedan ocho.

---

## 2. Una base de datos por servicio, impuesta por el motor

**Decisión.** Nueve bases PostgreSQL. `REVOKE CONNECT ON DATABASE ... FROM PUBLIC` y un
usuario por servicio sin GRANT cruzado.

**Por qué el motor y no la disciplina.** Nadie va a "consultar rapidito la tabla del otro
para salir del apuro" porque el motor no lo permite. Una regla que depende de que todos se
acuerden es una regla que se rompe el primer viernes con incidente.

**Qué se pierde.** El JOIN entre contextos. Un reporte de ventas con el nombre del vendedor
y del local era un JOIN de tres tablas; ahora ninguna base tiene las tres.

**Dónde se recupera.** En ms-reportes, cuyo read model guarda la venta ya desnormalizada
porque el evento `VentaConfirmada` trae los nombres adentro. La consulta que era un JOIN
ahora es un SELECT sobre una tabla plana, y corre más rápido.

**Topología física.** Producción: nueve instancias separadas con sus réplicas, que es donde
está la ganancia real de capacidad. Desarrollo: un contenedor con nueve bases y nueve
usuarios sin permisos cruzados. El aislamiento lógico es idéntico y el código no nota la
diferencia.

---

## 3. Outbox transaccional, no publicación directa a Kafka

**Decisión.** El caso de uso escribe el evento en la tabla `outbox` dentro de la misma
transacción que el cambio de negocio. Un publicador por sondeo lo empuja a Kafka después.

**El problema que resuelve.** Escribir en Postgres y publicar en Kafka son dos sistemas. Si
el segundo falla después del primero, la venta quedó confirmada y el stock nunca se
descontó. Nadie se entera hasta el inventario físico, semanas después. Es corrupción
silenciosa.

**Alternativa descartada: Debezium.** Es el escalón siguiente, no el primero. Un sondeo cada
200 ms con lotes de 200 filas y `FOR UPDATE SKIP LOCKED` aguanta con holgura los 200
eventos/s proyectados, y no agrega Kafka Connect ni replicación lógica al stack.

**Qué la cambiaría.** La métrica `outbox.antiguedad.segundos` por encima de 60 s de forma
sostenida. Está alertada.

---

## 4. Reserva de stock con un UPDATE condicional atómico

**Decisión.** Una tabla `stock_local` con una fila por (producto, local) y contadores
`disponible` y `reservado`. Reservar es un solo statement:

```sql
UPDATE stock_local
   SET reservado = reservado + :cant
 WHERE producto_id = :p AND local_id = :l
   AND disponible - reservado >= :cant;
```

**Por qué.** Sin `SELECT FOR UPDATE`, sin transacción larga, sin deadlock posible. Cero
filas afectadas significa que no hay stock, y el cajero se entera en un round trip.

**La contención queda acotada al local**, que es el shard natural del negocio: 500 boticas
vendiendo el mismo paracetamol son 500 filas independientes, no una cola. La tabla está
particionada por hash de `local_id` para repartir también a nivel físico.

**Alternativa descartada: lock optimista de JPA.** Reintenta en la aplicación, y a 200
ventas/s sobre los productos estrella eso es una tormenta de reintentos justo donde no se
puede.

**El lote se asigna después.** El FEFO (primero el que vence antes, obligatorio en farmacia)
corre en el consumidor del evento, no en el camino crítico. El cajero no necesita saber de
qué lote sale hasta el despacho, y meter esa escritura en la ruta caliente agregaría
contención donde ya hay.

**Reservas con TTL de 15 minutos.** Una caja que se cuelga a mitad de venta no puede dejar
el stock bloqueado para siempre: el producto aparecería agotado teniéndolo en el anaquel.
Un job libera las vencidas y el stock vuelve solo.

---

## 5. Idempotencia en dos niveles

**En la API.** Todo POST, PUT y PATCH acepta `Idempotency-Key`. La respuesta se guarda en
Redis 24 h; un reintento con la misma clave devuelve la respuesta original sin volver a
ejecutar. Confirmar venta y registrar pago lo exigen.

**En los consumidores.** Tabla `evento_procesado (evento_id, consumidor)` con PK compuesta.
El INSERT que viola la PK identifica el duplicado. Lo decide el motor, no un
`if (yaProcesado)` que dos pods pueden evaluar a la vez y pasar los dos.

**Alternativa descartada: exactly-once de Kafka.** Cuesta rendimiento, complica el
consumidor y no cubre el efecto secundario (la fila que ya se escribió en Postgres).
Consumidor idempotente resuelve el problema real.

**Por qué Redis y no una tabla.** Las claves son efímeras, de altísima frecuencia y no
necesitan sobrevivir a nada. Una tabla sería escritura transaccional gratis en el camino
más caliente.

**Si Redis se cae, la venta sigue.** El filtro atrapa el error, lo anota en el log y deja
pasar la petición sin la protección de reintento. Redis no es stock ni crédito: no puede
impedir una venta. Mientras está caído, confirmar dos veces la misma venta igual lo
rechaza el dominio (solo se confirma una venta en BORRADOR). Lo prueba
`FiltroIdempotencyKeyTest`.

---

## 6. Saga con compensación, sin transacción distribuida

**Decisión.** Confirmar venta es una transacción local (estado + saga abierta + outbox) y
nada más. Inventario, crédito, facturación y reportes se enteran por el evento.

**Alternativa descartada: dos fases sobre las nueve bases.** Sería un lock distribuido
sostenido durante toda la operación. A 200 ventas/s no termina bien, y convierte la
disponibilidad del conjunto en el producto de las disponibilidades de todos.

**Lo que cuesta.** Una ventana de inconsistencia de segundos entre que la venta se confirma
y el stock se descuenta de los lotes. Es el precio real de partir el monolito, no un
defecto de la implementación, y se documenta como tal.

**La tabla `saga_venta`.** Sin ella, una venta que quedó a medias porque un consumidor falló
es invisible: nadie sabe si hay que compensar ni cuántas van. Con ella, la métrica de
compensaciones tiene de dónde salir y "ventas de hoy sin comprobante" se contesta con un
SELECT.

---

## 7. Solo stock y crédito pueden impedir una venta

**Decisión.** Timeout y circuit breaker por destino, con comportamiento definido para cada
falla. Sin bulkhead de hilos propio: está desactivado (`disable-thread-pool: true`) porque el
pool aparte perdía el `SecurityContext` y las llamadas salían sin token.

| Llamada | Timeout | Si falla |
|---|---|---|
| ventas → inventario | 500 ms | Falla la línea, el cajero lo ve |
| ventas → crédito | 800 ms | Falla el pago a crédito, se ofrece efectivo. **Hoy no se consulta** (`ESTADO.md`, punto 5) |
| ventas → catálogo | 200 ms | Falla la línea: sin precio no se cotiza. El caché vive en ms-catalogo, no en ventas, así que no cubre la caída del servicio |
| ventas → promociones | 300 ms | Venta sin promoción, degradación registrada |
| ventas → clientes | 300 ms | Venta anónima |
| facturación → SUNAT | 15 s | Queda pendiente y se reintenta cada 5 s, la venta ya terminó |
| ventas → identidad | 300 ms | El reporte sale sin nombre de local |
| cualquiera → Redis | 200 ms | Sin límite de peticiones ni atajo de reintento, la venta sigue |

**El razonamiento.** Un sistema que se niega a cobrar porque el servicio de promociones está
lento no es resiliente, le hizo perder la venta a la botica. El cliente igual se lleva su
producto.

**La degradación se registra.** Cada rama de degradación deja una línea en el log con su
motivo, y el estado de los circuitos sale como métrica en Prometheus. No hay tablero de
Grafana armado todavía. La degradación silenciosa es peor que la caída ruidosa.

---

## 8. Validación local del JWT, con JWKS cacheado

**Decisión.** ms-identidad firma con RSA y publica la clave pública en
`/.well-known/jwks.json`. Los demás validan la firma en memoria, sin llamar a nadie.

**Por qué RS256 y no HS256.** Con clave simétrica, cada servicio necesitaría el secreto para
validar, y entonces cualquiera de los diez podría EMITIR tokens, no solo validarlos.

**Por qué validación local.** Si identidad se cae, las cajas siguen vendiendo con los tokens
que ya tienen y lo único que no se puede hacer es iniciar sesión nueva. Preguntarle a
identidad por cada request lo convertiría en el punto único de falla de las 5000 req/s del
sistema entero.

**Cada servicio valida por su cuenta**, aunque el gateway ya lo hizo. Un pod comprometido
dentro del clúster no debería poder saltarse la autorización por estar del lado de adentro.

**Acceso de 15 minutos, no de 480.** El proyecto original usaba 8 horas. Con 1500
terminales, esa ventana para un token robado es demasiado ancha. El refresh de 8 horas, que
sí se puede revocar en base de datos, cubre la comodidad del cajero.

---

## 9. Caché de catálogo en memoria del pod

**Decisión.** Caffeine en memoria de cada réplica de ms-catalogo, TTL 60 s. El pod que
cambia un producto limpia su caché al instante y publica `pos.catalogo.cambios`.

**Por qué en memoria y no en Redis.** Un viaje a Redis por cada producto escaneado son
5000 round trips por segundo que no hacen falta: el 99% de las lecturas son de los mismos
doscientos productos y esos caben de sobra en la memoria del pod. Leer de la memoria del
propio proceso no tiene salto de red.

**Por qué TTL corto.** Las otras réplicas no se enteran al instante del cambio. Con TTL de
60 s el peor caso es un minuto de precio desactualizado y el sistema se corrige solo, sin
invalidación distribuida que mantener.

**Lo que no se hizo: Redis como segundo nivel.** La primera versión de este documento lo
proponía (TTL 10 min, compartido entre réplicas), pero nunca se implementó y al revisarlo
no hace falta: solo ayudaría a un pod recién arrancado, que tarda unos segundos en llenar
su caché. Entra si las pruebas de carga muestran que ese arranque en frío pega en el p99.

**Es la palanca más grande del sistema.** 10x a 100x en capacidad de lectura. Todo lo demás
(más réplicas, más conexiones, particionar) da mejoras lineales.

---

## 10. Un módulo Maven por servicio, con ArchUnit

**Decisión.** Clean Architecture por paquetes (`domain`, `usecases`, `adapters`,
`infrastructure`) dentro de un solo módulo Maven por servicio.

**Alternativa descartada: los siete módulos del proyecto original, replicados.** Serían 63
`pom.xml` para probar lo que un test de ArchUnit de 20 líneas prueba mejor, más rápido y sin
recompilar el reactor entero.

**Qué la cambiaría.** Que el trabajo califique explícitamente la separación física en
módulos. Es un cambio mecánico.

---

## 11. JdbcClient en los caminos calientes, no JPA

**Decisión.** Los adaptadores de persistencia de inventario, ventas, crédito y catálogo usan
`JdbcClient` con SQL explícito.

**Por qué.** En estos servicios no hay grafo de objetos ni ciclo de vida que mapear. Una
entidad JPA con su repositorio, su mapper y su versión optimista serían cuarenta líneas para
hacer peor lo que una sentencia hace bien. Además evita el N+1 accidental y la sesión
abierta más tiempo del necesario.

**Lo que se conserva de Clean Architecture.** El puerto sigue siendo la interfaz. Que el
adaptador use JDBC en vez de JPA es exactamente el tipo de decisión que la arquitectura
permite cambiar sin tocar el dominio.

---

## 12. Un solo broker: Kafka, sin RabbitMQ

**Decisión.** Toda la mensajería va por Kafka. RabbitMQ salió del sistema el 27 de
setiembre de 2026.

**De dónde venía.** El proyecto anterior usaba Kafka para eventos y RabbitMQ para la cola
de emisión de comprobantes. Al partirlo en microservicios, RabbitMQ se quedó en el compose
y en el pom de facturación, pero ninguna clase lo usaba: facturación ya consumía Kafka y
enviaba a SUNAT desde una tabla. Estaba prendido, gastando memoria, sin hacer nada.

**Por qué no hace falta.** Lo que se le pide a RabbitMQ es trabajo dirigido a un solo
consumidor con reintento por mensaje. Eso ya lo resuelve la tabla `comprobantes`:

- Cada comprobante nace PENDIENTE al llegar `VentaConfirmada`.
- Un job cada 5 s toma lotes de 50 con `FOR UPDATE SKIP LOCKED`: seis réplicas se
  reparten el trabajo sin tomar dos veces el mismo.
- Si SUNAT falla, el comprobante sigue PENDIENTE y el ciclo siguiente lo reintenta.
- La tabla hace falta igual, porque SUNAT exige saber qué se emitió, qué se rechazó y qué
  falta (resumen diario de boletas, reenvíos, comunicaciones de baja). Con una cola además
  habría dos fuentes de verdad sobre lo mismo, y cuando no cuadren nadie sabe a cuál creer.

El reparto de trabajo que se le atribuye a RabbitMQ Kafka también lo da: con 12
particiones y un grupo de consumidores, cada evento lo procesa una sola réplica.

| | Con RabbitMQ | Solo Kafka |
|---|---|---|
| Brokers que operar, monitorear y actualizar | 2 | 1 |
| Contenedores en el perfil completo | 17 | 16 (15 desde que salió Schema Registry) |
| Memoria en la laptop | 320 MB más de límite | |
| Fuente de verdad de un comprobante | tabla y cola | la tabla |
| Estado de un comprobante ante SUNAT | hay que cruzar tabla y cola | un SELECT |
| Credenciales que custodiar | Postgres y RabbitMQ | Postgres |

**Lo que se pierde.**

- Reintento con espera distinta por mensaje. Hoy todos los pendientes se reintentan cada
  5 s. Si SUNAT pidiera espaciar los envíos, se agrega una columna `proximo_intento` y el
  job la filtra: una línea de SQL, no un broker.
- Algo de latencia: el comprobante sale hasta 5 s después, no apenas se encola. Para un
  trámite que SUNAT permite enviar días después, no importa.
- La cola muerta nativa de RabbitMQ. La reemplaza la alerta sobre la métrica
  `comprobantes.pendientes.total`.

**En qué afecta.** No cambió ninguna regla de negocio ni ningún flujo: el código que emite
comprobantes es el mismo. Los nueve microservicios siguen siendo nueve, porque RabbitMQ era
infraestructura, no un servicio. Lo que se tocó:

| Archivo | Cambio |
|---|---|
| `docker-compose.yml` | Fuera el contenedor `rabbitmq`, las variables `RABBITMQ_*` y el `depends_on` de `ms-facturacion` |
| `services/ms-facturacion/pom.xml` | Fuera `spring-boot-starter-amqp` |
| `contracts/.../Topicos.java` | Fuera la cola, el exchange y la routing key |
| `.env.example`, `k8s/base/configmap.yaml` | Fuera `RABBITMQ_USER` y `RABBITMQ_PASSWORD` |
| Frontend, pantalla Actividad | Decía que la venta encolaba en RabbitMQ. No era cierto |

El único contenedor modificado es `ms-facturacion`, que pierde una dependencia que no
usaba. `rabbitmq` desaparece. Los otros quince no cambian.

**Qué la cambiaría.** Un caso que Kafka resuelve mal: prioridad por mensaje, espera
distinta por mensaje a gran escala, colas creadas al vuelo por local, o respuesta asíncrona
tipo RPC. Si aparece, entra RabbitMQ para ese caso y solo para ese.

---

## 13. Un evento que falla se reintenta con espera y termina en la cola muerta

**Decisión.** Todos los consumidores comparten un manejador de errores
(`plataforma/mensajeria/KafkaConfig`): seis reintentos con espera creciente (1, 2, 4, 8, 16
y 30 s) sobre la misma partición y, si igual falla, el evento se copia a `<topico>.dlq`.
Recién ahí avanza el offset.

**Qué había antes.** Nada, y eso era lo grave. El manejador por defecto de Spring Kafka
reintenta diez veces sin espera y después descarta el evento. Un corte de un segundo en
la base de facturación dejaba una venta sin comprobante para siempre, sin un solo error a
la vista. La documentación describía una cadena de tópicos de reintento que nunca se
construyó.

**Alternativa descartada: tópicos de reintento** (`@RetryableTopic`, un tópico por cada
espera). No frenan la partición mientras esperan, pero triplican los tópicos y rompen el
orden por local: una anulación podría procesarse antes que la confirmación que anula, que
es justo lo que la clave por local existe para evitar.

**Lo que se paga.** Mientras un evento se reintenta, su partición espera hasta un minuto.
Con doce particiones y la clave por local, eso es una botica demorada, no la cadena. Lo
prueba `verificar-resiliencia.sh`: con un evento corrupto reintentándose, una venta normal
se facturó igual.

**Qué la cambiaría.** Que el lag de un grupo crezca de forma sostenida por eventos que se
reintentan. Ahí entra `@RetryableTopic` para ese consumidor, aceptando el costo en orden.

---

## 14. Lo que deliberadamente no se hizo

| Pieza | Por qué no | Cuándo entraría |
|---|---|---|
| Service mesh | Los Service de Kubernetes más Resilience4j cubren descubrimiento, balanceo y resiliencia | mTLS automático a esta escala o tráfico dividido por versión |
| Eureka o Consul | El DNS de Kubernetes ya resuelve. Eureka resuelve un problema que Kubernetes no tiene | Balanceo entre clústeres |
| Config Server | ConfigMaps y Secrets alcanzan | Recarga de configuración sin reiniciar |
| Debezium | El publicador por sondeo aguanta el volumen proyectado | `outbox.antiguedad` > 60 s sostenido |
| ClickHouse | Postgres particionado con agregados cubre los reportes actuales | Un reporte que tarde más de lo aceptable |
| Avro con codegen | JSON plano alcanza: el payload es chico y los contratos son records de `contracts` | Si el tamaño del payload o la estrictez lo exigieran |
| Schema Registry | Estuvo levantado y ningún servicio registraba esquemas: los eventos viajan como JSON y su contrato es el record compartido de `contracts`. Salió el 28 de setiembre, después de caerse por el tópico `_schemas` creado sin compactar | Consumidores fuera de este repo, o equipos que desplieguen contratos por separado |
| Event sourcing | Outbox y eventos de integración sí; reconstruir el estado desde el log, no | Un requisito real de auditoría temporal completa |
| GraphQL o BFF | Un frontend con un gateway REST alcanza | App móvil con necesidades distintas |
| Tópicos de reintento | El reintento en la misma partición con cola muerta no pierde eventos y conserva el orden por local | Lag sostenido por eventos reintentándose (decisión 13) |

---

## 15. Cuándo esta arquitectura es la decisión equivocada

Con 20 locales y 40 cajas, es una pérdida neta. Un monolito con réplicas de lectura y caché
aguanta ese volumen de sobra, con un décimo del costo operativo.

El punto de quiebre:

- El pico supera lo que una sola Postgres afinada sostiene junto con los reportes,
  aproximadamente 50 a 80 ventas/s.
- El equipo pasa de unas 8 personas y los despliegues se bloquean entre sí.
- Una integración externa lenta empieza a costar ventas de verdad.
- Un reporte gerencial ya afecta de forma medible el p99 de las cajas.

Saber cuándo no aplicar un patrón demuestra más criterio que saber aplicarlo.
