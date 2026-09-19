-- Semilla de datos de prueba para ms-catalogo.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO categorias (id, nombre) VALUES
    ('b726a47e-ca01-5ede-8b91-ba7a84df0372', 'Analgesicos'),
    ('1f4eb2a0-3988-5ec8-b95e-bca7b33f61e3', 'Antibioticos'),
    ('f9909839-4110-5a17-b119-9c7286810e9c', 'Antigripales'),
    ('3cacccde-397e-5935-9d3e-8d9c82a3be21', 'Antialergicos'),
    ('a2b098bc-35c4-55a4-9f4a-d6c3d7a164c6', 'Vitaminas y Suplementos'),
    ('5010ec5f-53fa-53d6-84a5-63df70fd19e8', 'Dermatologicos'),
    ('31aaf8bf-74c6-568b-a7c5-e82a30da1826', 'Gastrointestinales'),
    ('e70bb699-1668-5790-b1a0-4b6e65ddb950', 'Cardiovasculares'),
    ('575d98c4-e8fd-53b4-84e7-99d8dd254f2c', 'Antiinflamatorios'),
    ('0f2cc80d-b21c-5d64-b8d7-9440d9a42a56', 'Cuidado Personal');

INSERT INTO laboratorios (id, nombre) VALUES
    ('f7faedc8-677c-5d41-949b-4eba95ada5a2', 'Laboratorio Generico'),
    ('5266de0f-285f-55f5-a564-1a81b9a3dcd4', 'Bayer'),
    ('006e90bb-5ad0-5d1f-9cce-9c864fdbbf65', 'Pfizer'),
    ('96407a87-6a9c-5f57-8d08-c400e5dff751', 'Genfar'),
    ('98a2c864-a484-59b1-bf9e-5295d7a012ad', 'Farmindustria'),
    ('61a6948b-d4c8-51c3-b4ef-9151d961aeab', 'Roche'),
    ('22af14f4-49a3-53f0-9cb2-43b1e0835c5c', 'Abbott'),
    ('1c40a9fd-4cd3-5a27-b5fa-dcacec736c27', 'MK'),
    ('ea027606-a3e7-511d-ad67-dfc806ff32f1', 'Medifarma'),
    ('ced1d813-e815-509a-8e67-6ca9c14c032d', 'Teva');

INSERT INTO presentaciones (id, nombre, unidad_medida) VALUES
    ('a287e391-d791-56e0-9632-f406e56d5948', 'Tableta x 10', 'Caja'),
    ('d0c03786-54ce-52de-b930-6c63682ab45a', 'Tableta x 20', 'Caja'),
    ('5cdb861a-25dc-5cb8-806c-c9351152537f', 'Jarabe 120ml', 'Frasco'),
    ('6bb17e00-dd51-577b-a221-55e03f35f8c7', 'Ampolla x 1', 'Unidad'),
    ('644b1965-bf79-5570-bca4-1fb9725035f3', 'Crema 30g', 'Tubo'),
    ('7aa0ee64-c39d-5b11-b3f5-7dd4013e580b', 'Capsula x 10', 'Caja'),
    ('d0ade409-9cb4-5f0c-b538-9fee514022f1', 'Suspension 60ml', 'Frasco'),
    ('9f7d5c86-4b6d-5070-b114-c92b741a0519', 'Gotas 15ml', 'Frasco'),
    ('880f1ed2-80a2-5943-b62c-39386b0b7159', 'Sobre x 1', 'Unidad'),
    ('c82bf686-9aee-53ca-a47b-6e2e0b467d9e', 'Frasco 100 tabletas', 'Frasco');

INSERT INTO productos (id, codigo_interno, codigo_barras, nombre_comercial, descripcion, tipo_producto, categoria_id, laboratorio_id, presentacion_id, precio_venta, es_controlado, requiere_receta, tipo_receta_requerida, estado) VALUES
    ('a81f4913-8184-5751-9322-c2f7a1807445', 'P0001', '7750001000019', 'Paracetamol 500mg', 'Analgesico y antipiretico de venta libre', 'OTC', 'b726a47e-ca01-5ede-8b91-ba7a84df0372', 'f7faedc8-677c-5d41-949b-4eba95ada5a2', 'a287e391-d791-56e0-9632-f406e56d5948', 12.5, false, false, null, 'ACTIVO'),
    ('350a61ff-1f06-5f0e-930c-9e8850332b0a', 'P0002', '7750001000026', 'Amoxicilina 500mg', 'Antibiotico de amplio espectro', 'MEDICAMENTO', '1f4eb2a0-3988-5ec8-b95e-bca7b33f61e3', '5266de0f-285f-55f5-a564-1a81b9a3dcd4', '7aa0ee64-c39d-5b11-b3f5-7dd4013e580b', 25.9, false, true, 'NORMAL', 'ACTIVO'),
    ('a203229b-3f3d-51b7-8f7b-beb729aefab0', 'P0003', '7750001000033', 'Loratadina 10mg', 'Antihistaminico para alergias', 'OTC', '3cacccde-397e-5935-9d3e-8d9c82a3be21', '96407a87-6a9c-5f57-8d08-c400e5dff751', 'a287e391-d791-56e0-9632-f406e56d5948', 8.5, false, false, null, 'ACTIVO'),
    ('23ceed9c-b0de-5ac7-8e1c-c72633d47266', 'P0004', '7750001000040', 'Ibuprofeno 400mg', 'Antiinflamatorio no esteroideo', 'OTC', '575d98c4-e8fd-53b4-84e7-99d8dd254f2c', 'f7faedc8-677c-5d41-949b-4eba95ada5a2', 'a287e391-d791-56e0-9632-f406e56d5948', 15.0, false, false, null, 'ACTIVO'),
    ('a1b00829-c7df-5dae-89b5-b66712d99d49', 'P0005', '7750001000057', 'Omeprazol 20mg', 'Inhibidor de la bomba de protones', 'OTC', '31aaf8bf-74c6-568b-a7c5-e82a30da1826', '98a2c864-a484-59b1-bf9e-5295d7a012ad', '7aa0ee64-c39d-5b11-b3f5-7dd4013e580b', 18.9, false, false, null, 'ACTIVO'),
    ('5d2fe11e-e6b8-5c32-970d-e8930d556d18', 'P0006', '7750001000064', 'Losartan 50mg', 'Antihipertensivo', 'MEDICAMENTO', 'e70bb699-1668-5790-b1a0-4b6e65ddb950', '006e90bb-5ad0-5d1f-9cce-9c864fdbbf65', 'a287e391-d791-56e0-9632-f406e56d5948', 22.0, false, true, 'NORMAL', 'ACTIVO'),
    ('b5018120-11e1-59d1-839a-d50f4122ce09', 'P0007', '7750001000071', 'Vitamina C 1g', 'Suplemento vitaminico efervescente', 'OTC', 'a2b098bc-35c4-55a4-9f4a-d6c3d7a164c6', 'ea027606-a3e7-511d-ad67-dfc806ff32f1', '880f1ed2-80a2-5943-b62c-39386b0b7159', 14.5, false, false, null, 'ACTIVO'),
    ('120e971e-060e-518b-91b9-4acff22a00ab', 'P0008', '7750001000088', 'Clonazepam 2mg', 'Ansiolitico, medicamento controlado', 'MEDICAMENTO', '1f4eb2a0-3988-5ec8-b95e-bca7b33f61e3', '61a6948b-d4c8-51c3-b4ef-9151d961aeab', 'a287e391-d791-56e0-9632-f406e56d5948', 9.9, true, true, 'ESPECIAL_RETENIDA', 'ACTIVO'),
    ('e8782adf-f18b-51b7-96ec-6c6f38ddd4dc', 'P0009', '7750001000095', 'Diazepam 10mg', 'Ansiolitico, medicamento controlado', 'MEDICAMENTO', '1f4eb2a0-3988-5ec8-b95e-bca7b33f61e3', '22af14f4-49a3-53f0-9cb2-43b1e0835c5c', 'a287e391-d791-56e0-9632-f406e56d5948', 11.2, true, true, 'ESPECIAL_RETENIDA', 'ACTIVO'),
    ('c387809a-8780-501f-8c81-6d371e9a19bf', 'P0010', '7750001000101', 'Metformina 850mg', 'Antidiabetico oral', 'MEDICAMENTO', 'e70bb699-1668-5790-b1a0-4b6e65ddb950', '1c40a9fd-4cd3-5a27-b5fa-dcacec736c27', 'a287e391-d791-56e0-9632-f406e56d5948', 19.9, false, true, 'NORMAL', 'ACTIVO'),
    ('586a5aa6-3933-5159-987a-8a64762d74d7', 'P0011', '7750001000118', 'Cetirizina 10mg', 'Antihistaminico de segunda generacion', 'OTC', '3cacccde-397e-5935-9d3e-8d9c82a3be21', 'ced1d813-e815-509a-8e67-6ca9c14c032d', 'a287e391-d791-56e0-9632-f406e56d5948', 10.5, false, false, null, 'ACTIVO'),
    ('ce17d1ee-264c-5f12-8c9c-02308fe9fbb1', 'P0012', '7750001000125', 'Salbutamol Inhalador', 'Broncodilatador para crisis asmatica', 'MEDICAMENTO', '575d98c4-e8fd-53b4-84e7-99d8dd254f2c', '5266de0f-285f-55f5-a564-1a81b9a3dcd4', '6bb17e00-dd51-577b-a221-55e03f35f8c7', 32.0, false, true, 'NORMAL', 'SUSPENDIDO');
