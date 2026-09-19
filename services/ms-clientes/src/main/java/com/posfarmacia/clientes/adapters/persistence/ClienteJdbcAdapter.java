package com.posfarmacia.clientes.adapters.persistence;

import com.posfarmacia.clientes.usecases.port.out.ClientePort;
import com.posfarmacia.contracts.api.ClienteDto;
import com.posfarmacia.contracts.api.CoberturaDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClienteJdbcAdapter implements ClientePort {

    private static final String POR_DNI = """
            SELECT id, dni, nombres, apellidos, estado FROM clientes WHERE dni = ?
            """;

    private static final String POR_ID = """
            SELECT id, dni, nombres, apellidos, estado FROM clientes WHERE id = ?
            """;

    private static final String CONVENIOS = """
            SELECT cs.id, cs.nombre
              FROM afiliaciones_cliente a
              JOIN convenios_seguro cs ON cs.id = a.convenio_id
             WHERE a.cliente_id = ?
               AND a.estado = 'ACTIVA'
               AND cs.activo = true
               AND (a.vigencia_inicio IS NULL OR a.vigencia_inicio <= CURRENT_DATE)
               AND (a.vigencia_fin    IS NULL OR a.vigencia_fin    >= CURRENT_DATE)
            """;

    private static final String COBERTURAS = """
            SELECT convenio_id, producto_id, porcentaje_cubierto
              FROM coberturas_seguro
             WHERE convenio_id = ? AND producto_id = ANY (?)
            """;

    private final JdbcClient jdbc;

    public ClienteJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ClienteDto> porDni(String dni) {
        return jdbc.sql(POR_DNI).param(dni).query(this::armar).optional();
    }

    @Override
    public Optional<ClienteDto> porId(UUID id) {
        return jdbc.sql(POR_ID).param(id).query(this::armar).optional();
    }

    private ClienteDto armar(java.sql.ResultSet rs, int fila) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new ClienteDto(id, rs.getString("dni"), rs.getString("nombres"),
                rs.getString("apellidos"), rs.getString("estado"), convenios(id));
    }

    private List<ClienteDto.ConvenioVigente> convenios(UUID clienteId) {
        return jdbc.sql(CONVENIOS).param(clienteId)
                .query((rs, i) -> new ClienteDto.ConvenioVigente(
                        rs.getObject("id", UUID.class), rs.getString("nombre")))
                .list();
    }

    @Override
    public List<CoberturaDto> coberturas(UUID convenioId, List<UUID> productoIds) {
        if (productoIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(COBERTURAS)
                .param(convenioId).param(productoIds.toArray(UUID[]::new))
                .query((rs, i) -> new CoberturaDto(
                        rs.getObject("convenio_id", UUID.class),
                        rs.getObject("producto_id", UUID.class),
                        rs.getBigDecimal("porcentaje_cubierto")))
                .list();
    }
}
