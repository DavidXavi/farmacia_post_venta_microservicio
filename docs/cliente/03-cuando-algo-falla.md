# 3. Qué pasa cuando algo falla

Todo sistema falla. La diferencia entre uno bien hecho y uno mal hecho es qué tan lejos
llega esa falla.

La regla que ordena todo el diseño es una sola:

> **Nada fuera del control de stock y del cobro puede impedir una venta.**

## Escenarios reales

### SUNAT se cae

**Qué pasa:** nada visible en la caja. La venta se cobra, el cliente se lleva su ticket
impreso y su producto.

**Por dentro:** el comprobante electrónico queda guardado como pendiente. El sistema lo
reintenta solo cada cinco segundos. Cuando SUNAT vuelve, se envían todos los pendientes.

**Cuándo se entera alguien:** hay una alerta que avisa si se acumulan más de 500
comprobantes o si el más viejo lleva más de media hora esperando. No es una emergencia
operativa, es una obligación tributaria pendiente.

### Se cae el servicio de promociones

**Qué pasa:** las ventas siguen, pero sin aplicar descuentos.

**Por qué se decidió así:** un sistema que se niega a cobrar porque no pudo calcular un
descuento no es resiliente, le hizo perder la venta a la botica. El cliente igual se lleva
su producto.

**Cuándo se entera alguien:** al instante. La degradación se registra como métrica y el
tablero la muestra por local. La degradación silenciosa sería peor que la caída ruidosa:
si el servicio lleva dos horas sin aplicar descuentos, alguien tiene que enterarse antes
que el cliente.

### Se cae el servicio de clientes

**Qué pasa:** no se puede identificar al cliente ni aplicar su seguro. La venta continúa
como anónima.

### Se cae el inventario

**Qué pasa:** no se puede vender.

**Por qué no hay degradación posible:** vender sin saber si hay stock es prometerle al
cliente algo que puede no estar en el anaquel. Eso cuesta más que perder la venta. Es uno
de los dos únicos servicios sin plan B.

Por eso corre en cuatro copias, con su base de datos replicada, y es el que más atención
recibe en monitoreo.

### Se cae el servicio de login

**Qué pasa:** quien ya está trabajando sigue trabajando. Quien intenta entrar de nuevo, no
puede.

**Por qué:** cuando un cajero inicia sesión recibe una credencial firmada. Los demás
servicios verifican esa firma por su cuenta, sin preguntarle a nadie. Si tuvieran que
consultar al servicio de login en cada operación, ese servicio se volvería el punto único
de falla de todo el sistema.

### Una caja se cuelga a mitad de venta

**Qué pasa:** el stock que esa venta había apartado vuelve a estar disponible solo, a los
quince minutos.

**Por qué importa:** sin este mecanismo, el producto apartado por una venta que nunca se
terminó quedaría bloqueado para siempre, y aparecería agotado teniéndolo en el anaquel.

### Se corta el internet del local

**Qué pasa:** ese local no puede vender mientras dure el corte. Los demás locales no se
enteran.

**Lo que no pasa:** ninguna venta a medias queda en un estado raro. Lo que no se confirmó,
no existe, y el stock apartado se libera solo.

### Una venta se anula

**Qué pasa:** tres cosas se deshacen en tres servicios distintos. El inventario devuelve
las unidades a los lotes exactos de los que salieron. El crédito libera el monto. Si el
comprobante ya salió a SUNAT, se emite una nota de crédito.

Cada uno deshace lo suyo cuando recibe el aviso. Si uno falla, reintenta, y si agota los
reintentos, suena una alerta y queda un registro para revisar a mano.

### El pago falla después de haber apartado el stock

**Qué pasa:** el stock apartado vuelve a estar disponible. La venta queda sin confirmar.

## Comprobado, no supuesto

El 28 de setiembre se apagaron piezas a propósito mientras se vendía, y se contó al final
si alguna venta se había quedado sin su comprobante:

| Qué se apagó | Qué pasó con las ventas |
|---|---|
| El servicio de facturación | Las cajas siguieron cobrando; al volver, salieron los comprobantes pendientes |
| La base de datos de facturación, unos segundos | El sistema esperó y reintentó; el comprobante salió cuando la base volvió |
| Un mensaje dañado entre servicios | Se apartó para revisión y las demás ventas siguieron normales |
| El sistema de mensajería completo | Las cajas siguieron cobrando; los avisos esperaron guardados y salieron al volver |

Al final: seis ventas, seis comprobantes, seis en los reportes. Ninguna perdida. La prueba
se puede repetir cuando se quiera.

## Lo que sí queda pendiente y hay que decir

Hay una ventana de unos segundos entre que la venta se confirma y el stock se descuenta de
los lotes definitivamente. Durante esos segundos, un reporte de inventario mostraría las
unidades como si todavía estuvieran.

No es un descuido, es el precio de que la caja responda en medio segundo en vez de en tres.
Se vigila con una alerta: si ese retraso pasa de un minuto, alguien se entera.

## Cómo se sabe que algo va mal

El sistema no espera a que llame un usuario. Estas alertas despiertan a alguien:

| Alerta | Qué significa |
|---|---|
| Confirmar venta tarda más de medio segundo | Las cajas están lentas |
| Evento sin procesar hace más de un minuto | Ventas confirmadas que no llegan a inventario |
| Más del 1% de las ventas se compensa | Algo falla de forma sistemática, no es ruido |
| Mensajes en la cola muerta | Algo agotó sus reintentos y necesita revisión manual |
| Comprobantes acumulados | SUNAT caída o rechazando |
| Un servicio dejó de responder | Circuito abierto hacia ese destino |

Cada alerta tiene escrito qué hacer cuando suena. Una alerta que nadie sabe cómo atender
se ignora a las dos semanas, y entrena al equipo a ignorar las demás.
