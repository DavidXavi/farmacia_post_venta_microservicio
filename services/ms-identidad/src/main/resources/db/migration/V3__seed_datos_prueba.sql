-- Semilla de datos de prueba para ms-identidad.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO locales (id, nombre, direccion, activo) VALUES
    ('f99e9244-d5a2-58bb-8757-787813be14f3', 'Sede Principal', 'Av. Principal 123, Lima', true),
    ('3314d935-7880-5e41-8bd6-ddd0985e003c', 'Sede Norte', 'Av. Tupac Amaru 456, Los Olivos', true),
    ('e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', 'Sede Sur', 'Av. Pachacutec 789, Villa El Salvador', true),
    ('eef251fe-7356-56c5-81a9-96ec687e793e', 'Sede Este', 'Av. Nicolas Ayllon 321, Ate', true);

INSERT INTO cajas (id, nombre, local_id, activa) VALUES
    ('5dc4fdd2-e049-51b7-aa34-48f6c3b0b6f5', 'Caja 1', 'f99e9244-d5a2-58bb-8757-787813be14f3', true),
    ('11c25355-4314-51b8-8726-63001d6b88f2', 'Caja 2', 'f99e9244-d5a2-58bb-8757-787813be14f3', true),
    ('99c9fc83-2c09-5098-8e6c-1835a8c04604', 'Caja 3', 'f99e9244-d5a2-58bb-8757-787813be14f3', true),
    ('e139085d-b389-53ba-9a68-a4cd8dab4ce2', 'Caja 1', '3314d935-7880-5e41-8bd6-ddd0985e003c', true),
    ('dbadda87-f847-5379-848a-47622f527da5', 'Caja 2', '3314d935-7880-5e41-8bd6-ddd0985e003c', true),
    ('e72c6616-a1ca-554b-b7e4-43a1ac7d5a23', 'Caja 1', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', true),
    ('762f7291-be49-52a4-b718-29241a46ba15', 'Caja 2', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', true),
    ('c8b3de56-7a27-533e-8e6d-9195d5ad2a5e', 'Caja 1', 'eef251fe-7356-56c5-81a9-96ec687e793e', true),
    ('1f820748-bb6f-5070-95e4-0a830dc0b0aa', 'Caja 2', 'eef251fe-7356-56c5-81a9-96ec687e793e', false),
    ('ca2c96c4-6f38-5ad6-aa77-1d1229ab2b8a', 'Caja de respaldo', 'f99e9244-d5a2-58bb-8757-787813be14f3', false);

INSERT INTO usuarios (id, nombre_usuario, password_hash, estado, local_id, permisos) VALUES
    ('cb2ea947-215b-5e31-9864-3b8f7c163a74', 'admin', '$2b$10$NrER4N5SVFEe6Xb1Ij8CdOmctYiDPBnoaCoU1QAUCgDTPEEFK1p86', 'ACTIVO', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'ANULAR_VENTAS,EMITIR_NOTA_CREDITO,VER_AUDITORIA'),
    ('70b442c5-be84-53b6-befe-1fffe647562a', 'cajero1', '$2b$10$AAmYH9C4Qy08gAX5tekmreuZTht4RQrGjpddI96Tl/Jrxv0/qHfzy', 'ACTIVO', 'f99e9244-d5a2-58bb-8757-787813be14f3', ''),
    ('0110c66b-eb7b-55c1-9f8b-304dc50e4430', 'cajero2', '$2b$10$AAmYH9C4Qy08gAX5tekmreuZTht4RQrGjpddI96Tl/Jrxv0/qHfzy', 'ACTIVO', 'f99e9244-d5a2-58bb-8757-787813be14f3', ''),
    ('6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', 'cajero3', '$2b$10$AAmYH9C4Qy08gAX5tekmreuZTht4RQrGjpddI96Tl/Jrxv0/qHfzy', 'ACTIVO', '3314d935-7880-5e41-8bd6-ddd0985e003c', ''),
    ('54677f1b-60d4-5b0f-8b0a-b70744852db5', 'cajero4', '$2b$10$AAmYH9C4Qy08gAX5tekmreuZTht4RQrGjpddI96Tl/Jrxv0/qHfzy', 'SUSPENDIDO', 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', ''),
    ('91663b6b-7c14-5dbb-9525-4eb36a55e37e', 'quimico1', '$2b$10$AAmYH9C4Qy08gAX5tekmreuZTht4RQrGjpddI96Tl/Jrxv0/qHfzy', 'ACTIVO', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'VALIDAR_RECETAS'),
    ('bd10af68-5662-5538-bc1b-13ee06bcf23e', 'quimico2', '$2b$10$AAmYH9C4Qy08gAX5tekmreuZTht4RQrGjpddI96Tl/Jrxv0/qHfzy', 'ACTIVO', '3314d935-7880-5e41-8bd6-ddd0985e003c', 'VALIDAR_RECETAS'),
    ('3c67bcbb-44e5-54b0-af93-1b3319bae6c7', 'inventario1', '$2b$10$AAmYH9C4Qy08gAX5tekmreuZTht4RQrGjpddI96Tl/Jrxv0/qHfzy', 'ACTIVO', 'f99e9244-d5a2-58bb-8757-787813be14f3', 'AJUSTAR_STOCK'),
    ('390070d1-0a7f-55ed-bf44-db1702396fb8', 'central1', '$2b$10$AAmYH9C4Qy08gAX5tekmreuZTht4RQrGjpddI96Tl/Jrxv0/qHfzy', 'ACTIVO', 'eef251fe-7356-56c5-81a9-96ec687e793e', ''),
    ('6b0b6090-f327-5f52-905c-b43f06dadf01', 'admin2', '$2b$10$NrER4N5SVFEe6Xb1Ij8CdOmctYiDPBnoaCoU1QAUCgDTPEEFK1p86', 'ACTIVO', '3314d935-7880-5e41-8bd6-ddd0985e003c', 'ANULAR_VENTAS,EMITIR_NOTA_CREDITO,VER_AUDITORIA');

INSERT INTO usuarios_roles (usuario_id, rol_id) VALUES
    ('cb2ea947-215b-5e31-9864-3b8f7c163a74', '11111111-1111-1111-1111-111111111101'),
    ('70b442c5-be84-53b6-befe-1fffe647562a', '11111111-1111-1111-1111-111111111102'),
    ('0110c66b-eb7b-55c1-9f8b-304dc50e4430', '11111111-1111-1111-1111-111111111102'),
    ('6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', '11111111-1111-1111-1111-111111111102'),
    ('54677f1b-60d4-5b0f-8b0a-b70744852db5', '11111111-1111-1111-1111-111111111102'),
    ('91663b6b-7c14-5dbb-9525-4eb36a55e37e', '11111111-1111-1111-1111-111111111103'),
    ('bd10af68-5662-5538-bc1b-13ee06bcf23e', '11111111-1111-1111-1111-111111111103'),
    ('3c67bcbb-44e5-54b0-af93-1b3319bae6c7', '11111111-1111-1111-1111-111111111104'),
    ('390070d1-0a7f-55ed-bf44-db1702396fb8', '11111111-1111-1111-1111-111111111105'),
    ('6b0b6090-f327-5f52-905c-b43f06dadf01', '11111111-1111-1111-1111-111111111101');

INSERT INTO sesiones_caja (id, caja_id, usuario_id, fecha_apertura, monto_inicial, fecha_cierre, monto_esperado, monto_declarado, diferencia, observacion_cierre, estado) VALUES
    ('23f811a1-cbe9-5a7b-93a1-1650fb6f032e', '5dc4fdd2-e049-51b7-aa34-48f6c3b0b6f5', 'cb2ea947-215b-5e31-9864-3b8f7c163a74', '2026-07-18 08:00:00+00', 100.0, '2026-07-18 20:00:00+00', '129.50', '129.50', '0.00', 'Cierre de turno sin novedad', 'CERRADA'),
    ('f20d9ceb-b59e-538a-86cd-89f845d78c8d', '11c25355-4314-51b8-8726-63001d6b88f2', '70b442c5-be84-53b6-befe-1fffe647562a', '2026-07-20 08:00:00+00', 100.0, '2026-07-20 20:00:00+00', '130.56', '130.56', '0.00', 'Cierre de turno sin novedad', 'CERRADA'),
    ('870c0715-9467-537f-a223-78df8fa7646a', 'e139085d-b389-53ba-9a68-a4cd8dab4ce2', '6dd4f5c5-c20e-5ba7-b5db-092b5c6f7cb2', '2026-07-22 08:00:00+00', 100.0, '2026-07-22 20:00:00+00', '111.68', '111.68', '0.00', 'Cierre de turno sin novedad', 'CERRADA'),
    ('2a95a15b-9a2c-5580-a03d-b26a4d7c25ed', 'e72c6616-a1ca-554b-b7e4-43a1ac7d5a23', '0110c66b-eb7b-55c1-9f8b-304dc50e4430', '2026-07-24 08:00:00+00', 100.0, '2026-07-24 20:00:00+00', '113.22', '113.22', '0.00', 'Cierre de turno sin novedad', 'CERRADA'),
    ('418e8036-44a1-5736-95ff-5caebcb16130', 'c8b3de56-7a27-533e-8e6d-9195d5ad2a5e', '390070d1-0a7f-55ed-bf44-db1702396fb8', '2026-07-26 08:00:00+00', 100.0, '2026-07-26 20:00:00+00', '100.00', '100.00', '0.00', 'Cierre de turno sin novedad', 'CERRADA');
