# Diagnóstico: en qué se rompe este sistema

Este documento no lista los fallos encontrados, eso está en `ESTADO.md`. Lista los
**patrones** que se repitieron, para saber dónde mirar antes de tocar algo.

Sale de tres tandas de pruebas sobre el mismo sistema: la primera recorrió las pantallas,
la segunda probó qué pasa cuando una regla dice que no, la tercera se sentó a usarlo en el
navegador. Cada tanda encontró fallos que la anterior no podía ver, y eso ya es el primer
dato.

---

## Lo que aprendimos sobre cómo probar esto

| Cómo se probó | Qué encuentra | Qué no |
|---|---|---|
| Compilar | Errores de tipos | Nada de lo que vino después |
| `./mvnw test` | Aritmética y reglas del dominio | Cableado de Spring, SQL, contratos |
| Levantar y `curl` el camino feliz | Que la saga cierra | Todo lo que pasa cuando algo sale mal |
| `curl` los caminos de error | Compensación, validaciones, degradación | Lo que el usuario ve |
| **Abrir el navegador y apretar botones** | **Contratos frontend/backend, flujos completos** | Carga, concurrencia |
| **Romper piezas a propósito** (`verificar-resiliencia.sh`) | **Eventos descartados, datos que no sobreviven a un reinicio** | Carga |

**El navegador encontró los fallos más caros.** Activar el segundo factor dejaba al usuario
fuera del sistema; ninguna promoción descontaba nunca. Los dos pasaban compilación, pruebas
unitarias y barrido de API sin una sola señal.

**Romper piezas encontró los que perdían ventas.** Con todo sano, cada venta cerraba su
saga. Solo al cortar la base de facturación unos segundos apareció que el consumidor
descartaba el evento y la venta se quedaba sin comprobante.

---

## Los ocho patrones que se repitieron

### 1. Degradación silenciosa

El patrón más caro y el más difícil de ver, porque **no falla nada**.

- La compensación de una anulación devolvía el stock a los lotes pero no al contador. La
  saga salía verde y el inventario bajaba para siempre.
- Las promociones calculaban descuento cero y el caso de uso filtra los ceros: promoción
  cargada, visible, sin efecto en la caja.
- Las coberturas de seguro caen a "cubre cero" si ms-clientes no contesta a tiempo. La
  primera venta con seguro después de cada despliegue se cobra completa.
- Identificar un DNI que no existe dejaba la venta anónima sin decirlo.

**Qué mirar:** todo `catch` que devuelva un valor por defecto, todo `orElse(vacío)`, todo
`default ->` de un `switch`. Preguntar: si esta rama se ejecuta, ¿alguien se entera?
Degradar está bien y a veces es lo correcto; degradar en silencio no.

### 2. El contrato entre frontend y backend no lo comprueba nadie

Doce casos, todos con el mismo perfil: nombres parecidos, nadie falla, el dato no llega.

| El frontend usa | El backend expone | Efecto |
|---|---|---|
| `mfaRequerido`, `mfaToken` | `requiereMfa`, `token` | El token pendiente se guardaba como sesión |
| `fechaInicio`, `fechaFin` | `vigenciaInicio`, `vigenciaFin` | La columna Vigencia mostraba "- a -" |
| `?texto=` | `?buscar=` | El buscador ignoraba lo escrito |
| `/api/clientes/{id}/linea-credito` | `/api/creditos/cliente/{id}` | Pantalla de Créditos muerta |
| `/api/clientes/dni/{dni}` | `/api/clientes/por-dni/{dni}` | Búsqueda por DNI rota |
| `dosisYCantidadAutorizada` | `dosis` + `cantidad_autorizada` | Alta de receta imposible |
| `Factura` | `FACTURA` | La factura salía como boleta B001 |
| `BilleteraDigital`, `DescuentoPorcentaje` | `BILLETERA_DIGITAL`, `DESCUENTO_PORCENTAJE` | Alta de forma de pago y de promoción rechazadas |
| `fechaInicio` al crear regla de incentivo | `vigenciaInicio` | La regla se guardaba sin vigencia |
| `estado === 'Abierta'`, `'Confirmada'` | `ABIERTA`, `CONFIRMADA` | No aparecía el botón de cerrar caja |
| Lista de ventas | Agregado por producto y día | Reportes mostraba "Invalid Date" |
| Productos de la promoción al editar | No venían en el listado | Editar una promoción la dejaba sin productos |

**Qué mirar:** al agregar o cambiar un endpoint, cruzar lo que el frontend llama contra lo
que el backend mapea. Un script de veinte líneas que lea los `@(Get|Post|...)Mapping` y los
`api.get/post/...` y haga el diff encuentra esto en segundos; así aparecieron estos seis.

### 3. Literales de texto que no coinciden con la base

`ReglaPromocion.descuentoPara` comparaba contra `"PORCENTAJE"` y `"MONTO_FIJO"`. La tabla
guarda `DESCUENTO_PORCENTAJE`, `DESCUENTO_MONTO` y `LLEVA_N_PAGA_M`. Los tres casos caían
en el `default`.

**Qué mirar:** cualquier `switch` o `equals` sobre una columna de texto. Si la base no
tiene un `CHECK` que acote los valores, el compilador no ayuda y la prueba tampoco, salvo
que use los valores reales. Las pruebas de `ReglaPromocionTest` usan los strings tal como
están en la tabla, a propósito.

### 4. Configuración presente en unos servicios y ausente en otros

`java.time.Clock` estaba declarado a mano en cuatro de los nueve servicios. Al agregar
reglas con fecha a los otros cinco, esos cinco murieron al arrancar. Compilaba y pasaba
todas las pruebas.

**Qué mirar:** antes de inyectar algo en un servicio nuevo, comprobar que el bean exista
ahí. Si es infraestructura compartida, va en `plataforma` y no copiado nueve veces. Lo
mismo aplica a cualquier `@Bean` que hoy esté declarado en algunos servicios y no en todos.

### 5. SQL copiado de la consulta de al lado

Ocho consultas "por id" en siete servicios tenían el `ORDER BY` delante del `WHERE`. SQL
inválido. Esos endpoints nunca funcionaron y nadie lo notó porque el frontend no los usaba.

**Qué mirar:** al copiar una consulta del listado para hacer la versión por id, revisar el
orden de las cláusulas. Y desconfiar de los endpoints que nadie llama: si no los llama
nadie, nadie sabe si funcionan.

### 6. Piezas conectadas por el compose y por nada más

`ms-promociones` tenía servicio, base, caso de uso y endpoint funcionando, y `ms-ventas`
devolvía `List.of()` escrito a mano en vez de llamarlo. `ClientesDeServicios.reservarCredito()`
sigue sin tener quien lo invoque: pagar con crédito de farmacia no verifica el límite.

**Qué mirar:** un `grep` del nombre de cada método público de los adaptadores de cliente
HTTP. Si solo aparece donde se define, es una integración muerta. Un servicio que arranca,
responde y no lo llama nadie parece sano en el tablero.

Le pasó también al arqueo de caja: la caja vive en identidad, los pagos en ventas, y nadie
los unía. El cierre guardaba como esperado solo el monto inicial.

### 7. El valor por defecto de la librería decide lo que nadie decidió

Tres casos en la cuarta tanda, los tres invisibles con el sistema sano:

- **Spring Kafka sin manejador de errores** reintenta diez veces sin espera y descarta el
  evento. Nadie eligió descartar ventas; simplemente nadie eligió otra cosa.
- **Kafka crea solo los tópicos que no existen**, con una partición. La documentación
  hablaba de doce y la cadena procesaba de a una.
- **`ZoneId.systemDefault()`** en un contenedor es UTC. Las ventas desde las 7 de la noche
  caían en el reporte del día siguiente.

**Qué mirar:** por cada pieza de infraestructura, preguntar qué hace cuando nadie la
configura. Si la respuesta es "descarta", "crea con lo mínimo" o "usa la zona del
servidor", configurarla explícito y dejar una prueba que lo diga.

### 8. La documentación promete piezas que el código no tiene

El patrón que más se repitió en la cuarta tanda. Todo sonaba bien en los documentos y en la
presentación, y el jurado lo habría preguntado:

| Se decía | Lo que había |
|---|---|
| RabbitMQ para la cola de comprobantes | El contenedor levantado y ninguna línea que lo usara |
| Caché de catálogo en dos niveles con Redis | Solo Caffeine en memoria |
| Tópicos de 12 particiones con cadena de reintentos y cola muerta | Una partición y el evento descartado |
| Logs en JSON enviados a Loki | Loki levantado sin nadie que le envíe |
| Bulkhead por destino y tablero de circuitos por local | Bulkhead desactivado, sin tablero |
| Degradación definida para crédito | Crédito no se consulta |
| "Encola y reintenta con esperas cada vez más largas" | Un job cada 5 s |
| Todos los servicios invalidan su caché con `pos.catalogo.cambios` | Se publica y nadie lo consume |
| Reportes proyecta `pos.stock.movimientos` | Se publica y nadie lo consume |
| Facturación emite nota de crédito al recibir `pos.ventas.anuladas` | Facturación no escucha ese tópico |
| Reportes consume `pos.comprobantes.emitidos` | Solo lo consume ventas |

**Qué mirar:** por cada frase que describe un mecanismo, buscar el código que lo hace. Un
`grep` del nombre de la pieza (Rabbit, Redis, retry, dlq, bulkhead) en `src/main` alcanza. Si
solo aparece en `docs/` y en la presentación, o se construye o se borra de los documentos.
Para eventos, cruzar cada `outbox.registrar(..., Topicos.X, ...)` con un
`@KafkaListener(topics = Topicos.X)`: un tópico con publicador y sin listener es un
mensaje que va a un buzón que nadie abre.

---

## Qué protege hoy cada cosa

| Guarda | Qué cubre | Qué no |
|---|---|---|
| `ArquitecturaTest` (ArchUnit, 9 servicios) | Dependencias hacia adentro, dominio sin Spring, casos de uso sin adaptadores | Cualquier cosa fuera de esas tres reglas |
| `ReglaPromocionTest`, `AsignadorFefoTest` | Aritmética de descuentos y FEFO | El resto del dominio |
| `TipoComprobanteTest` | Que una factura nunca salga como boleta | Serie y correlativo, que decide facturación |
| `FiltroIdempotencyKeyTest` | Que Redis caído no impida vender | La idempotencia contra una base real |
| `KafkaConfigTest` | Destino de la cola muerta, particiones de cada tópico | El reintento en vivo, que lo cubre el script |
| `scripts/verificar.sh` | Camino feliz de punta a punta, aislamiento de bases, idempotencia | Los caminos de error |
| `scripts/verificar-reglas.sh` | Reglas de negocio, anulación con compensación y su descuento en reportes, convenio, arqueo de caja | Escrituras de administración |
| `scripts/verificar-resiliencia.sh` | Facturación, la base o Kafka caídos, evento corrupto, cuadre entre servicios | Carga, varias réplicas a la vez |
| `ManejadorErrores` (plataforma) | Que una regla de negocio llegue como 4xx con mensaje | Que el mensaje sea el correcto |

**Total: 49 pruebas automáticas y tres scripts.** Los 44 endpoints que mutan se verificaron a mano contra
el sistema levantado, no hay pruebas que los cubran en CI. Es el hueco más grande.

---

## Antes de dar por bueno un cambio

1. **¿Compila y pasan las pruebas?** Necesario y no suficiente. Los dos fallos más caros
   de esta sesión pasaron las dos cosas.
2. **¿Arranca?** Los cinco servicios sin `Clock` compilaban. `docker compose ps` tiene que
   mostrar los quince, no trece.
3. **¿Se ve en el navegador?** Si tocaste un endpoint que una pantalla usa, ábrela. El
   contrato de nombres no lo comprueba nadie más.
4. **¿La rama de error dice algo?** Provoca el fallo y lee lo que sale en pantalla. "Error
   500" y "Error 405" no son mensajes.
5. **¿Alguien llama a lo que escribiste?** `grep` del nombre del método. Si solo aparece
   donde se define, no está conectado.
6. **¿Qué pasa si lo de al lado se cae?** Si tocaste un consumidor, una dependencia HTTP o
   el outbox, corre `verificar-resiliencia.sh`. Es el único que apaga piezas.
7. **Tras reconstruir un servicio suelto**, `docker compose restart api-gateway`. Si no,
   sus rutas devuelven 404 con el servicio sano.

---

## Decisiones tomadas que conviene revisar

Ninguna de estas es técnica pura; son criterios que alguien del negocio debería confirmar.

- **`DESCUENTO_MONTO` descuenta por línea, no por unidad.** El código anterior decía por
  unidad pero nunca llegó a ejecutarse, así que no había comportamiento que preservar. Se
  eligió la lectura que menos dinero regala.
- **El comprobante se emite por el total de la venta, no por el copago.** El CPE documenta
  la venta; el seguro paga su parte aparte. Es criterio contable.
- **Una devolución consulta a ms-ventas por HTTP** en vez de guardar las líneas en
  facturación. Evita mil inserciones por segundo en el camino caliente a cambio de una
  llamada en un camino frío, pero crea una dependencia nueva entre servicios.
- **La devolución de una venta con convenio** emite la nota de crédito por el precio de la
  línea, pero no separa cuánto vuelve al cliente (que pagó el copago) y cuánto al seguro.
- **La cobertura de seguro degrada a cero en silencio.** Está documentado como decisión del
  proyecto, no se revirtió. Hay tres salidas posibles en `ESTADO.md`.
