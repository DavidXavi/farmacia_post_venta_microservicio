# 6. Preguntas frecuentes

## Sobre la decisión

**¿Por qué nueve servicios y no cuatro, que parece más simple?**

Cuatro fue la primera propuesta y era correcta para el criterio de entonces: agrupar lo que
cambia junto dentro de una misma transacción. Al fijar el objetivo de 500 locales y 200
ventas por segundo, el criterio tuvo que incluir dos cosas más: qué escala distinto y qué
falla junto.

Los tres cortes que aparecieron por eso son catálogo separado de inventario (cargas
opuestas), facturación separada de ventas (SUNAT no puede parar cajas) y reportes separado
de ventas (las consultas pesadas no pueden tocar la base transaccional).

**¿Y por qué no quince servicios, uno por cada cosa?**

Porque empeoraría el sistema. Cada servicio en el camino de una venta agrega un salto de
red, y los saltos se pagan dos veces: el tiempo de respuesta empeora más de lo que suma, y
la disponibilidad se multiplica hacia abajo. Quince servicios darían peor tiempo de
respuesta y peor disponibilidad que nueve, para la misma carga.

**¿Esto es sobre-ingeniería?**

Para una cadena de 20 locales, sí, y está dicho en el [documento de costos](05-costo.md).
Para 500 locales, no. La diferencia no es de opinión: se decide con el volumen de ventas
por segundo y con el tamaño del equipo.

**¿Por qué ya no usan RabbitMQ, si el sistema anterior lo tenía?**

Porque su único trabajo era llevar los comprobantes pendientes hacia SUNAT, y eso ya lo hace
una tabla: cada comprobante queda guardado como pendiente y se reintenta hasta que SUNAT lo
acepta. Esa tabla hace falta igual, porque hay que saber qué se emitió y qué no. Tener
además una cola era llevar la misma cuenta en dos cuadernos. Con un solo sistema de
mensajería hay uno menos que vigilar, actualizar y pagar. No se perdió ninguna función.

**¿Para qué agregaron Redis, si antes no estaba?**

Porque ahora cada servicio corre en varias copias a la vez, y hay dos datos que todas las
copias tienen que ver igual. El primero: si la caja reintenta "confirmar venta" y el
reintento cae en otra copia, esa copia tiene que saber que ya se cobró. El segundo: el
límite de peticiones por usuario, que protege al resto de cajas de una que se quedó en
bucle. Los dos son datos de corta vida que Redis borra solo cuando vencen. Si Redis se
cae, las cajas siguen vendiendo.

**¿Se reescribió el sistema desde cero?**

No. Las reglas de negocio son las mismas del sistema anterior, movidas de lugar. El FEFO,
el copago del seguro, la validación de recetas y el cálculo de la línea de crédito son el
mismo código con sus mismas pruebas. Lo que cambió es dónde vive cada cosa y cómo se hablan
entre sí.

## Sobre la operación

**¿Se puede perder una venta si algo se cae?**

No, y está probado apagando piezas a propósito: el servicio de facturación, su base de
datos y el sistema de mensajería. En todos los casos la caja siguió cobrando y cada venta
terminó con su comprobante cuando la pieza volvió. Lo que no se puede procesar se aparta
para revisión en vez de descartarse. El detalle está en
[Qué pasa cuando algo falla](03-cuando-algo-falla.md).

**¿Qué pasa si SUNAT se cae?**

Nada visible en la caja. Se cobra, se imprime y el cliente se va. El comprobante queda
pendiente y se envía solo cuando SUNAT vuelve. Hay una alerta si se acumulan demasiados o si el
más viejo lleva mucho esperando.

**¿Y si se corta el internet de un local?**

Ese local no puede vender mientras dure el corte. Los demás no se enteran. Ninguna venta
queda a medias: lo que no se confirmó no existe, y el stock apartado se libera solo.

**¿Puede el sistema vender sin conexión?**

No. Hoy requiere conexión. Venta sin conexión con sincronización posterior es un proyecto
aparte y de tamaño considerable: obliga a resolver numeración de comprobantes, control de
stock y conflictos de precios sin un árbitro central.

**¿Dos cajas pueden vender la misma última unidad?**

No. Apartar stock es una operación atómica: el motor de base de datos garantiza que solo
una de las dos cajas se la lleva. La otra recibe al instante cuántas unidades quedan, para
ofrecerle al cliente lo que sí hay.

**¿Qué pasa si el cajero hace doble clic en cobrar?**

Se cobra una sola vez. Cada operación de cobro lleva una clave única; si llega repetida, el
sistema devuelve el resultado de la primera sin volver a ejecutar nada.

**¿Se puede anular una venta ya facturada?**

Sí. Se emite una nota de crédito, el stock vuelve a los lotes exactos de los que salió, y
si era a crédito se libera el monto. Las tres cosas ocurren en tres servicios distintos y
cada uno deshace lo suyo.

## Sobre los datos

**¿Los datos están repetidos en varias bases?**

Algunos sí, a propósito. La línea de una venta guarda el nombre y el precio del producto
tal como estaban en el momento de la venta, no una referencia al catálogo. Eso no es
descuido: el precio de la venta es el de ese día aunque mañana cambie la lista, y el
comprobante tiene que poder imprimirse aunque el producto se haya dado de baja.

**¿Se puede hacer un reporte que cruce información de varios servicios?**

Sí, en el servicio de reportes, que guarda la información ya cruzada. Lo que no se puede es
cruzarla con una sola consulta contra las bases transaccionales, y eso es intencional: es
lo que evita que un reporte pesado frene a las cajas.

**¿Se puede perder una venta si un servicio se cae en el momento justo?**

No. La confirmación de la venta y el aviso a los demás servicios se guardan en la misma
operación de base de datos: entran las dos o no entra ninguna. Si un servicio está caído
cuando llega su aviso, el aviso lo espera hasta que vuelva.

**¿Cuánto tarda en actualizarse el stock después de una venta?**

Segundos. Hay un intervalo entre que la venta se confirma y el stock se descuenta de los
lotes definitivamente. Es el precio de que la caja responda en medio segundo, y está
vigilado con una alerta que avisa si ese retraso pasa de un minuto.

## Sobre la seguridad

**¿Cómo se protege el acceso?**

Cada usuario recibe al entrar una credencial firmada que dura 15 minutos y se renueva
sola. Las cuentas con permisos sensibles, como cambiar precios o anular ventas, pueden
exigir además un código de seis dígitos del celular.

**¿Un servicio puede hacer algo que no le corresponde?**

No por descuido: cada servicio verifica los permisos por su cuenta y no confía en que la
puerta de entrada ya lo haya hecho. Y ninguno puede leer la base de datos de otro, porque
el motor no le da permiso.

**¿Dónde están las contraseñas y las claves?**

Nunca en el código. En desarrollo van en un archivo local que no se versiona; en
producción, en el gestor de secretos de la plataforma.

## Sobre el futuro

**¿Qué falta para poner esto en producción?**

Tres cosas: correr las pruebas de carga contra los 200 ventas por segundo proyectados,
conectar la integración real con SUNAT (hoy hay un simulador que imita su lentitud y sus
fallas), y medir el servicio de promociones para decidir si se queda separado o vuelve a
integrarse.

**¿Se puede agregar venta en línea o una app?**

Sí. Se conectan al mismo punto de entrada y reutilizan los mismos servicios. No hay que
duplicar reglas de negocio. Si las necesidades de la app fueran muy distintas a las de la
caja, ahí correspondería evaluar una capa intermedia propia para ese canal.

**¿Se puede volver atrás?**

Durante la migración, sí: cada uno de los doce pasos deja el sistema funcionando y se puede
revertir solo ese paso. Una vez completada, volver al sistema único sería un proyecto de
tamaño comparable al de ir hacia adelante.
