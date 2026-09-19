-- Semilla de datos de prueba para ms-promociones.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO promociones (id, nombre, descripcion, tipo_beneficio, valor_beneficio, requiere_cliente, cantidad_minima, vigencia_inicio, vigencia_fin, activa) VALUES
    ('4be4d8ef-c0ad-53d4-9c16-c9d3ec60be96', 'Descuento 10% Paracetamol', '10% de descuento en Paracetamol 500mg', 'DESCUENTO_PORCENTAJE', 10.0, false, 1, '2026-06-28', '2026-09-26', true),
    ('e630542d-e22d-5a4b-a714-36a39cbd8f25', 'Descuento S/5 Ibuprofeno', 'S/5 de descuento fijo en Ibuprofeno 400mg', 'DESCUENTO_MONTO', 5.0, false, 1, '2026-06-28', '2026-09-26', true),
    ('6d9c5d64-ba0d-56cf-8f8c-26288338db67', 'Lleva 3 paga 2 Vitamina C', 'En la compra de 3 unidades, la de menor valor es gratis', 'LLEVA_N_PAGA_M', 1.0, false, 3, '2026-07-13', '2026-09-11', true),
    ('f487a9ba-8aa1-503d-9f09-ea3607141901', 'Fidelidad clientes 15% Loratadina', '15% de descuento para clientes registrados', 'DESCUENTO_PORCENTAJE', 15.0, true, 1, '2026-06-28', '2026-10-26', true),
    ('5f7024d4-5753-5de9-8ef6-65302b69aef3', 'Promo verano vencida', 'Descuento de temporada ya vencido', 'DESCUENTO_PORCENTAJE', 20.0, false, 1, '2026-03-30', '2026-06-28', false),
    ('f3d85a94-89d4-5596-9111-6e7a13ce20f2', 'Promo Dia de la Madre', 'Descuento programado para una fecha futura', 'DESCUENTO_MONTO', 3.0, false, 1, '2026-08-27', '2026-09-11', true),
    ('5620f463-3e18-567a-8762-a23dbb1c6e3e', 'Descuento Omeprazol 8%', '8% de descuento por 2 o mas unidades', 'DESCUENTO_PORCENTAJE', 8.0, false, 2, '2026-07-18', '2026-10-16', true),
    ('b8ee45d8-8295-5b08-a7c5-c021dd46f243', '2x1 Cetirizina', 'Lleva 2 y paga 1', 'LLEVA_N_PAGA_M', 1.0, false, 2, '2026-07-08', '2026-10-06', true),
    ('90642f3a-01b9-55ea-b689-22d1b0bba311', 'Descuento Losartan adultos mayores', '12% de descuento para clientes registrados', 'DESCUENTO_PORCENTAJE', 12.0, true, 1, '2026-06-28', '2026-10-26', true),
    ('cd0fc2ab-5c96-5cf0-826e-ea254df4fbab', 'Promo desactivada manualmente', 'Se desactivo antes de que expirara su vigencia', 'DESCUENTO_MONTO', 2.0, false, 1, '2026-07-18', '2026-09-16', false);

INSERT INTO promocion_condiciones (id, promocion_id, producto_id) VALUES
    ('a915f48c-35e0-51ab-9314-29d1d4951cb3', '4be4d8ef-c0ad-53d4-9c16-c9d3ec60be96', 'a81f4913-8184-5751-9322-c2f7a1807445'),
    ('ff41f8bf-db00-5e62-b665-e41cad52ae58', 'e630542d-e22d-5a4b-a714-36a39cbd8f25', '23ceed9c-b0de-5ac7-8e1c-c72633d47266'),
    ('fe4afde2-ea97-5fb7-9002-1288fd752ec1', '6d9c5d64-ba0d-56cf-8f8c-26288338db67', 'b5018120-11e1-59d1-839a-d50f4122ce09'),
    ('e7969db5-63a1-5ec0-ad7a-19d55f7328b9', 'f487a9ba-8aa1-503d-9f09-ea3607141901', 'a203229b-3f3d-51b7-8f7b-beb729aefab0'),
    ('572ad0e7-b6f3-55ae-ae6e-525792a99111', '5f7024d4-5753-5de9-8ef6-65302b69aef3', 'a81f4913-8184-5751-9322-c2f7a1807445'),
    ('9a8dc8df-3240-52f8-8d65-435762e0034f', 'f3d85a94-89d4-5596-9111-6e7a13ce20f2', 'b5018120-11e1-59d1-839a-d50f4122ce09'),
    ('81c4a283-08d7-58c4-81c9-49d670d8f5e8', '5620f463-3e18-567a-8762-a23dbb1c6e3e', 'a1b00829-c7df-5dae-89b5-b66712d99d49'),
    ('d7236ebe-778e-5996-9be0-032329d0ca84', 'b8ee45d8-8295-5b08-a7c5-c021dd46f243', '586a5aa6-3933-5159-987a-8a64762d74d7'),
    ('d3c4957f-bbcc-58b6-9cb1-6fb9d80c306f', '90642f3a-01b9-55ea-b689-22d1b0bba311', '5d2fe11e-e6b8-5c32-970d-e8930d556d18'),
    ('afa2ea9b-c14d-5071-a41c-b1732336dfc6', 'cd0fc2ab-5c96-5cf0-826e-ea254df4fbab', '23ceed9c-b0de-5ac7-8e1c-c72633d47266');
