-- Anular una venta fallaba con 500 y la saga de compensacion nunca arrancaba.
--
-- Causa: VentaJdbcAdapter.guardar() borra las lineas y las reinserta con los MISMOS
-- ids en vez de reconciliar altas y bajas. Antes de confirmar eso no molesta, pero
-- cuando ms-inventario ya asigno los lotes, detalle_venta_lotes apunta a esas lineas
-- y el DELETE choca contra la clave foranea a mitad de la transaccion.
--
-- La restriccion se difiere al commit: el borrado y la reinsercion ocurren dentro de
-- la misma transaccion (todos los casos de uso que guardan son @Transactional), asi
-- que al final del commit las filas referenciadas estan de vuelta con su id intacto.
--
-- Se difiere en lugar de poner ON DELETE CASCADE: el cascade borraria la trazabilidad
-- de que lote salio en cada linea en cada guardado posterior a la confirmacion, y eso
-- es justo lo que hay que conservar para reimprimir un comprobante.
ALTER TABLE detalle_venta_lotes
    DROP CONSTRAINT detalle_venta_lotes_detalle_venta_id_fkey;

ALTER TABLE detalle_venta_lotes
    ADD CONSTRAINT detalle_venta_lotes_detalle_venta_id_fkey
        FOREIGN KEY (detalle_venta_id) REFERENCES detalles_venta (id)
        DEFERRABLE INITIALLY DEFERRED;
