package com.posfarmacia.promociones.adapters.persistence;

import com.posfarmacia.promociones.domain.ReglaPromocion;
import com.posfarmacia.promociones.usecases.port.out.PromocionPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PromocionJdbcAdapter implements PromocionPort {

    /**
     * Una sola consulta con join, no una por promocion.
     *
     * <p>Traer las promociones y despues sus condiciones de a una seria el N+1 clasico:
     * con veinte promociones vigentes son veintiuna consultas por venta. El join las
     * deja en una y el agrupado se hace en memoria, que para esta cardinalidad es gratis.
     */
    private static final String VIGENTES = """
            SELECT p.id, p.nombre, p.tipo_beneficio, p.valor_beneficio, p.requiere_cliente,
                   p.cantidad_minima, p.vigencia_inicio, p.vigencia_fin, p.activa,
                   c.producto_id
              FROM promociones p
              JOIN promocion_condiciones c ON c.promocion_id = p.id
             WHERE p.activa = true
               AND (p.vigencia_inicio IS NULL OR p.vigencia_inicio <= CURRENT_DATE)
               AND (p.vigencia_fin    IS NULL OR p.vigencia_fin    >= CURRENT_DATE)
               AND c.producto_id = ANY (?)
            """;

    private final JdbcClient jdbc;

    public PromocionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Fila(UUID id, String nombre, String tipoBeneficio,
                        java.math.BigDecimal valorBeneficio, boolean requiereCliente,
                        int cantidadMinima, java.time.LocalDate vigenciaInicio,
                        java.time.LocalDate vigenciaFin, boolean activa, UUID productoId) {
    }

    @Override
    public List<ReglaPromocion> vigentesPara(List<UUID> productoIds) {
        if (productoIds.isEmpty()) {
            return List.of();
        }

        List<Fila> filas = jdbc.sql(VIGENTES)
                .param(productoIds.toArray(UUID[]::new))
                .query((rs, i) -> new Fila(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombre"),
                        rs.getString("tipo_beneficio"),
                        rs.getBigDecimal("valor_beneficio"),
                        rs.getBoolean("requiere_cliente"),
                        rs.getInt("cantidad_minima"),
                        rs.getObject("vigencia_inicio", java.time.LocalDate.class),
                        rs.getObject("vigencia_fin", java.time.LocalDate.class),
                        rs.getBoolean("activa"),
                        rs.getObject("producto_id", UUID.class)))
                .list();

        Map<UUID, List<UUID>> productosPorPromocion = new LinkedHashMap<>();
        Map<UUID, Fila> cabeceras = new LinkedHashMap<>();
        for (Fila f : filas) {
            cabeceras.putIfAbsent(f.id(), f);
            productosPorPromocion.computeIfAbsent(f.id(), k -> new ArrayList<>()).add(f.productoId());
        }

        return cabeceras.values().stream()
                .map(f -> new ReglaPromocion(f.id(), f.nombre(), f.tipoBeneficio(),
                        f.valorBeneficio(), f.requiereCliente(), f.cantidadMinima(),
                        f.vigenciaInicio(), f.vigenciaFin(), f.activa(),
                        productosPorPromocion.get(f.id())))
                .toList();
    }
}
