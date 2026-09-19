-- Semilla de datos de prueba para ms-credito.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO lineas_credito (id, cliente_id, monto_autorizado, saldo_disponible, vigencia_inicio, vigencia_fin, estado) VALUES
    ('cd8e63d0-864e-5eee-9f5a-87bb4eabcac1', '5730402c-8a8a-5313-9342-3d3c323841bb', 500.0, 500.0, '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('9ecaa8dc-e832-5ad6-9e60-af087434fcdc', 'a6552978-7e8c-51d4-8b74-5a6718fe3987', 800.0, 620.0, '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('92f41840-bf04-5d04-83de-d6cd32dcf491', '9a9fdbb4-5788-533d-9f61-70009063cabb', 300.0, 300.0, '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('ac179c5d-ae47-5595-a3fd-ae218960e1da', '64edcc36-30ab-5369-973e-5ff70f94bd2f', 1000.0, 750.0, '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('81d9e5ab-9bce-5d09-a3a7-898d278a2ebc', '770d8af1-6a03-5d45-9c2d-931bf5016044', 600.0, 587.61, '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('c4886b60-4a3c-5888-a4f3-93e2065136d1', '6bc54e9a-6cc2-54a8-ac3f-428a6c4750d4', 400.0, 400.0, '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('7fc3bfb4-b353-570b-b7a8-9422536987a8', '6a29a8bf-b7ff-55f4-aab6-d69ff8a7d243', 700.0, 250.0, '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('358dfc95-01ef-5f9a-a73a-a28cb80ebeb9', '45b69675-01ec-554f-a046-52a380b10995', 250.0, 250.0, '2026-01-09', '2027-05-24', 'BLOQUEADA'),
    ('25cf4d14-7ea6-5a42-8914-b59c323c4e3f', 'acf795dc-15d3-52ce-b5ab-e81f6fbe50df', 900.0, 900.0, '2026-01-09', '2027-05-24', 'INACTIVA'),
    ('eb3e60c6-d022-5f72-a06a-24bd5f0a38f3', '8a0c1d3f-b9d3-5879-90df-e0b90d5bfc3e', 350.0, 350.0, '2026-01-09', '2027-05-24', 'ACTIVA');

INSERT INTO movimientos_credito (id, linea_credito_id, venta_id, tipo, monto, saldo_resultante, fecha)
SELECT '95011532-8356-50a5-b8f6-99bf83d753dd',
       lc.id,
       '155d3d62-cc80-5868-a490-2ad665b084dc',
       'CARGO',
       12.39,
       lc.saldo_disponible,
       '2026-07-26 11:05:00+00'
  FROM lineas_credito lc
 WHERE lc.id = '81d9e5ab-9bce-5d09-a3a7-898d278a2ebc';
