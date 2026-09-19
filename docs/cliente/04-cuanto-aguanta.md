# 4. Cuánto aguanta

## Para qué tamaño está diseñado

| Dimensión | Objetivo |
|---|---|
| Locales | 500 |
| Cajas por local | 3, o sea 1500 terminales |
| Ventas en el pico | 200 por segundo |
| Consultas de catálogo | 5000 por segundo |
| Respuesta al escanear un producto | menos de 2 décimas de segundo |
| Respuesta al confirmar la venta | menos de medio segundo |
| Disponibilidad del cobro | 99.9%, equivalente a 43 minutos de caída al mes |

Estos números no son decorativos: cada decisión técnica del sistema se justifica contra
ellos. Si la cadena fuera de otro tamaño, el diseño correcto sería otro, y eso está dicho
sin rodeos en el [documento de costos](05-costo.md).

## Qué pasa si la cadena crece

**Se duplican los locales, de 500 a 1000.** Se agregan copias de los servicios que lo
necesiten. La operación es de minutos y no requiere tocar código. El diseño reparte la
carga por local, así que duplicar locales reparte la carga entre más filas de la base de
datos en vez de concentrarla.

**Se duplican las ventas por local.** Igual: más copias de ventas e inventario. El límite
aquí es la contención sobre la fila de stock de cada producto en cada local, que es alto
porque dos locales distintos nunca se pelean la misma fila.

**Se triplican los reportes.** No afecta a las cajas en absoluto: los reportes viven en su
propia base. Si las consultas se vuelven muy pesadas, se le agregan copias de lectura solo
a ese servicio.

**Se agrega un canal nuevo, por ejemplo venta en línea.** Se conecta al mismo gateway y
usa los mismos servicios. No hay que duplicar reglas de negocio.

## Qué aguanta menos de lo que parece

Vale la pena decir dónde están los límites reales:

**La emisión a SUNAT.** No depende del sistema sino de SUNAT. Si SUNAT acepta 10 envíos
por segundo, ese es el techo, y agregar copias del servicio de facturación no lo mueve. Lo
que sí está resuelto es que esa lentitud no llegue a la caja.

**El servicio de promociones en el camino caliente.** Es el corte más discutible del
diseño. Se evalúa una vez por cada producto escaneado, que es la operación más frecuente
del sistema. Está pendiente medirlo en las pruebas de carga: si el tiempo de respuesta no
baja de 20 milisegundos, conviene volver a meterlo dentro del servicio de ventas.

**Las consultas analíticas muy grandes.** El servicio de reportes usa PostgreSQL con los
datos ya preparados. Aguanta con holgura los reportes actuales. Si en el futuro se quiere
analítica sobre años de historia, ahí sí correspondería un motor especializado. No se puso
ahora porque sería un sistema más que operar sin necesidad demostrada.

## Lo que más capacidad agrega, por orden de importancia

Esto es útil saberlo al priorizar inversión, porque no es intuitivo:

1. **La memoria caché.** Multiplica por entre 10 y 100 la capacidad de consulta. Es el
   único cambio que mueve el orden de magnitud. No tiene nada que ver con cuántos
   servicios haya.
2. **El servicio de reportes separado.** Multiplica por entre 5 y 20 la capacidad de
   consulta analítica, y además libera a las cajas.
3. **Las copias de lectura de la base de datos.** Crecimiento proporcional.
4. **Pedir las cosas en lote.** Una venta de cinco productos es una sola consulta al
   catálogo, no cinco. Divide el tráfico interno entre el número de líneas.
5. **Más copias del mismo servicio.** Crecimiento proporcional, hasta que la base de datos
   sea el límite.
6. **Partir un servicio más.** Solo ayuda si ese servicio tenía un cuello de botella
   propio. Si no, solo agrega un salto de red más y empeora el tiempo de respuesta.

## Una advertencia sobre agregar servicios

Cada servicio nuevo en el camino de una venta se paga dos veces:

- **El tiempo de respuesta empeora más de lo que parece.** Con cinco servicios encadenados,
  lo que antes era el peor 1% de las operaciones pasa a ser el peor 5%. El usuario percibe
  el sistema cinco veces más lento aunque cada servicio esté igual de rápido.
- **La disponibilidad se multiplica hacia abajo.** Cinco servicios con 99.9% cada uno dan
  99.5% en cadena, o sea de 43 minutos de caída al mes se pasa a 3.6 horas.

Por eso el camino de una venta tiene tres saltos y no ocho, y por eso nueve servicios es el
número que la carga justifica, ni uno más.

## Lo que falta medir

Este documento habla de un diseño dimensionado, no de un sistema ya medido en producción.
Falta correr las pruebas de carga contra los 200 ventas por segundo proyectados y ajustar
el número de copias y los tamaños de pool con datos reales.

Hasta que eso ocurra, los números de esta página son objetivos de diseño, no mediciones.
