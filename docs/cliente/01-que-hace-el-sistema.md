# 1. Qué hace el sistema

## En una frase

Es el sistema con el que una cadena de boticas cobra: atiende al cliente en caja, controla
qué hay en cada anaquel, aplica el seguro y las promociones que correspondan, cobra en
efectivo o a crédito, y emite la boleta o factura ante SUNAT.

## Quién lo usa

| Rol | Qué hace en el sistema |
|---|---|
| Cajero | Abre su caja, escanea productos, identifica al cliente, cobra, imprime |
| Químico farmacéutico | Valida recetas de medicamentos controlados |
| Encargado de inventario | Recibe lotes, ajusta stock, da de baja vencidos |
| Administrador | Usuarios, precios, promociones, convenios de seguro |
| Gerencia | Reportes de venta, incentivos por vendedor, tablero por local |

## Qué pasa en una venta, paso a paso

Así lo vive el cajero:

1. **Abre la venta.** Un clic. El sistema no consulta nada, solo crea la venta vacía.

2. **Escanea un producto.** En menos de dos décimas de segundo el sistema:
   - busca el precio,
   - aparta las unidades en el stock de ese local, para que otra caja no las venda,
   - revisa si hay alguna promoción que aplique.

   Si el producto necesita receta y no se adjuntó, el sistema no deja seguir.

3. **Identifica al cliente** (opcional). Con el DNI trae sus datos, su convenio de seguro
   y su línea de crédito si tiene.

4. **Registra el pago.** Efectivo, tarjeta, transferencia, billetera, copago de seguro o
   crédito de la botica. Se pueden combinar.

5. **Confirma.** Acá termina la espera del cliente. El sistema responde en menos de medio
   segundo y el cliente se va con su producto.

6. **Después, sin que nadie espere**, el sistema descuenta los lotes correctos, aplica el
   cargo al crédito, emite el comprobante ante SUNAT y actualiza los reportes.

Ese paso 6 es la clave del diseño: lo que puede tardar, tarda cuando el cliente ya se fue.

## Las reglas de farmacia que el sistema respeta

Estas no son opcionales, son del rubro:

**FEFO: sale primero el que vence antes.** Si hay dos lotes del mismo producto, el sistema
despacha el que vence primero. Despachar el otro garantiza que el próximo se venza en el
anaquel, y un medicamento vencido en stock es una observación de DIGEMID.

**Trazabilidad por lote.** El sistema guarda de qué lote salió cada unidad vendida. Si
mañana hay una alerta sanitaria sobre un lote, se puede decir exactamente a quién se le
vendió.

**Receta obligatoria para controlados.** Los productos marcados como controlados no se
venden sin registrar la receta, con su número, su profesional y su cantidad autorizada.
Cada receta tiene un tope de usos.

**Una sola promoción por línea.** Si a un producto le aplican dos promociones, se usa la
que más le conviene al cliente, pero solo una. Acumularlas es como una botica termina
vendiendo por debajo del costo sin que nadie lo note hasta el cierre de mes.

**Comprobante electrónico sin huecos.** La numeración de boletas y facturas es correlativa
y sin repetidos. El sistema garantiza esto incluso cuando hay varias cajas emitiendo al
mismo tiempo en el mismo local.

## Cómo entra el usuario

Tres formas, todas con el mismo resultado:

- Usuario y contraseña.
- Cuenta de Google.
- Cuenta de Facebook.

Y además, si la cuenta lo tiene activado, un segundo factor: el código de seis dígitos que
genera Google Authenticator en el celular. Eso protege las cuentas de administrador, que
son las que pueden cambiar precios y anular ventas.

## Lo que el sistema no hace

Por claridad, y para que no haya sorpresas en la reunión de alcance:

- No hace contabilidad. Emite comprobantes; los libros son otro sistema.
- No hace compras ni reposición automática a proveedores.
- No maneja planillas ni asistencia del personal.
- No tiene app móvil para el cliente final.
