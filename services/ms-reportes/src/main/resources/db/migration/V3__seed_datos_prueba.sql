-- Semilla de datos de prueba para ms-reportes.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO reglas_incentivo (id, nombre, producto_id, categoria_id, monto_por_unidad, vigencia_inicio, vigencia_fin, activa) VALUES
    ('5d5d7015-8cf4-559c-a7a1-f6de7b092083', 'Incentivo Paracetamol', 'a81f4913-8184-5751-9322-c2f7a1807445', null, 0.5, '2026-05-29', '2026-11-25', true),
    ('5c17052b-f4d6-5c01-83a5-6a19f5d87084', 'Incentivo linea Antibioticos', null, '1f4eb2a0-3988-5ec8-b95e-bca7b33f61e3', 1.2, '2026-05-29', '2026-11-25', true),
    ('9a769762-aca2-5e7b-b9de-e213f388eef6', 'Incentivo Vitamina C', 'b5018120-11e1-59d1-839a-d50f4122ce09', null, 0.8, '2026-05-29', '2026-11-25', true),
    ('1bdf8c0b-3953-5c2d-af44-efdd6a23debc', 'Incentivo linea Cardiovascular', null, 'e70bb699-1668-5790-b1a0-4b6e65ddb950', 1.0, '2026-05-29', '2026-11-25', true),
    ('0afcd54c-36ba-56f8-bad8-3f24dd81634a', 'Incentivo Losartan', '5d2fe11e-e6b8-5c32-970d-e8930d556d18', null, 1.5, '2026-06-28', '2026-10-26', true),
    ('bea4bb91-3618-58bb-94d7-7485d9c3fa0c', 'Incentivo campana antigripal', null, 'f9909839-4110-5a17-b119-9c7286810e9c', 0.6, '2026-04-29', '2026-07-18', false),
    ('77724e0b-e54f-588c-95fd-20b81dc98554', 'Incentivo Metformina', 'c387809a-8780-501f-8c81-6d371e9a19bf', null, 1.1, '2026-06-28', '2026-10-26', true),
    ('a603835a-3f0b-5611-9ca9-91d4097f17dd', 'Incentivo linea Dermatologicos', null, '5010ec5f-53fa-53d6-84a5-63df70fd19e8', 0.9, '2026-06-28', '2026-10-26', true),
    ('83f951fe-1431-52f6-8a33-870e2a8b92e8', 'Incentivo Cetirizina', '586a5aa6-3933-5159-987a-8a64762d74d7', null, 0.7, '2026-06-28', '2026-10-26', true),
    ('87ff7320-b325-5ac5-b0fa-05a664f985de', 'Incentivo general OTC fin de mes', null, null, 0.3, '2026-07-23', '2026-08-22', true);

INSERT INTO incentivos_venta (id, regla_incentivo_id, usuario_id, venta_id, detalle_venta_id, cantidad, monto_calculado, fecha) VALUES
    ('926e781e-b0e1-5ef9-a1d1-f9322fa935ae', '5d5d7015-8cf4-559c-a7a1-f6de7b092083', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', '8f82d1fc-945f-5869-80a1-4dc2eb4d8b83', '3833110c-b2d6-5e6c-8acd-ac9911c11e37', 2, '1.00', '2026-07-18 11:04:00+00'),
    ('c31f84aa-d04e-53e9-b6b1-6ab958333e84', '9a769762-aca2-5e7b-b9de-e213f388eef6', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', '80630ced-8f45-5664-afee-bfd87178411b', '939e1772-efb6-5785-8ebe-eaa22d9ee2b2', 3, '2.40', '2026-07-18 11:04:00+00'),
    ('3c274403-2ad2-54b2-9d22-c84e8d5f1332', '5c17052b-f4d6-5c01-83a5-6a19f5d87084', '70b442c5-be84-53b6-befe-1fffe647562a', '79ac7823-44f6-5db4-be19-b55cdb436694', 'daab77f7-6059-5d75-9eff-19d841285179', 1, '1.20', '2026-07-20 11:04:00+00'),
    ('d6c3e831-7da6-51c7-9560-006391b1431f', '0afcd54c-36ba-56f8-bad8-3f24dd81634a', '6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', '320ee597-b801-5d10-8059-30b7ed403118', 'a79c5f86-1bf3-5025-a0b1-8b967a2621f2', 1, '1.50', '2026-07-22 11:04:00+00'),
    ('1d2d59b0-a97d-5373-9cb9-268c63d626d3', '77724e0b-e54f-588c-95fd-20b81dc98554', '0110c66b-eb7b-55c1-9f8b-304dc50e4430', 'b9a5a97e-ecdb-5cc9-9dee-25e535d80b31', '712f7ea4-ef9d-55c0-8cad-7140d06660a1', 2, '2.20', '2026-07-24 11:04:00+00'),
    ('5369ccf6-3665-5ba3-b6d0-99aeb7efd7fc', '83f951fe-1431-52f6-8a33-870e2a8b92e8', '390070d1-0a7f-55ed-bf44-db1702396fb8', '155d3d62-cc80-5868-a490-2ad665b084dc', '20e83b3a-e642-54a6-bc29-f93d4d7fba4b', 2, '1.40', '2026-07-26 11:04:00+00');
