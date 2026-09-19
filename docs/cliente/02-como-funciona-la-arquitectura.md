# 2. Cómo funciona por dentro

## La idea de fondo

En vez de un solo programa grande que hace todo, el sistema son nueve programas
independientes que se hablan entre sí. Cada uno tiene su propia base de datos y se puede
actualizar, reiniciar o escalar sin tocar a los demás.

La comparación más simple: en vez de una sola botica gigante donde una persona atiende,
despacha, cobra y factura, son nueve mostradores especializados. Cuando el de facturación
tiene cola, el de cobro sigue atendiendo.

## Los nueve

| Servicio | De qué se encarga | Qué pasa si se cae |
|---|---|---|
| **identidad** | Usuarios, roles, locales, cajas, login y segundo factor | No se puede iniciar sesión nueva. Las cajas ya abiertas siguen vendiendo |
| **catálogo** | Productos, categorías, laboratorios, precios | Se usa la información guardada en memoria, aunque esté algo vieja |
| **inventario** | Lotes, stock, vencimientos, FEFO | **No se puede vender**: sin saber si hay stock no se puede prometer un producto |
| **clientes** | Clientes, seguros, convenios, recetas | La venta continúa como anónima, sin seguro |
| **crédito** | Línea de crédito de cada cliente y sus movimientos | No se puede pagar a crédito. El cajero ofrece otra forma de pago |
| **promociones** | Las reglas de descuento y su evaluación | La venta entra sin descuento |
| **ventas** | La venta en sí, los pagos, y coordinar a los demás | **No se puede vender** |
| **facturación** | Boletas, facturas, notas de crédito y el envío a SUNAT | El comprobante queda en cola. La caja sigue cobrando |
| **reportes** | Los tableros de gerencia y los incentivos por vendedor | No hay reportes. Cero impacto en las cajas |

Delante de los nueve hay una puerta de entrada única, el *gateway*, que revisa que quien
entra tenga permiso y reparte cada pedido al servicio que corresponde.

Hay un diagrama interactivo en `presentacion/arquitectura.html`. Se abre con doble clic,
no necesita internet ni instalar nada.

## Por qué nueve y no uno solo

Tres razones concretas, con el problema que resuelven:

### Porque las cargas son distintas

Consultar un precio y descontar stock parecen operaciones parecidas, pero no lo son.
Consultar precios pasa miles de veces por segundo sobre datos que cambian una vez a la
semana. Descontar stock pasa menos veces pero todas sobre la misma fila del mismo
producto, peleando entre cajas.

Juntos en un solo programa, el segundo le come los recursos al primero justo en hora pico.
Separados, cada uno se dimensiona por su cuenta: catálogo corre en seis copias, crédito en
dos.

### Porque las caídas no deben contagiarse

SUNAT se cae seguido. Es un hecho del negocio en Perú, no un defecto del sistema. Si la
emisión del comprobante viviera dentro del programa que cobra, cada caída de SUNAT sería
una caída de todas las cajas de la cadena.

Al estar aparte, una SUNAT caída significa comprobantes acumulados en una cola, que salen
solos cuando el servicio vuelve. El cliente ya se fue con su producto y su ticket impreso.

### Porque los reportes no deben molestar a las cajas

Un reporte que recorre tres meses de ventas es una consulta pesada. Corriendo contra la
misma base de datos que usan las cajas, las hace más lentas, y justo a fin de mes, que es
cuando más se vende y cuando más reportes se piden.

El servicio de reportes tiene su propia base, con la información ya preparada. Sus
consultas no tocan la base de las ventas.

## Por qué cada uno tiene su propia base de datos

Es la parte que más extraña a quien viene de sistemas tradicionales, y la más importante.

Si dos servicios compartieran tablas, cambiar una columna obligaría a coordinar dos
despliegues, y un reporte mal hecho en uno frenaría al otro. No serían nueve servicios,
sería un solo sistema repartido en nueve procesos, con todos los costos y ninguna ventaja.

La separación no depende de la buena voluntad del equipo: el motor de base de datos
simplemente no le da permiso a un servicio para conectarse a la base de otro.

**Lo que se gana:** nueve bases pueden atender nueve veces más conexiones, tienen nueve
discos distintos y se pueden ajustar por separado.

**Lo que se pierde:** ya no se puede cruzar información de dos contextos con una sola
consulta. Un reporte de "ventas del día con el nombre del vendedor" antes era una consulta;
ahora el dato del vendedor ya viene guardado dentro del reporte. Se recupera en el servicio
de reportes, y de hecho termina siendo más rápido.

## Cómo se hablan entre ellos

De dos maneras, según si hay que esperar la respuesta o no.

**Cuando hay que esperar** (el cajero está mirando la pantalla), el servicio de ventas
llama directamente al que necesita y espera. Pero con un límite de tiempo estricto para
cada uno:

| A quién llama | Espera máximo | Si no responde |
|---|---|---|
| inventario | medio segundo | La línea falla, el cajero lo ve |
| crédito | ocho décimas | No se puede pagar a crédito |
| catálogo | dos décimas | Usa la información guardada en memoria |
| promociones | tres décimas | La venta sigue sin descuento |
| clientes | tres décimas | La venta sigue como anónima |

**Cuando no hay que esperar**, el servicio publica un aviso ("se confirmó esta venta") y
sigue con lo suyo. Los cuatro servicios interesados lo recogen a su ritmo. Si uno está
caído, el aviso lo espera hasta que vuelva.

## Lo que esto significa en la práctica

- Actualizar el sistema de reportes no obliga a parar las cajas.
- Un pico de ventas en campaña se absorbe agregando copias solo del servicio que lo
  necesita.
- Una falla en un servicio secundario degrada la experiencia, no la corta.
- Hay un intervalo de unos segundos entre que la venta se confirma y el stock se descuenta
  definitivamente. Es el costo real de este diseño y está medido y vigilado.
