-- Los nombres del read model son copias para mostrar, no claves.
--
-- Estaban como NOT NULL y eso invertia la prioridad: si ms-ventas no lograba resolver
-- el nombre del local (porque identidad estaba lento, o porque el dato aun no viaja en
-- el evento), la venta entera se caia del read model y el consumidor entraba en bucle
-- de reintentos hasta la cola muerta.
--
-- Perder una venta del tablero por un nombre para mostrar es exactamente al reves de
-- lo que el sistema promete. Las claves (local_id, usuario_id, cliente_id) siguen
-- siendo NOT NULL donde corresponde: esas si son el dato.
ALTER TABLE rm_ventas ALTER COLUMN local_nombre   DROP NOT NULL;
ALTER TABLE rm_ventas ALTER COLUMN usuario_nombre DROP NOT NULL;

ALTER TABLE rm_venta_lineas ALTER COLUMN producto_nombre DROP NOT NULL;
