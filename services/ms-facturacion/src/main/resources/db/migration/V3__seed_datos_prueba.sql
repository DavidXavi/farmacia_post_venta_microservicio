-- Semilla de datos de prueba para ms-facturacion.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO comprobantes (id, venta_id, local_id, tipo, serie, correlativo, monto_total, fecha_emision) VALUES
    ('8964f061-af25-598d-88b5-08b254cf3e28', '8f82d1fc-945f-5869-80a1-4dc2eb4d8b83', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'BOLETA', 'B001', 1, '29.50', '2026-07-18 11:03:00+00'),
    ('99f6a4e0-3b33-5692-9cfb-76fac6689a4f', '80630ced-8f45-5664-afee-bfd87178411b', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'BOLETA', 'B001', 2, '34.22', '2026-07-18 11:03:00+00'),
    ('ec8e0c0a-d6c6-5561-953d-d318af54df38', '79ac7823-44f6-5db4-be19-b55cdb436694', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'BOLETA', 'B001', 3, '30.56', '2026-07-20 11:03:00+00'),
    ('20205102-ecc0-50d8-bb74-bfa09c67bb52', 'c2726692-927a-5bdd-8f56-4ee70ef2ccdb', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'BOLETA', 'B001', 4, '29.50', '2026-07-20 11:03:00+00'),
    ('3e069e6d-5ab9-5e02-b646-baed2a58f476', 'c615ef40-7276-5f66-94cb-79d307b4890a', '3314d935-7880-5e41-8bd6-ddd0985e003c', 'BOLETA', 'B001', 5, '11.68', '2026-07-22 11:03:00+00'),
    ('6d88a45d-1c0e-50b9-91e4-1597db7e8acb', '320ee597-b801-5d10-8059-30b7ed403118', '3314d935-7880-5e41-8bd6-ddd0985e003c', 'BOLETA', 'B001', 6, '25.96', '2026-07-22 11:03:00+00'),
    ('0929b10d-e816-5a7b-a7e9-f4e9eace17c9', 'b9a5a97e-ecdb-5cc9-9dee-25e535d80b31', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', 'FACTURA', 'F001', 1, '46.96', '2026-07-24 11:03:00+00'),
    ('6e1f6c79-f633-577c-b2ac-f16b48eabd5a', 'd4b72821-e19f-5591-af34-9f0e01cec4ec', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', 'BOLETA', 'B001', 7, '13.22', '2026-07-24 11:03:00+00'),
    ('cfb33adf-aaf6-56c8-ac50-5d72c88b4837', '0064b946-33e2-52eb-955d-9fe64ed1ff78', 'eef251fe-7356-56c5-81a9-96ec687e793e', 'BOLETA', 'B001', 8, '17.05', '2026-07-26 11:03:00+00'),
    ('9bb4222a-b6a3-55bc-b9ee-ca7bdf0a3e7c', '155d3d62-cc80-5868-a490-2ad665b084dc', 'eef251fe-7356-56c5-81a9-96ec687e793e', 'BOLETA', 'B001', 9, '12.39', '2026-07-26 11:03:00+00');

INSERT INTO devoluciones (id, venta_id, local_id, usuario_id, motivo, fecha) VALUES
    ('94199053-63a5-5633-9d9c-7c2038e92964', '8f82d1fc-945f-5869-80a1-4dc2eb4d8b83', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', 'Cliente reporto reaccion alergica leve', '2026-07-19 15:00:00+00'),
    ('cbc21614-b7da-5431-912f-176af9238985', '80630ced-8f45-5664-afee-bfd87178411b', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', 'Producto con empaque danado', '2026-07-19 15:00:00+00'),
    ('dd73c0b8-ae80-5328-b952-32f2f10774a4', '79ac7823-44f6-5db4-be19-b55cdb436694', 'f99e9244-d5a2-58bb-8757-787813be14f3', '70b442c5-be84-53b6-befe-1fffe647562a', 'Cliente se equivoco de presentacion', '2026-07-21 15:00:00+00'),
    ('56335b84-b75a-56c0-9f3a-a72de8b927c0', 'c2726692-927a-5bdd-8f56-4ee70ef2ccdb', 'f99e9244-d5a2-58bb-8757-787813be14f3', '70b442c5-be84-53b6-befe-1fffe647562a', 'Cambio de indicacion medica', '2026-07-21 15:00:00+00'),
    ('8594474b-912f-5a36-87e6-a20fd7b79201', 'c615ef40-7276-5f66-94cb-79d307b4890a', '3314d935-7880-5e41-8bd6-ddd0985e003c', '6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', 'Cliente ya no lo necesita', '2026-07-23 15:00:00+00'),
    ('edf4e71a-ed92-5302-915f-7115dd54186c', '320ee597-b801-5d10-8059-30b7ed403118', '3314d935-7880-5e41-8bd6-ddd0985e003c', '6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', 'Producto proximo a vencer detectado en mostrador', '2026-07-23 15:00:00+00'),
    ('647d2d02-b786-565a-9afc-e66998be2805', 'b9a5a97e-ecdb-5cc9-9dee-25e535d80b31', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', '0110c66b-eb7b-55c1-9f8b-304dc50e4430', 'Error de digitacion en la venta original', '2026-07-25 15:00:00+00'),
    ('615c5f64-cfc6-588d-9546-8787d2ba9b73', 'd4b72821-e19f-5591-af34-9f0e01cec4ec', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', '0110c66b-eb7b-55c1-9f8b-304dc50e4430', 'Cliente solicito otro laboratorio', '2026-07-25 15:00:00+00'),
    ('b79bb6f3-bf10-5889-af10-3779c732a6df', '0064b946-33e2-52eb-955d-9fe64ed1ff78', 'eef251fe-7356-56c5-81a9-96ec687e793e', '390070d1-0a7f-55ed-bf44-db1702396fb8', 'Devolucion por garantia comercial', '2026-07-27 15:00:00+00'),
    ('632846a0-f8ab-5414-affe-3a9572100701', '155d3d62-cc80-5868-a490-2ad665b084dc', 'eef251fe-7356-56c5-81a9-96ec687e793e', '390070d1-0a7f-55ed-bf44-db1702396fb8', 'Cliente devolvio por duplicidad de compra', '2026-07-27 15:00:00+00');

INSERT INTO detalle_devoluciones (id, devolucion_id, detalle_venta_id, producto_id, cantidad, monto_devuelto) VALUES
    ('41df5992-d778-5a81-a803-b200834c1367', '94199053-63a5-5633-9d9c-7c2038e92964', '3833110c-b2d6-5e6c-8acd-ac9911c11e37', 'a81f4913-8184-5751-9322-c2f7a1807445', 1, '12.50'),
    ('a1abccc5-1230-52f9-a405-d2d520ad2431', 'cbc21614-b7da-5431-912f-176af9238985', '939e1772-efb6-5785-8ebe-eaa22d9ee2b2', 'b5018120-11e1-59d1-839a-d50f4122ce09', 1, '14.50'),
    ('7617b76c-1863-5681-9fd7-c5b27bba6b02', 'dd73c0b8-ae80-5328-b952-32f2f10774a4', 'daab77f7-6059-5d75-9eff-19d841285179', '350a61ff-1f06-5f0e-930c-9e8850332b0a', 1, '25.90'),
    ('8cecc910-c6f0-54d5-83f9-48ca9a92e0db', '56335b84-b75a-56c0-9f3a-a72de8b927c0', '412c05bb-f302-54b6-9dcc-7fcb80401b66', '23ceed9c-b0de-5ac7-8e1c-c72633d47266', 1, '15.00'),
    ('b5df4971-bce0-5a61-bf52-faffa8fd9bb7', '8594474b-912f-5a36-87e6-a20fd7b79201', '2448a568-28a9-5b4f-b4f1-2297910fa889', '120e971e-060e-518b-91b9-4acff22a00ab', 1, '9.90'),
    ('6b884d77-7671-56c1-acc6-43596daf94cb', 'edf4e71a-ed92-5302-915f-7115dd54186c', 'a79c5f86-1bf3-5025-a0b1-8b967a2621f2', '5d2fe11e-e6b8-5c32-970d-e8930d556d18', 1, '22.00'),
    ('931aa4f7-9f01-5db0-9843-a748ba6a9383', '647d2d02-b786-565a-9afc-e66998be2805', '712f7ea4-ef9d-55c0-8cad-7140d06660a1', 'c387809a-8780-501f-8c81-6d371e9a19bf', 1, '19.90'),
    ('9ed1c9c8-93b9-5d87-ad97-93176fafebe5', '615c5f64-cfc6-588d-9546-8787d2ba9b73', 'bd07b798-7978-56de-b851-a7257f1d7d77', 'e8782adf-f18b-51b7-96ec-6c6f38ddd4dc', 1, '11.20'),
    ('711779ee-e892-5c7b-a183-2576ef837bd4', 'b79bb6f3-bf10-5889-af10-3779c732a6df', '7d873eb3-017a-5d2c-ad33-dad76930bd70', 'a203229b-3f3d-51b7-8f7b-beb729aefab0', 1, '8.50'),
    ('6211e562-2b49-5168-b35b-f96c45d03569', '632846a0-f8ab-5414-affe-3a9572100701', '20e83b3a-e642-54a6-bc29-f93d4d7fba4b', '586a5aa6-3933-5159-987a-8a64762d74d7', 1, '10.50');

INSERT INTO notas_credito (id, venta_id, comprobante_id, local_id, usuario_id, motivo, monto_total, fecha) VALUES
    ('293151d8-f075-5356-acf1-63ae5c9e8376', '8f82d1fc-945f-5869-80a1-4dc2eb4d8b83', '8964f061-af25-598d-88b5-08b254cf3e28', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', 'Nota de credito por devolucion parcial: Cliente reporto reaccion alergica leve', '12.50', '2026-07-19 15:10:00+00'),
    ('9bba86cf-c31d-5b6c-9f47-674da2ddd4d0', '80630ced-8f45-5664-afee-bfd87178411b', '99f6a4e0-3b33-5692-9cfb-76fac6689a4f', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', 'Nota de credito por devolucion parcial: Producto con empaque danado', '14.50', '2026-07-19 15:10:00+00'),
    ('087b7c0f-4043-50a6-a003-bb6971b3c84d', '79ac7823-44f6-5db4-be19-b55cdb436694', 'ec8e0c0a-d6c6-5561-953d-d318af54df38', 'f99e9244-d5a2-58bb-8757-787813be14f3', '70b442c5-be84-53b6-befe-1fffe647562a', 'Nota de credito por devolucion parcial: Cliente se equivoco de presentacion', '25.90', '2026-07-21 15:10:00+00'),
    ('c6c318df-71aa-54ca-af01-dfd0e2041f63', 'c2726692-927a-5bdd-8f56-4ee70ef2ccdb', '20205102-ecc0-50d8-bb74-bfa09c67bb52', 'f99e9244-d5a2-58bb-8757-787813be14f3', '70b442c5-be84-53b6-befe-1fffe647562a', 'Nota de credito por devolucion parcial: Cambio de indicacion medica', '15.00', '2026-07-21 15:10:00+00'),
    ('cdbdf847-7c1c-5eb7-bebc-4e72f2177561', 'c615ef40-7276-5f66-94cb-79d307b4890a', '3e069e6d-5ab9-5e02-b646-baed2a58f476', '3314d935-7880-5e41-8bd6-ddd0985e003c', '6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', 'Nota de credito por devolucion parcial: Cliente ya no lo necesita', '9.90', '2026-07-23 15:10:00+00'),
    ('9b6d273d-2398-5e31-9499-b1479e617d0b', '320ee597-b801-5d10-8059-30b7ed403118', '6d88a45d-1c0e-50b9-91e4-1597db7e8acb', '3314d935-7880-5e41-8bd6-ddd0985e003c', '6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', 'Nota de credito por devolucion parcial: Producto proximo a vencer detectado en mostrador', '22.00', '2026-07-23 15:10:00+00'),
    ('556f7890-7ef1-5184-8504-35d00c0eaf78', 'b9a5a97e-ecdb-5cc9-9dee-25e535d80b31', '0929b10d-e816-5a7b-a7e9-f4e9eace17c9', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', '0110c66b-eb7b-55c1-9f8b-304dc50e4430', 'Nota de credito por devolucion parcial: Error de digitacion en la venta original', '19.90', '2026-07-25 15:10:00+00'),
    ('971ccae0-c691-5378-a55a-a1f428af8a42', 'd4b72821-e19f-5591-af34-9f0e01cec4ec', '6e1f6c79-f633-577c-b2ac-f16b48eabd5a', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', '0110c66b-eb7b-55c1-9f8b-304dc50e4430', 'Nota de credito por devolucion parcial: Cliente solicito otro laboratorio', '11.20', '2026-07-25 15:10:00+00'),
    ('d32b83dc-2db0-5273-b970-8d02c6769268', '0064b946-33e2-52eb-955d-9fe64ed1ff78', 'cfb33adf-aaf6-56c8-ac50-5d72c88b4837', 'eef251fe-7356-56c5-81a9-96ec687e793e', '390070d1-0a7f-55ed-bf44-db1702396fb8', 'Nota de credito por devolucion parcial: Devolucion por garantia comercial', '8.50', '2026-07-27 15:10:00+00'),
    ('2fc6d8d4-b9f5-5096-a2cf-52d2a2135cf6', '155d3d62-cc80-5868-a490-2ad665b084dc', '9bb4222a-b6a3-55bc-b9ee-ca7bdf0a3e7c', 'eef251fe-7356-56c5-81a9-96ec687e793e', '390070d1-0a7f-55ed-bf44-db1702396fb8', 'Nota de credito por devolucion parcial: Cliente devolvio por duplicidad de compra', '10.50', '2026-07-27 15:10:00+00');
