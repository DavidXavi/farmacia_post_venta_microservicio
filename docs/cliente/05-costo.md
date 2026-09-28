# 5. Cuánto cuesta

Este documento pone los números sobre la mesa, incluidos los incómodos. Un cambio de
arquitectura que solo muestra los beneficios no es una propuesta, es una venta.

## Resumen para quien decide

| Concepto | Valor |
|---|---|
| Esfuerzo de desarrollo | 29 semanas-persona, unos 7 meses-persona |
| Equipo mínimo | 4 personas: 2 desarrollo, 1 plataforma, 1 pruebas |
| Calendario realista | 5 a 7 meses |
| Infraestructura mensual, antes | S/ 1,500 a 2,600 |
| Infraestructura mensual, después | S/ 6,700 a 11,100 |
| Multiplicador de infraestructura | entre 3 y 4 veces |
| Costo operativo permanente | un rol de plataforma a medio tiempo que antes no existía |

Las cifras en soles son orden de magnitud para nube gestionada y hay que validarlas con el
proveedor. El multiplicador (entre 3 y 4 veces) es más confiable que los montos absolutos.

## Esfuerzo de desarrollo, paso por paso

La migración se hace en doce pasos, y en cada uno el sistema queda funcionando.

| Paso | Qué incluye | Semanas-persona |
|---|---|---|
| 1 y 2 | Copiar el proyecto y montar la infraestructura nueva | 3 |
| 3 | Puerta de entrada única delante del sistema actual | 1 |
| 4 | Separar identidad: login, Google, Facebook, segundo factor | 2 |
| 5 | Separar catálogo, con su memoria caché | 2 |
| 6 | **Separar inventario: control de stock, reservas, FEFO, coordinación** | **5** |
| 7 | Separar promociones y clientes | 3 |
| 8 | Separar crédito, con su libro de movimientos | 2 |
| 9 | Separar facturación, con reintentos contra SUNAT | 3 |
| 10 | Separar reportes y reconstruir los tableros | 3 |
| 11 | Lo que queda es ventas: el sistema viejo desaparece | 2 |
| 12 | Pruebas de carga y ajuste fino | 3 |
| | **Total** | **29** |

El paso 6 se lleva casi una quinta parte del esfuerzo él solo. Es donde se resuelve que
varias cajas no vendan el mismo producto a la vez, y donde se arma la coordinación entre
servicios. También es el único paso verdaderamente riesgoso.

Veintinueve semanas-persona no son veintinueve semanas de calendario. Con cuatro personas
y considerando que los pasos 4 al 11 no se pueden hacer todos en paralelo, el calendario
realista es de cinco a siete meses.

## Infraestructura

| | Sistema actual | Nueve microservicios |
|---|---|---|
| Servidores de aplicación | 2 instancias | Clúster de 6 a 8 nodos, unos 30 procesos |
| Bases de datos | 1 PostgreSQL | 9 PostgreSQL más 10 copias de lectura |
| Mensajería | 1 Kafka, 1 RabbitMQ | 3 Kafka y registro de esquemas |
| Memoria compartida | no hay | Redis en 3 nodos |
| Monitoreo | registros del contenedor | Prometheus, Grafana, trazas y almacenamiento |
| **Costo mensual** | **S/ 1,500 a 2,600** | **S/ 6,700 a 11,100** |

Se puede reducir bastante corriendo varias bases en menos instancias, a costa de perder
aislamiento. Esa decisión conviene tomarla con la factura real del proveedor en la mano.

## El costo que no termina cuando termina el proyecto

Este es el que más pesa a largo plazo y el que menos aparece en las propuestas.

**Guardia.** Antes había un sistema que podía fallar. Ahora hay diez que pueden fallar por
separado. Quien está de turno necesita entender cómo se coordinan entre sí, no solo leer
un registro de errores.

**Despliegues.** Diez líneas de despliegue en vez de una. Los cambios de base de datos
tienen que hacerse en dos etapas, porque durante la actualización conviven la versión
vieja y la nueva.

**Un rol de plataforma.** Medio tiempo dedicado que antes no existía. El monitoreo deja de
ser un lujo y pasa a ser infraestructura crítica: sin él, una falla en la coordinación
entre servicios es invisible.

**Curva de aprendizaje.** El equipo necesita entender coordinación entre servicios,
publicación confiable de eventos, reintentos seguros y Kubernetes. Entre cuatro y seis
semanas por persona, solapadas con el desarrollo.

## Lo que no cuesta

**Las reglas de negocio no se reescriben.** El FEFO, el copago del seguro, la línea de
crédito y la validación de recetas son el mismo código, movido de lugar. Sus pruebas
siguen valiendo. Esto es lo que hace que el proyecto sea una reorganización y no un
sistema nuevo.

**El frontend casi no se toca.** Solo cambia la dirección a la que apunta.

**Las tablas se reparten, no se rediseñan.** Las 35 tablas del sistema actual ya estaban
organizadas por contexto y ya evitaban referencias cruzadas. El corte es mecánico.

**El riesgo está concentrado.** De los doce pasos, uno solo es peligroso. Se mitiga
corriendo el servicio nuevo en paralelo sin que nadie use su resultado, hasta comprobar que
coincide con el sistema viejo.

## Cuándo esto no vale la pena

Con 20 locales y 40 cajas, esta arquitectura es una pérdida neta.

Un sistema único con copias de lectura y memoria caché aguanta ese volumen de sobra, con
una décima parte del costo operativo. Pagar nueve despliegues, nueve bases y una guardia
especializada para atender 5 ventas por segundo no es arquitectura, es sobrecosto.

El punto de quiebre, o sea cuándo sí conviene:

- Cuando el pico supera lo que una sola base de datos bien afinada sostiene junto con los
  reportes, aproximadamente entre 50 y 80 ventas por segundo.
- Cuando el equipo pasa de unas 8 personas y los despliegues empiezan a bloquearse entre sí.
- Cuando una integración externa lenta, como SUNAT, empieza a costar ventas de verdad.
- Cuando un reporte gerencial ya afecta de forma medible la velocidad de las cajas.

Si la cadena no está en ninguno de esos cuatro escenarios, la recomendación honesta es
quedarse con el sistema actual y volver a esta conversación cuando lo esté.
