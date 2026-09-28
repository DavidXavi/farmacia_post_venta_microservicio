# Estado del proyecto

Última sesión: **19 de setiembre de 2026**, tercera tanda. La primera recorrió las
pantallas y el camino feliz. La segunda fue a buscar qué pasa cuando una regla dice que no
y cuando hay que deshacer. La tercera se sentó a usar el sistema en el navegador, pantalla
por pantalla y botón por botón, y ahí aparecieron los que ninguna prueba de API veía: la
pantalla de Seguridad cerraba la sesión, ningún formulario de alta existía, y ninguna
promoción habría descontado nunca.

Los 27 endpoints de escritura están portados. Las pruebas pasaron de 1 a 40.

Este documento es el punto de retomada. Dice qué funciona (verificado, no supuesto), qué
falta, y los fallos que ya se encontraron para no volver a descubrirlos.

---

## Cómo volver a levantarlo

```bash
cd C:\Users\User\source\repos\arquitectura_3_t1
docker compose --profile completo up -d
./scripts/verificar.sh          # camino feliz de punta a punta
./scripts/verificar-reglas.sh   # reglas, anulación y convenio
```

Los dos tienen que terminar en "Todo pasó."

**Si se reconstruye un servicio suelto** (`docker compose up -d --build ms-ventas`),
reiniciar después el gateway: `docker compose restart api-gateway`. Si no, sus rutas
responden 404 aunque el servicio esté sano. Está anotado más abajo.

El `.env` ya está creado y no se versiona. Si se pierde, regenerar las claves JWT con
`./scripts/generar-claves-jwt.sh` y volver a completar `DB_PASSWORD`.

| Acceso | URL |
|---|---|
| Frontend | http://localhost:5175 |
| Gateway | http://localhost:8095 (no 8080: ver abajo) |
| Kafka UI | http://localhost:8092 |
| Postgres | localhost:5452, usuario `postgres` |

Credenciales de prueba: `admin` / `Admin123!`

**Ojo con el puerto.** En esta máquina el 8080 lo ocupa `javid-gateway`, de otro proyecto.
Por eso el `.env` tiene `GATEWAY_PORT=8095` y `FRONTEND_API_URL=http://localhost:8095`. El
proyecto sigue documentando 8080 como valor por defecto para quien lo clone limpio.

---

## Lo que está verificado funcionando

Verificado quiere decir que se ejecutó y se comprobó el resultado, no que debería andar.

### El camino crítico, de punta a punta

Una venta completa hecha desde la interfaz, con la saga cerrándose:

| Paso | Resultado comprobado |
|---|---|
| Login y emisión de JWT | Token RS256 de 716 caracteres |
| JWKS publicado | `kid=pos-1`, clave fija |
| Abrir caja | Sesión activa, monto inicial |
| Abrir venta | Estado BORRADOR |
| Agregar producto | Precio de catálogo, IGV 18%, stock reservado |
| Registrar pago | Total pagado actualizado |
| Confirmar | Estado CONFIRMADA |
| Outbox → Kafka | Evento publicado |
| ms-inventario | FEFO asignó el lote L0005 (vence 2027-02-13) |
| ms-facturacion | Comprobante B001-4, estado ACEPTADO |
| ms-reportes | Read model proyectado |
| `saga_venta` | COMPLETADA, stock y comprobante en true |

### Las reglas y la compensación

Lo que agregó la segunda tanda, en `scripts/verificar-reglas.sh`. Ninguna de estas líneas
pasaba antes.

| Comprobación | Resultado |
|---|---|
| Producto que requiere receta | 400 "El producto Amoxicilina 500mg requiere receta" |
| Cantidad cero o negativa | 400 "cantidad must be greater than 0" |
| Más unidades de las que hay | 400 "Sin stock de Paracetamol 500mg. Solo quedan 140 unidades" |
| Confirmar una venta sin líneas | 400 "No se puede confirmar una venta sin lineas" |
| Anular una venta confirmada | 202, estado ANULADA, `VentaAnulada` publicada |
| El stock vuelve al anaquel | 140 a 136 al vender, 136 a 140 al anular |
| Convenio de seguro al 50% | sobre 14.75 cubre 7.38, copago 7.37 |

### Propiedades de la arquitectura, comprobadas en vivo

- **Aislamiento de bases.** `u_catalogo` no puede conectarse a `pg_ventas`. Lo impone el
  motor, no la disciplina.
- **Idempotencia.** Confirmar dos veces con la misma `Idempotency-Key` deja un solo
  comprobante, no dos.
- **Identidad no es punto único de falla.** Se emitió un token, se reinició ms-identidad,
  y el token siguió valiendo. Las cajas no se enteran de que el login se cayó.
- **Sin stock no se vende, y se dice por qué.** Al pedir Ibuprofeno en un local sin
  existencias, el sistema respondió "Solo quedan 0 unidades" en vez de fallar en seco.
- **Sesión de 15 minutos.** El token expira y la interfaz manda al login. Comprobado.

### Los 16 contenedores

Todos arriba y sin reinicios: postgres, redis, kafka, schema-registry, kafka-ui,
api-gateway, los nueve microservicios y el frontend. Eran 17: RabbitMQ salió el 27 de
setiembre porque ningún servicio lo usaba (decisión 12 de `DECISIONES.md`).

### Consumo de RAM, medido

| | Medido |
|---|---|
| 17 contenedores del perfil completo | **4,5 GB** |
| Más la VM de WSL2 | ~6 GB en total |
| Mínimo para levantarlo | 8 GB, justo |
| Cómodo | 16 GB |

La estimación previa de 6,3 GB era alta: cada JVM se queda en 250-310 MB, no en 400.

### Los endpoints de lectura que el frontend consume

Todos responden 200 con datos reales. Verificado con un barrido de curl, cruzando además
cada llamada del frontend contra los mapeos del backend. Ese cruce es lo que destapó las
dos rutas mal escritas (`linea-credito` y `dni`) que ya están corregidas.

Quedan 27 llamadas del frontend sin endpoint detrás: las 24 de escritura del punto 1, las
tres de alta de MFA y el `PATCH` de promoción por línea.

### Pantallas recorridas en el navegador

Funcionan con datos: Venta (POS), Caja, Productos, Lotes, Inventario, Clientes, Recetas,
Promociones, Convenios, Líneas de crédito, Catálogos, Usuarios, Reportes, Actividad
(Kafka), Devoluciones.

---

## Lo que falta

### 1. Pruebas de carga

Los 200 ventas/s del diseño siguen sin medirse. Con eso se decide si ms-promociones se
queda separado o vuelve a ser una librería dentro de ms-ventas.

### 2. Auditoría: ya llega, pero solo de cinco operaciones

El tópico `pos.auditoria` dejó de estar vacío. Publican: bloquear y retirar lote
(ms-inventario), validar receta (ms-clientes), otorgar o ajustar crédito (ms-credito),
desactivar promoción (ms-promociones) y alta de usuario (ms-identidad).

Falta lo que toca el dinero desde ventas: anular una venta y cambiar un precio de
catálogo. Las dos son de las primeras que una auditoría real va a pedir.

### 3. Lotes próximos a vencer

`/api/reportes/lotes-proximos-a-vencer` devuelve vacío **a propósito**. Los lotes viven en
`pg_inventario` y ms-reportes no puede leer esa base, que es exactamente la regla del
sistema. Servirlo de verdad requiere proyectarlos al read model desde el tópico
`pos.stock.movimientos`, igual que se hace con las ventas.

Se dejó vacío y documentado antes que romper el aislamiento con una consulta cruzada.

### 4. MFA: el QR no se probó con un teléfono de verdad

El alta ya existe (`GET /api/auth/mfa/estado`, `POST /registro`, `POST
/registro/confirmar`, `DELETE /api/auth/mfa`) y la pantalla dejó de cerrar la sesión. Lo
que no se comprobó es el ciclo completo con Google Authenticator: escanear el QR, que la
app genere el código y que el login lo pida en la siguiente entrada. Eso necesita un
teléfono.

El secreto se guarda deshabilitado y solo se activa cuando el usuario escribe un código
válido. Es a propósito: si naciera activo, cerrar la pestaña antes de escanear te deja
fuera de tu propia cuenta.

### 5. Promociones y crédito: uno cableado, el otro no

**ms-promociones no se consulta nunca.** `GET /api/ventas/{id}/promociones-disponibles` en
ms-ventas devuelve `List.of()` escrito a mano. El servicio existe, tiene su base, su caso
de uso y su endpoint `/api/promociones/evaluar` funcionando, y nadie lo llama. El botón de
aplicar promoción del POS además llama a `PATCH /api/ventas/{id}/detalles/{id}/promocion`,
que tampoco existe.

**ms-credito tampoco.** `ClientesDeServicios.reservarCredito()` está escrito y no lo invoca
ningún caso de uso. Pagar con la forma de pago "Crédito de farmacia" no reserva crédito ni
verifica el límite: registra el pago y ya.

Los dos tienen el mismo peso en la presentación (nueve servicios, la saga, la degradación)
y conviene decidir si se cablean o si se dice en voz alta que están de muestra.

### 6. Cobertura de pruebas

De 1 prueba a 40. `ArquitecturaTest` con ArchUnit existe ahora en los nueve servicios, que
es lo que `CLAUDE.md` daba por hecho: comprueba que las dependencias apunten hacia adentro,
que el dominio no sepa de Spring ni de JPA, y que los casos de uso no toquen los
adaptadores. La primera vez que corrió encontró una violación real en ms-ventas, un caso de
uso importando su cliente HTTP; se corrigió con `ServiciosExternosPort`.

Cinco servicios no tienen paquete `domain` y está bien: catálogo, clientes, crédito,
facturación y reportes son consulta y altas, sin reglas que modelar. El test usa
`withOptionalLayers` para no obligarlos a inventar un dominio vacío.

Lo que sigue faltando es prueba de los casos de uso nuevos: los 27 endpoints de escritura
se verificaron corriendo contra el sistema levantado, no con pruebas que corran en CI.

### 7. Detalle menor

Al confirmar, la interfaz dice "Comprobante null". El comprobante se emite de forma
asíncrona y en ese momento genuinamente no existe todavía. El mensaje honesto sería
"comprobante en emisión".

---

## Los fallos que ya se encontraron

Están anotados para que nadie los vuelva a descubrir. Ninguno se veía al compilar.

### Tercera tanda: lo que solo se ve usando el sistema

Ninguno de estos se veía desde la API. Aparecieron abriendo el navegador y apretando
botones, que es lo que hace un cajero.

**Activar el segundo factor te dejaba fuera del sistema.** Cuatro desajustes encadenados,
y el conjunto solo se podía disparar una vez que existía el alta de MFA, cosa que hasta
esta tanda no existía. El gateway no tenía `/api/auth/mfa/verificar` entre sus rutas
públicas: el login devolvía un token pendiente, y el único endpoint que lo convierte en
token real exigía un token real. Encima el frontend leía `mfaRequerido` y `mfaToken`
cuando el backend manda `requiereMfa` y `token`, así que guardaba el token PENDIENTE como
si fuera una sesión buena: el usuario entraba, veía el menú completo y todo le respondía
403, porque ese token viaja con la lista de roles vacía. Y mandaba `mfaToken` donde el
backend espera `tokenPendiente`.

Verificado de punta a punta: alta, QR, código calculado del secreto, login pidiendo el
segundo factor, y baja.

**Ninguna promoción habría descontado nunca.** `ReglaPromocion.descuentoPara` comparaba
contra `"PORCENTAJE"` y `"MONTO_FIJO"`; la tabla guarda `DESCUENTO_PORCENTAJE`,
`DESCUENTO_MONTO` y `LLEVA_N_PAGA_M`. Los tres casos caían en el `default` y devolvían
cero, y el caso de uso filtra los descuentos en cero. Resultado: promociones cargadas,
visibles en la pantalla, sin ningún efecto en la caja. No fallaba nada, simplemente no
descontaba. Corregido y con prueba: `ReglaPromocionTest`, ocho casos.

**`java.time.Clock` no era un bean en cinco de los nueve servicios.** Estaba declarado a
mano en cuatro. Al agregar reglas con fecha a los otros cinco (receta emitida en el
futuro, lote ya vencido, vigencias), esos cinco murieron al arrancar con "No qualifying
bean of type java.time.Clock". Compilaba y pasaba los tests. Ahora vive en
`plataforma/tiempo/RelojConfig` y los duplicados se borraron.

**Ocho consultas "por id" llevaban el `ORDER BY` delante del `WHERE`.** SQL inválido, en
siete servicios. El `ORDER BY` se había copiado del listado de al lado. Esos endpoints
nunca funcionaron.

**Dos rutas más con el nombre cambiado.** El backend devuelve `vigenciaInicio` y el
frontend leía `fechaInicio`, por eso la columna Vigencia de Promociones mostraba "- a -".
Y el buscador de productos mandaba `texto` donde el backend espera `buscar`, así que
ignoraba lo que se escribía y devolvía el listado entero.

**Detalles de pantalla que un cajero ve todo el día.** El desplegable de cajas repetía
"Caja 1, Caja 2, Caja 1" sin decir de qué local y ofrecía cajas desactivadas. Las fechas
de lotes salían como `2026-08-12T00:00:00.000Z`. El alta de usuario venía rellenada por el
navegador con admin y su contraseña. Al aplicar un convenio, el total seguía mostrando el
importe completo y no el copago, que es lo que hay que cobrar.

### Segunda tanda: seis que solo aparecen cuando algo sale mal

Los seis pasaban desapercibidos porque el camino feliz funciona. Se encontraron probando
lo contrario: pedir de más, pedir mal, y deshacer.

**Anular una venta daba 500 y la saga de compensación nunca arrancaba.**
`VentaJdbcAdapter.guardar()` borra las líneas y las reinserta con los mismos id en vez de
reconciliar altas y bajas. Antes de confirmar da igual, pero cuando ms-inventario ya
asignó los lotes, `detalle_venta_lotes` apunta a esas líneas y el DELETE choca con la
clave foránea a mitad de la transacción. Corregido en `V4__fk_lotes_diferida.sql`: la
restricción se difiere al commit, que es cuando las filas ya volvieron. No se puso
`ON DELETE CASCADE` porque borraría la trazabilidad del lote en cada guardado posterior a
la confirmación.

**La compensación devolvía el stock a los lotes pero no al contador.**
`LiberarStockUseCase` llamaba a `stock.liberar()`, que baja `reservado`. Pero cuando la
salida ya se aplicó, `reservado` ya había bajado en `confirmarSalida` y lo que faltaba
reponer era `disponible`. Resultado: los lotes recuperaban las unidades, `stock_local` no,
y cada anulación perdía stock en silencio. La saga salía verde. Se agregó
`StockPort.devolver()`. Es el fallo más caro de los seis: no falla nada, solo el inventario
se va alejando de la realidad.

**Toda regla de negocio salía como 500 sin cuerpo.** No había un solo
`@RestControllerAdvice` en el proyecto. "Requiere receta", "no hay stock" y "venta sin
líneas" llegaban al cajero como error interno, y al monitoreo como caída del servicio.
Ahora hay `ManejadorErrores` en `plataforma`, y las dos excepciones de dominio cuelgan de
`IllegalArgumentException` y `IllegalStateException`: tipos del JDK, así que el dominio
sigue sin depender de nada y plataforma no nombra ninguna entidad.

**`@Valid` faltaba en cinco controladores.** Los `@NotNull` y `@NotBlank` de los records
eran decorativos: nadie los evaluaba. Una cantidad negativa viajaba hasta ms-inventario y
volvía como 500. Con `@Valid` puesto y `@Positive` en la cantidad, se corta en el borde.

**Reservar stock y reservar crédito no tenían fallback.** Resilience4j lanzaba
`NoFallbackAvailableException` y el cajero veía "error interno". Que la venta falle está
bien y es lo decidido; lo que faltaba era decir por qué. Ahora fallan con motivo legible.

**Dos rutas que el frontend llamaba y no existen.** `/api/clientes/{id}/linea-credito`
(la real es `/api/creditos/cliente/{id}`) y `/api/clientes/dni/{dni}` (la real es
`por-dni`). La pantalla de Créditos no funcionaba y la página cargaba igual, que es
justamente por qué no se había notado.

### El gateway se queda con 404 tras reconstruir un servicio

Después de `docker compose up -d --build ms-ventas`, el gateway devolvió 404 en todas las
rutas de ese servicio (`/api/ventas` y `/api/formas-pago`) mientras el servicio respondía
bien por dentro y los otros ocho seguían enrutando. Se arregla con
`docker compose restart api-gateway`.

La causa exacta queda abierta: forzar la recreación del contenedor manteniendo la misma IP
no lo reproduce, así que no es solo DNS cacheado. En Kubernetes el Service da una IP
estable y no debería morder, pero conviene confirmarlo antes de desplegar.

### La primera llamada entre servicios tras arrancar se pasa del presupuesto

El cliente HTTP hacia ms-clientes tiene 300 ms y hacia ms-catálogo 200 ms. Con la JVM
fría, la primera llamada se pasa y cae el timeout. En el catálogo eso da un 409 honesto.
En las coberturas de seguro **la degradación es silenciosa**: devuelve cobertura cero, el
cajero cobra el total y nadie se entera. Del segundo pedido en adelante responde bien.

Es un fallo de dinero, no de disponibilidad: la primera venta con seguro después de cada
despliegue se cobra completa. Tres salidas posibles, ninguna aplicada todavía porque es
una decisión de diseño: calentar las conexiones al arrancar, subir el presupuesto solo de
esa llamada, o hacer visible la degradación devolviendo "no se pudo verificar la
cobertura" en vez de "cubre 0".

### Primera tanda: Spring Boot 4, la librería y su autoconfiguración son artefactos distintos

| Declarado suelto | Síntoma | Lo correcto |
|---|---|---|
| `org.springframework.kafka:spring-kafka` | no hay `KafkaTemplate` | `spring-boot-starter-kafka` |
| `com.fasterxml.jackson.core:jackson-*` | no hay `ObjectMapper` | `spring-boot-starter-jackson` |
| `org.flywaydb:flyway-core` | **arranca verde y la base queda vacía** | `spring-boot-starter-flyway` |

El de Flyway es el peligroso: no falla el arranque, solo no ejecuta las migraciones.

### Jackson 3, no Jackson 2

El `ObjectMapper` que autoconfigura Boot 4 es `tools.jackson.databind.ObjectMapper`. Ambos
están en el classpath, así que el código compila y falla al arrancar.

### Jackson 3 más el cliente HTTP de la JDK

Jackson 3 lee más allá del final del JSON para verificar que no haya tokens sobrantes, y
para entonces el stream ya está cerrado. El síntoma es un `java.io.IOException: closed`
**después** de recibir la respuesta completa: parece un fallo de red y es de parseo. Se
resolvió envolviendo en `BufferingClientHttpRequestFactory`.

### El circuit breaker perdía el token

Resilience4j ejecutaba la llamada en su propio pool y el `SecurityContext` de Spring vive
en un `ThreadLocal` que no viaja: toda llamada entre servicios salía sin `Authorization` y
recibía 401. Se resolvió con
`spring.cloud.circuitbreaker.resilience4j.disable-thread-pool: true`. El timeout real ya lo
pone cada cliente HTTP por destino, así que el pool sobraba.

### Par RSA efímero

ms-identidad generaba claves nuevas en cada arranque. Reiniciarlo invalidaba todos los
tokens vivos y dejaba obsoleto el JWKS cacheado por los otros nueve. Se resolvió con claves
fijas en el `.env` (`scripts/generar-claves-jwt.sh`).

### Proxy de Spring y llamadas dentro del mismo bean

`registrarEnvio` era `protected` y se llamaba desde otro método del mismo bean: el proxy no
aplica, no había transacción, y `OutboxRegistrador` (que la exige con `MANDATORY`) habría
fallado en cada comprobante aceptado. Se separó en `RegistrarEnvioUseCase`.

### Otros

- **Dockerfiles**: copiaban solo su propio `pom.xml`; el reactor raíz declara 13 módulos.
- **Kafka KRaft**: `CLUSTER_ID` exige un UUID de 16 bytes en base64 (22 caracteres), no una
  cadena legible.
- **Gateway**: `Path=/api/ventas/**` no enruta `/api/ventas` a secas. Hay que declarar
  ambas formas.
- **Mapeos ambiguos**: dos controladores del mismo servicio declarando la misma ruta
  rompen el arranque. Pasó con `/api/clientes/{id}` y con `/api/reportes/ventas-diarias`.
- **Postgres**: un `?` suelto en `? IS NULL` no permite inferir el tipo. Necesita
  `?::uuid`.
- **Vite**: `VITE_API_URL` se inyecta en tiempo de build, no en runtime. Va como `ARG` del
  Dockerfile, no como `environment` del compose.

---

## Lo demás que ya está entregado

- **Diagrama interactivo**: `presentacion/arquitectura.html` (Archify, validado y
  verificado en navegador).
- **Presentación**: `presentacion/diapositivas.html`, 21 diapositivas con botón "Cómo
  exponer" que muestra el guion de cada una. Incluye la comparación 4 / 9 / 15+ servicios y
  el costo de migración.
- **Documentación para el cliente**: `docs/cliente/`, seis documentos sin jerga.
- **Decisiones técnicas**: `docs/DECISIONES.md`, con qué cambiaría cada una.
- **Kubernetes**: `k8s/` con Kustomize, HPA y PodDisruptionBudget. Valida con
  `kubectl kustomize k8s/base`. No se ha desplegado en un clúster real.

---

## Por dónde seguir

Antes de portar escrituras hay dos decisiones que conviene tomar, porque cambian qué se
escribe:

1. **ms-promociones y ms-credito**: se cablean o se declaran de muestra (punto 6 de lo que
   falta). Cablearlos es más barato ahora que después de agregarles formularios.
2. **La degradación silenciosa de las coberturas**: calentar, subir el presupuesto, o
   decirle al cajero que no se pudo verificar. Toca el contrato de `Copago`, así que mejor
   antes de que haya pantallas encima.

Y una que no cuesta discutir: **escribir el test de ArchUnit**. La dependencia ya está en
el pom y el proyecto lo da por hecho en cinco javadoc distintos.

**Después, lo ya decidido: portar los endpoints de escritura** para que los formularios de
cada pantalla funcionen. El orden sugerido va de mayor a menor valor demostrable:

1. **ms-catalogo**: crear y editar producto, categoría, laboratorio, presentación. Es la
   pantalla que más se usa para preparar una demo.
2. **ms-inventario**: registrar lote, bloquear, retirar, ajustar stock. Alimenta el resto.
3. **ms-clientes**: cliente, convenio, cobertura, afiliación, receta, validación de receta.
4. **ms-promociones**: crear y desactivar promoción.
5. **ms-identidad**: usuario con roles, local, caja.
6. **ms-credito**: línea de crédito.
7. **ms-facturacion**: devolución y nota de crédito. Es la que toca la saga de
   compensación, así que conviene dejarla para cuando el resto ya esté probado.
8. **ms-reportes**: regla de incentivo.

Al portar cada escritura, respetar las reglas del proyecto (ver `CLAUDE.md`):

- Toda operación que muta acepta `Idempotency-Key`.
- Lo que otros servicios deben saber sale por `outbox`, nunca con `kafkaTemplate.send()`
  desde el caso de uso.
- Las operaciones sensibles publican en `pos.auditoria`, que resuelve de paso el punto 2
  de abajo.
- Antes de reconstruir imágenes, probar el arranque con `java -jar` contra la
  infraestructura de Docker: ahorra ciclos de build de 15 minutos.

Después:

2. Publicar eventos de auditoría desde las operaciones sensibles (sale gratis si se hace
   junto con las escrituras del punto anterior).
3. Proyectar los lotes al read model para el reporte de próximos a vencer.
4. Pruebas de carga, y con eso decidir el destino de ms-promociones.
