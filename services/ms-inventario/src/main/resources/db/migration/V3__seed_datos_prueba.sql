-- Semilla de datos de prueba para ms-inventario.
-- Repartida desde V9__seed_datos_prueba.sql del monolito: cada servicio se lleva
-- exactamente las filas de SUS tablas. Los UUID se conservan tal cual, porque son
-- la unica cosa que sigue uniendo los datos entre bases ahora que no hay FK.

INSERT INTO lotes (id, codigo, producto_id, fecha_vencimiento, cantidad_recibida, cantidad_disponible, costo, local_id, estado) VALUES
    ('ec22db25-d17d-5335-800e-7466c1d0d709', 'L0001', 'a81f4913-8184-5751-9322-c2f7a1807445', '2026-09-11', 60, 60, 8.0, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('ac3f0e20-61ac-5fe7-a78e-32a0d8742d44', 'L0002', 'a81f4913-8184-5751-9322-c2f7a1807445', '2027-09-01', 100, 100, 8.2, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('74ae3fa5-bc22-5e87-b3a3-94d22c9bc66b', 'L0003', '350a61ff-1f06-5f0e-930c-9e8850332b0a', '2027-05-24', 40, 40, 15.0, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('4d47262a-36f2-50ec-8de1-583c76e0d155', 'L0004', '350a61ff-1f06-5f0e-930c-9e8850332b0a', '2026-08-17', 30, 30, 15.5, '3314d935-7880-5e41-8bd6-ddd0985e003c', 'DISPONIBLE'),
    ('4472e3e9-003f-5f21-a3dd-9e90b0235187', 'L0005', 'a203229b-3f3d-51b7-8f7b-beb729aefab0', '2027-02-13', 80, 80, 5.0, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('4c046251-e60a-5e3c-bbc5-31e03fa418cb', 'L0006', '23ceed9c-b0de-5ac7-8e1c-c72633d47266', '2027-04-04', 70, 70, 9.0, '3314d935-7880-5e41-8bd6-ddd0985e003c', 'DISPONIBLE'),
    ('02b6cd8f-a067-586f-a5d4-7e902cbaca30', 'L0007', 'a1b00829-c7df-5dae-89b5-b66712d99d49', '2027-01-24', 50, 50, 11.0, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('1e2d9416-53e8-50c2-8cc8-3c5283a3fa26', 'L0008', '5d2fe11e-e6b8-5c32-970d-e8930d556d18', '2027-03-05', 45, 45, 13.5, 'e0db1b28-2ae0-5e28-a9b9-13c5bb15a77d', 'DISPONIBLE'),
    ('92e31d84-d764-516d-876e-e2531be07666', 'L0009', 'b5018120-11e1-59d1-839a-d50f4122ce09', '2027-05-24', 90, 90, 8.0, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('9ea735d3-41a9-5eb3-b2c3-4d9a59bce0ee', 'L0010', '120e971e-060e-518b-91b9-4acff22a00ab', '2026-09-26', 20, 20, 5.5, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('88f38cc6-2b93-549f-b97f-c6d0c6d522a7', 'L0011', '120e971e-060e-518b-91b9-4acff22a00ab', '2027-12-10', 30, 30, 5.8, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('2af68e15-9190-5583-b5ec-d1c430b6a14f', 'L0012', 'e8782adf-f18b-51b7-96ec-6c6f38ddd4dc', '2027-09-01', 25, 25, 6.2, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('b004c2ae-44d2-51fb-ae8f-4279b1f3c480', 'L0013', 'c387809a-8780-501f-8c81-6d371e9a19bf', '2026-08-12', 40, 40, 10.5, 'eef251fe-7356-56c5-81a9-96ec687e793e', 'DISPONIBLE'),
    ('a53006f3-5e18-56e0-a7af-a48faf0d537e', 'L0014', 'c387809a-8780-501f-8c81-6d371e9a19bf', '2027-05-24', 60, 60, 10.8, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('153299a9-2688-557f-8c0e-e75c28ac30a7', 'L0015', '586a5aa6-3933-5159-987a-8a64762d74d7', '2027-04-14', 55, 55, 6.0, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'DISPONIBLE'),
    ('74974091-96c1-57ab-a82a-8da6f86f2585', 'L0016', 'ce17d1ee-264c-5f12-8c9c-02308fe9fbb1', '2026-11-05', 15, 0, 20.0, 'f99e9244-d5a2-58bb-8757-787813be14f3', 'BLOQUEADO');


-- stock_local se deriva de los lotes recien insertados. Es el contador que usa el
-- UPDATE condicional de las reservas; los lotes siguen siendo la trazabilidad.
INSERT INTO stock_local (producto_id, local_id, disponible, reservado, actualizado_en)
SELECT producto_id, local_id, SUM(cantidad_disponible), 0, now()
  FROM lotes
 WHERE estado = 'DISPONIBLE'
 GROUP BY producto_id, local_id;
