# El sistema, explicado

Esta carpeta explica cómo funciona el sistema POS de farmacia y por qué está construido
así. Está escrita para que la entienda quien toma la decisión de comprarlo o mantenerlo,
no solo quien lo programa.

No hace falta saber programar para leerla. Donde aparece un término técnico, está
explicado en el momento.

## Por dónde empezar

| Documento | De qué trata | Para quién |
|---|---|---|
| [1. Qué hace el sistema](01-que-hace-el-sistema.md) | Qué resuelve, quién lo usa, qué pasa en una venta | Todos |
| [2. Cómo funciona por dentro](02-como-funciona-la-arquitectura.md) | Los nueve servicios y por qué son nueve | Gerencia, jefatura de sistemas |
| [3. Qué pasa cuando algo falla](03-cuando-algo-falla.md) | Caídas, SUNAT, internet, la caja colgada | Todos |
| [4. Cuánto aguanta](04-cuanto-aguanta.md) | Capacidad, crecimiento, qué pasa si duplicamos locales | Gerencia |
| [5. Cuánto cuesta](05-costo.md) | Migración, infraestructura y operación | Gerencia, finanzas |
| [6. Preguntas frecuentes](06-preguntas-frecuentes.md) | Las dudas que salen en toda reunión | Todos |

## Lo esencial en cinco líneas

1. Es un sistema de punto de venta para una cadena de boticas: cobra, controla stock por
   lote y vencimiento, aplica seguros y promociones, y emite comprobantes ante SUNAT.
2. Por dentro son nueve programas independientes, cada uno con su propia base de datos.
3. Si uno se cae, los demás siguen funcionando. Solo dos de ellos pueden impedir una venta.
4. SUNAT caída no para las cajas: el comprobante queda en cola y se envía cuando vuelva.
5. Está dimensionado para 500 locales y 1500 cajas. Para una cadena de 20 locales sería
   un gasto innecesario, y eso está dicho con todas sus letras en el documento 5.
