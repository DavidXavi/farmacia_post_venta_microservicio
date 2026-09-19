-- Semilla de datos de prueba para ms-ventas.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO ventas (id, local_id, caja_id, sesion_caja_id, usuario_id, cliente_id, convenio_seguro_id, linea_credito_id, fecha, estado, numero_correlativo) VALUES
    ('8f82d1fc-945f-5869-80a1-4dc2eb4d8b83', 'f99e9244-d5a2-58bb-8757-787813be14f3', '5dc4fdd2-e049-51b7-aa34-48f6c3b0b6f5', '23f811a1-cbe9-5a7b-93a1-1650fb6f032e', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', null, null, null, '2026-07-18 11:00:00+00', 'CONFIRMADA', 1),
    ('80630ced-8f45-5664-afee-bfd87178411b', 'f99e9244-d5a2-58bb-8757-787813be14f3', '5dc4fdd2-e049-51b7-aa34-48f6c3b0b6f5', '23f811a1-cbe9-5a7b-93a1-1650fb6f032e', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', null, null, null, '2026-07-18 11:00:00+00', 'CONFIRMADA', 2),
    ('79ac7823-44f6-5db4-be19-b55cdb436694', 'f99e9244-d5a2-58bb-8757-787813be14f3', '11c25355-4314-51b8-8726-63001d6b88f2', 'f20d9ceb-b59e-538a-86cd-89f845d78c8d', '70b442c5-be84-53b6-befe-1fffe647562a', '5730402c-8a8a-5313-9342-3d3c323841bb', null, null, '2026-07-20 11:00:00+00', 'CONFIRMADA', 3),
    ('c2726692-927a-5bdd-8f56-4ee70ef2ccdb', 'f99e9244-d5a2-58bb-8757-787813be14f3', '11c25355-4314-51b8-8726-63001d6b88f2', 'f20d9ceb-b59e-538a-86cd-89f845d78c8d', '70b442c5-be84-53b6-befe-1fffe647562a', null, null, null, '2026-07-20 11:00:00+00', 'CONFIRMADA', 4),
    ('c615ef40-7276-5f66-94cb-79d307b4890a', '3314d935-7880-5e41-8bd6-ddd0985e003c', 'e139085d-b389-53ba-9a68-a4cd8dab4ce2', '870c0715-9467-537f-a223-78df8fa7646a', '6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', '64edcc36-30ab-5369-973e-5ff70f94bd2f', null, null, '2026-07-22 11:00:00+00', 'CONFIRMADA', 5),
    ('320ee597-b801-5d10-8059-30b7ed403118', '3314d935-7880-5e41-8bd6-ddd0985e003c', 'e139085d-b389-53ba-9a68-a4cd8dab4ce2', '870c0715-9467-537f-a223-78df8fa7646a', '6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', '9a9fdbb4-5788-533d-9f61-70009063cabb', '812a400b-b984-514a-b4a0-041d0ec0d0d1', null, '2026-07-22 11:00:00+00', 'CONFIRMADA', 6),
    ('b9a5a97e-ecdb-5cc9-9dee-25e535d80b31', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', 'e72c6616-a1ca-554b-b7e4-43a1ac7d5a23', '2a95a15b-9a2c-5580-a03d-b26a4d7c25ed', '0110c66b-eb7b-55c1-9f8b-304dc50e4430', '6a29a8bf-b7ff-55f4-aab6-d69ff8a7d243', null, null, '2026-07-24 11:00:00+00', 'CONFIRMADA', 7),
    ('d4b72821-e19f-5591-af34-9f0e01cec4ec', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', 'e72c6616-a1ca-554b-b7e4-43a1ac7d5a23', '2a95a15b-9a2c-5580-a03d-b26a4d7c25ed', '0110c66b-eb7b-55c1-9f8b-304dc50e4430', '45b69675-01ec-554f-a046-52a380b10995', null, null, '2026-07-24 11:00:00+00', 'CONFIRMADA', 8),
    ('0064b946-33e2-52eb-955d-9fe64ed1ff78', 'eef251fe-7356-56c5-81a9-96ec687e793e', 'c8b3de56-7a27-533e-8e6d-9195d5ad2a5e', '418e8036-44a1-5736-95ff-5caebcb16130', '390070d1-0a7f-55ed-bf44-db1702396fb8', '6bc54e9a-6cc2-54a8-ac3f-428a6c4750d4', null, null, '2026-07-26 11:00:00+00', 'CONFIRMADA', 9),
    ('155d3d62-cc80-5868-a490-2ad665b084dc', 'eef251fe-7356-56c5-81a9-96ec687e793e', 'c8b3de56-7a27-533e-8e6d-9195d5ad2a5e', '418e8036-44a1-5736-95ff-5caebcb16130', '390070d1-0a7f-55ed-bf44-db1702396fb8', '770d8af1-6a03-5d45-9c2d-931bf5016044', null, '81d9e5ab-9bce-5d09-a3a7-898d278a2ebc', '2026-07-26 11:00:00+00', 'CONFIRMADA', 10);

INSERT INTO detalles_venta (id, venta_id, producto_id, cantidad, precio_unitario, tasa_impuesto, promocion_aplicada_id, receta_id, descuento_monto, nombre_producto) VALUES
    ('3833110c-b2d6-5e6c-8acd-ac9911c11e37', '8f82d1fc-945f-5869-80a1-4dc2eb4d8b83', 'a81f4913-8184-5751-9322-c2f7a1807445', 2, 12.5, '18', null, null, '0', 'Paracetamol 500mg'),
    ('939e1772-efb6-5785-8ebe-eaa22d9ee2b2', '80630ced-8f45-5664-afee-bfd87178411b', 'b5018120-11e1-59d1-839a-d50f4122ce09', 3, 14.5, '18', '6d9c5d64-ba0d-56cf-8f8c-26288338db67', null, '14.50', 'Vitamina C 1g'),
    ('daab77f7-6059-5d75-9eff-19d841285179', '79ac7823-44f6-5db4-be19-b55cdb436694', '350a61ff-1f06-5f0e-930c-9e8850332b0a', 1, 25.9, '18', null, '66d4284a-1a11-5d7e-ae9a-035ed5295ff5', '0', 'Amoxicilina 500mg'),
    ('412c05bb-f302-54b6-9dcc-7fcb80401b66', 'c2726692-927a-5bdd-8f56-4ee70ef2ccdb', '23ceed9c-b0de-5ac7-8e1c-c72633d47266', 2, 15.0, '18', 'e630542d-e22d-5a4b-a714-36a39cbd8f25', null, '5.00', 'Ibuprofeno 400mg'),
    ('2448a568-28a9-5b4f-b4f1-2297910fa889', 'c615ef40-7276-5f66-94cb-79d307b4890a', '120e971e-060e-518b-91b9-4acff22a00ab', 1, 9.9, '18', null, '4a8f9f8b-a69a-576b-8fdf-889964799b3c', '0', 'Clonazepam 2mg'),
    ('a79c5f86-1bf3-5025-a0b1-8b967a2621f2', '320ee597-b801-5d10-8059-30b7ed403118', '5d2fe11e-e6b8-5c32-970d-e8930d556d18', 1, 22.0, '18', null, '21ac9c25-b130-5041-a056-2af940812823', '0', 'Losartan 50mg'),
    ('712f7ea4-ef9d-55c0-8cad-7140d06660a1', 'b9a5a97e-ecdb-5cc9-9dee-25e535d80b31', 'c387809a-8780-501f-8c81-6d371e9a19bf', 2, 19.9, '18', null, 'fb19a897-24be-5fde-bb2a-44db107a8c17', '0', 'Metformina 850mg'),
    ('bd07b798-7978-56de-b851-a7257f1d7d77', 'd4b72821-e19f-5591-af34-9f0e01cec4ec', 'e8782adf-f18b-51b7-96ec-6c6f38ddd4dc', 1, 11.2, '18', null, '67b3ca86-3ebd-56ea-a250-95dafe2a86a4', '0', 'Diazepam 10mg'),
    ('7d873eb3-017a-5d2c-ad33-dad76930bd70', '0064b946-33e2-52eb-955d-9fe64ed1ff78', 'a203229b-3f3d-51b7-8f7b-beb729aefab0', 2, 8.5, '18', 'f487a9ba-8aa1-503d-9f09-ea3607141901', null, '2.55', 'Loratadina 10mg'),
    ('20e83b3a-e642-54a6-bc29-f93d4d7fba4b', '155d3d62-cc80-5868-a490-2ad665b084dc', '586a5aa6-3933-5159-987a-8a64762d74d7', 2, 10.5, '18', 'b8ee45d8-8295-5b08-a7c5-c021dd46f243', null, '10.50', 'Cetirizina 10mg');

INSERT INTO detalle_venta_lotes (id, detalle_venta_id, lote_id, cantidad_tomada) VALUES
    ('5d63ad92-db11-566d-ba20-bf0325787b5c', '3833110c-b2d6-5e6c-8acd-ac9911c11e37', 'ec22db25-d17d-5335-800e-7466c1d0d709', 2),
    ('13eb77de-2358-500f-b15d-357b51f7d6f1', '939e1772-efb6-5785-8ebe-eaa22d9ee2b2', '92e31d84-d764-516d-876e-e2531be07666', 3),
    ('48050caf-7cfe-5db9-9ce8-94468d719540', 'daab77f7-6059-5d75-9eff-19d841285179', '74ae3fa5-bc22-5e87-b3a3-94d22c9bc66b', 1),
    ('7398e3f3-6f95-5ba8-aad8-ec12d03f0a0a', '412c05bb-f302-54b6-9dcc-7fcb80401b66', '4c046251-e60a-5e3c-bbc5-31e03fa418cb', 2),
    ('75e3c975-b212-508d-a807-beda1273ee9e', '2448a568-28a9-5b4f-b4f1-2297910fa889', '9ea735d3-41a9-5eb3-b2c3-4d9a59bce0ee', 1),
    ('be79a586-fc78-5862-9767-1e78e3b7418b', 'a79c5f86-1bf3-5025-a0b1-8b967a2621f2', '1e2d9416-53e8-50c2-8cc8-3c5283a3fa26', 1),
    ('56d9fa9f-fb13-51b4-a49a-29f5d9a594a5', '712f7ea4-ef9d-55c0-8cad-7140d06660a1', 'b004c2ae-44d2-51fb-ae8f-4279b1f3c480', 2),
    ('65181788-7836-5203-b7d9-027cf52bce34', 'bd07b798-7978-56de-b851-a7257f1d7d77', '2af68e15-9190-5583-b5ec-d1c430b6a14f', 1),
    ('4543e6be-b828-5b8f-969c-5f95ad3991c1', '7d873eb3-017a-5d2c-ad33-dad76930bd70', '4472e3e9-003f-5f21-a3dd-9e90b0235187', 2),
    ('c4f373ba-d081-5dbc-ad50-ddc8b147c93f', '20e83b3a-e642-54a6-bc29-f93d4d7fba4b', '153299a9-2688-557f-8c0e-e75c28ac30a7', 2);

INSERT INTO pagos (id, venta_id, forma_pago_id, monto, codigo_autorizacion, fecha) VALUES
    ('c95e97bb-d4ec-5c22-932d-ecf066beeed8', '8f82d1fc-945f-5869-80a1-4dc2eb4d8b83', '00000000-0000-0000-0000-000000000001', '29.50', null, '2026-07-18 11:02:00+00'),
    ('e64e418e-fbda-53de-87da-184981ada305', '80630ced-8f45-5664-afee-bfd87178411b', '00000000-0000-0000-0000-000000000002', '34.22', null, '2026-07-18 11:02:00+00'),
    ('6238ccb8-f8a4-595f-ab3a-e34821bc2373', '79ac7823-44f6-5db4-be19-b55cdb436694', '00000000-0000-0000-0000-000000000001', '30.56', null, '2026-07-20 11:02:00+00'),
    ('c6be1c39-17f0-56d0-a98a-0c6e23589004', 'c2726692-927a-5bdd-8f56-4ee70ef2ccdb', '00000000-0000-0000-0000-000000000004', '29.50', null, '2026-07-20 11:02:00+00'),
    ('7702ea93-39ed-5aaf-a471-b12eed58089d', 'c615ef40-7276-5f66-94cb-79d307b4890a', '00000000-0000-0000-0000-000000000001', '11.68', null, '2026-07-22 11:02:00+00'),
    ('dc53060b-4643-5e51-97e8-804951588bed', '320ee597-b801-5d10-8059-30b7ed403118', '00000000-0000-0000-0000-000000000006', '25.96', null, '2026-07-22 11:02:00+00'),
    ('a2d0a566-f221-5981-a3a2-e5ec4cf12b84', 'b9a5a97e-ecdb-5cc9-9dee-25e535d80b31', '00000000-0000-0000-0000-000000000003', '46.96', null, '2026-07-24 11:02:00+00'),
    ('1948a00b-a8b1-58ac-8fcc-444146c6b611', 'd4b72821-e19f-5591-af34-9f0e01cec4ec', '00000000-0000-0000-0000-000000000001', '13.22', null, '2026-07-24 11:02:00+00'),
    ('920eea3c-932d-51d5-9ebb-97b64fc9a962', '0064b946-33e2-52eb-955d-9fe64ed1ff78', '00000000-0000-0000-0000-000000000005', '17.05', null, '2026-07-26 11:02:00+00'),
    ('2b1a7a22-9464-5bef-91a7-62ff6bffca5d', '155d3d62-cc80-5868-a490-2ad665b084dc', '00000000-0000-0000-0000-000000000007', '12.39', null, '2026-07-26 11:02:00+00');
