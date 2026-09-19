-- Semilla de datos de prueba para ms-clientes.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO clientes (id, dni, nombres, apellidos, fecha_nacimiento, telefono, correo, direccion, estado) VALUES
    ('5730402c-8a8a-5313-9342-3d3c323841bb', '45678912', 'Juan Carlos', 'Perez Gomez', '1985-03-14', '987654321', 'jperez@example.com', 'Jr. Union 100, Lima', 'ACTIVO'),
    ('a6552978-7e8c-51d4-8b74-5a6718fe3987', '41234567', 'Maria Fernanda', 'Lopez Torres', '1990-07-22', '987654322', 'mlopez@example.com', 'Av. Arequipa 200, Lima', 'ACTIVO'),
    ('9a9fdbb4-5788-533d-9f61-70009063cabb', '42345678', 'Carlos Alberto', 'Ramirez Silva', '1978-11-05', '987654323', 'cramirez@example.com', 'Jr. Cusco 300, Lima', 'ACTIVO'),
    ('64edcc36-30ab-5369-973e-5ff70f94bd2f', '43456789', 'Ana Lucia', 'Fernandez Vega', '1995-01-30', '987654324', 'afernandez@example.com', 'Av. Brasil 400, Lima', 'ACTIVO'),
    ('770d8af1-6a03-5d45-9c2d-931bf5016044', '44567890', 'Luis Miguel', 'Torres Castro', '1982-09-18', '987654325', 'ltorres@example.com', 'Jr. Ica 500, Lima', 'ACTIVO'),
    ('6bc54e9a-6cc2-54a8-ac3f-428a6c4750d4', '46789123', 'Rosa Elena', 'Vasquez Mendoza', '1970-05-02', '987654326', 'rvasquez@example.com', 'Av. Salaverry 600, Lima', 'ACTIVO'),
    ('6a29a8bf-b7ff-55f4-aab6-d69ff8a7d243', '47891234', 'Jorge Luis', 'Huaman Quispe', '1988-12-25', '987654327', 'jhuaman@example.com', 'Jr. Puno 700, Lima', 'ACTIVO'),
    ('45b69675-01ec-554f-a046-52a380b10995', '48912345', 'Patricia Isabel', 'Rojas Diaz', '1993-04-11', '987654328', 'projas@example.com', 'Av. Colonial 800, Lima', 'ACTIVO'),
    ('acf795dc-15d3-52ce-b5ab-e81f6fbe50df', '49123456', 'Miguel Angel', 'Sanchez Flores', '1965-08-08', '987654329', 'msanchez@example.com', 'Jr. Tacna 900, Lima', 'INACTIVO'),
    ('8a0c1d3f-b9d3-5879-90df-e0b90d5bfc3e', '40123456', 'Carmen Rosa', 'Chavez Paredes', '1975-02-19', '987654330', 'cchavez@example.com', 'Av. Grau 1000, Lima', 'INACTIVO');

INSERT INTO convenios_seguro (id, nombre, activo) VALUES
    ('812a400b-b984-514a-b4a0-041d0ec0d0d1', 'Rimac Salud', true),
    ('ee4d11a9-f8b0-5eb9-9d37-bd8e8b7262db', 'Pacifico Seguros', true),
    ('70594cb0-7bda-5460-8450-389b0e31dee5', 'Mapfre Salud', true),
    ('60b77870-3c7d-5185-88c6-e04c2980d0ae', 'La Positiva Salud', true),
    ('b382c530-cd23-55f0-8f2d-7ec65fdc0c24', 'Sanitas Peru', true),
    ('610a6e70-ec40-574f-bab7-227cb77e053c', 'EsSalud Convenio', true),
    ('8a019b0d-88b6-5496-872a-fbef56dfdc06', 'Interseguro Salud', true),
    ('f2ad1e50-4e9f-5ded-8bcb-903f7999a886', 'Protecta Salud', false),
    ('8d4d3eb5-8366-54cb-bf40-d427796bd7a9', 'Sanna Convenio', true),
    ('d68c726a-64e0-5b83-8d2c-e2accaf4641e', 'Auna Salud', false);

INSERT INTO coberturas_seguro (id, convenio_id, producto_id, porcentaje_cubierto) VALUES
    ('5498bbaa-128d-58cb-959f-bb2ba6494b0e', '812a400b-b984-514a-b4a0-041d0ec0d0d1', '350a61ff-1f06-5f0e-930c-9e8850332b0a', 70.0),
    ('aafa02d4-7828-5e48-9587-72a7e5e1c6dd', '812a400b-b984-514a-b4a0-041d0ec0d0d1', '5d2fe11e-e6b8-5c32-970d-e8930d556d18', 60.0),
    ('7fb9547b-e782-5f66-83d1-4cf97c74d8a8', 'ee4d11a9-f8b0-5eb9-9d37-bd8e8b7262db', '5d2fe11e-e6b8-5c32-970d-e8930d556d18', 80.0),
    ('eada7056-a414-532e-b393-2de7256e6230', 'ee4d11a9-f8b0-5eb9-9d37-bd8e8b7262db', 'c387809a-8780-501f-8c81-6d371e9a19bf', 65.0),
    ('72c64713-497a-5535-b8ea-5d85b3252508', '70594cb0-7bda-5460-8450-389b0e31dee5', 'a81f4913-8184-5751-9322-c2f7a1807445', 50.0),
    ('3e64b8da-59c9-506a-b727-5ccf027f3f18', '60b77870-3c7d-5185-88c6-e04c2980d0ae', '23ceed9c-b0de-5ac7-8e1c-c72633d47266', 55.0),
    ('f59fec31-3db1-52c3-804a-90e174d18257', 'b382c530-cd23-55f0-8f2d-7ec65fdc0c24', 'a1b00829-c7df-5dae-89b5-b66712d99d49', 60.0),
    ('990a4026-d674-5e90-811c-e08cd79d3a50', '610a6e70-ec40-574f-bab7-227cb77e053c', 'b5018120-11e1-59d1-839a-d50f4122ce09', 40.0),
    ('7310d800-c439-5d17-96c3-f225ef8c453d', '8a019b0d-88b6-5496-872a-fbef56dfdc06', '586a5aa6-3933-5159-987a-8a64762d74d7', 45.0),
    ('e759397f-ed8c-5213-b394-d0d81cc8ee1d', '8d4d3eb5-8366-54cb-bf40-d427796bd7a9', 'c387809a-8780-501f-8c81-6d371e9a19bf', 75.0);

INSERT INTO afiliaciones_cliente (id, cliente_id, convenio_id, vigencia_inicio, vigencia_fin, estado) VALUES
    ('fc2db497-1408-51a1-9cb2-97e3d0e032c8', '5730402c-8a8a-5313-9342-3d3c323841bb', '812a400b-b984-514a-b4a0-041d0ec0d0d1', '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('a0cf7e23-a73d-500b-9973-d5d9551f562a', 'a6552978-7e8c-51d4-8b74-5a6718fe3987', 'ee4d11a9-f8b0-5eb9-9d37-bd8e8b7262db', '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('83545160-afb5-5aeb-8803-e4f9356f5b39', '9a9fdbb4-5788-533d-9f61-70009063cabb', '70594cb0-7bda-5460-8450-389b0e31dee5', '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('8782f00a-62ea-5097-8ad4-2fb53a91e502', '64edcc36-30ab-5369-973e-5ff70f94bd2f', '60b77870-3c7d-5185-88c6-e04c2980d0ae', '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('61fae9ab-03eb-5f35-b786-7c1b6311f4f2', '770d8af1-6a03-5d45-9c2d-931bf5016044', 'b382c530-cd23-55f0-8f2d-7ec65fdc0c24', '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('b48f99d9-1208-5c17-a018-a1e91540e13e', '6bc54e9a-6cc2-54a8-ac3f-428a6c4750d4', '610a6e70-ec40-574f-bab7-227cb77e053c', '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('2295c791-ba31-5fd2-9587-7f6d7e5876ea', '6a29a8bf-b7ff-55f4-aab6-d69ff8a7d243', '8a019b0d-88b6-5496-872a-fbef56dfdc06', '2026-01-09', '2027-05-24', 'SUSPENDIDA'),
    ('dbf76d4d-29a6-530d-9925-0002a9baec21', '45b69675-01ec-554f-a046-52a380b10995', 'f2ad1e50-4e9f-5ded-8bcb-903f7999a886', '2026-01-09', '2027-05-24', 'ACTIVA'),
    ('7d017d43-53bb-53d8-8052-c3dc527a4213', 'acf795dc-15d3-52ce-b5ab-e81f6fbe50df', '8d4d3eb5-8366-54cb-bf40-d427796bd7a9', '2026-01-09', '2027-05-24', 'INACTIVA'),
    ('98000948-85b1-53b5-9984-d7bb94254f0f', '8a0c1d3f-b9d3-5879-90df-e0b90d5bfc3e', 'd68c726a-64e0-5b83-8d2c-e2accaf4641e', '2026-01-09', '2027-05-24', 'INACTIVA');

INSERT INTO recetas (id, numero, tipo, fecha_emision, fecha_vencimiento, producto_id, cliente_id, datos_paciente, datos_profesional, dosis, cantidad_autorizada, archivo_respaldo_url, estado, retenida_en_botica, version) VALUES
    ('66d4284a-1a11-5d7e-ae9a-035ed5295ff5', 'REC-0001', 'NORMAL', '2026-07-23', '2026-08-22', '350a61ff-1f06-5f0e-930c-9e8850332b0a', '5730402c-8a8a-5313-9342-3d3c323841bb', 'Paciente atendido en consulta REC-0001', 'Dr. Fernando Castillo Rios - CMP 45612', '500mg cada 8 horas por 7 dias', 21, null, 'APROBADA', false, 0),
    ('21ac9c25-b130-5041-a056-2af940812823', 'REC-0002', 'NORMAL', '2026-07-25', '2026-08-24', '5d2fe11e-e6b8-5c32-970d-e8930d556d18', '9a9fdbb4-5788-533d-9f61-70009063cabb', 'Paciente atendido en consulta REC-0002', 'Dr. Fernando Castillo Rios - CMP 45612', '50mg cada 24 horas', 30, null, 'APROBADA', false, 0),
    ('fb19a897-24be-5fde-bb2a-44db107a8c17', 'REC-0003', 'NORMAL', '2026-07-27', '2026-08-26', 'c387809a-8780-501f-8c81-6d371e9a19bf', '6a29a8bf-b7ff-55f4-aab6-d69ff8a7d243', 'Paciente atendido en consulta REC-0003', 'Dr. Fernando Castillo Rios - CMP 45612', '850mg cada 12 horas', 60, null, 'APROBADA', false, 0),
    ('a68a1f1f-e681-52b7-a345-8d8151d09b02', 'REC-0004', 'ESPECIAL', '2026-07-18', '2026-07-27', 'ce17d1ee-264c-5f12-8c9c-02308fe9fbb1', '64edcc36-30ab-5369-973e-5ff70f94bd2f', 'Paciente atendido en consulta REC-0004', 'Dr. Fernando Castillo Rios - CMP 45612', '2 inhalaciones cada 6 horas segun necesidad', 1, null, 'APROBADA', false, 0),
    ('4a8f9f8b-a69a-576b-8fdf-889964799b3c', 'REC-0005', 'ESPECIAL_RETENIDA', '2026-07-20', '2026-08-19', '120e971e-060e-518b-91b9-4acff22a00ab', '64edcc36-30ab-5369-973e-5ff70f94bd2f', 'Paciente atendido en consulta REC-0005', 'Dr. Fernando Castillo Rios - CMP 45612', '2mg cada 12 horas por 15 dias', 30, null, 'UTILIZADA', true, 0),
    ('67b3ca86-3ebd-56ea-a250-95dafe2a86a4', 'REC-0006', 'ESPECIAL_RETENIDA', '2026-07-22', '2026-08-21', 'e8782adf-f18b-51b7-96ec-6c6f38ddd4dc', '45b69675-01ec-554f-a046-52a380b10995', 'Paciente atendido en consulta REC-0006', 'Dr. Fernando Castillo Rios - CMP 45612', '10mg cada 24 horas por 10 dias', 10, null, 'UTILIZADA', true, 0),
    ('0966a998-9582-5247-9da6-78b132aeb645', 'REC-0007', 'ESPECIAL_RETENIDA', '2026-07-26', '2026-08-25', '120e971e-060e-518b-91b9-4acff22a00ab', null, 'Paciente atendido en consulta REC-0007', 'Dr. Fernando Castillo Rios - CMP 45612', '2mg cada 12 horas', 20, null, 'PENDIENTE', false, 0),
    ('346d3683-9e6e-5c93-8ea0-648b9d5870cc', 'REC-0008', 'NORMAL', '2026-07-13', '2026-08-12', '350a61ff-1f06-5f0e-930c-9e8850332b0a', null, 'Paciente atendido en consulta REC-0008', 'Dr. Fernando Castillo Rios - CMP 45612', '500mg cada 8 horas', 21, null, 'RECHAZADA', false, 0),
    ('756b4df5-5531-5831-bac9-360de6b66136', 'REC-0009', 'NORMAL', '2026-07-24', '2026-08-23', '5d2fe11e-e6b8-5c32-970d-e8930d556d18', '6bc54e9a-6cc2-54a8-ac3f-428a6c4750d4', 'Paciente atendido en consulta REC-0009', 'Dr. Fernando Castillo Rios - CMP 45612', '50mg cada 24 horas por 30 dias', 30, null, 'APROBADA', false, 0),
    ('b3111fa0-832c-5d67-9252-1185f051f594', 'REC-0010', 'ESPECIAL', '2026-07-27', '2026-08-27', 'c387809a-8780-501f-8c81-6d371e9a19bf', 'a6552978-7e8c-51d4-8b74-5a6718fe3987', 'Paciente atendido en consulta REC-0010', 'Dr. Fernando Castillo Rios - CMP 45612', '850mg cada 12 horas por 60 dias', 60, null, 'PENDIENTE', false, 0);
