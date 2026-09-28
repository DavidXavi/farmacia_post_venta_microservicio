package com.posfarmacia.plataforma.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;

import com.posfarmacia.contracts.Topicos;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class KafkaConfigTest {

    @Test
    void unEventoQueAgotaLosReintentosVaALaColaMuertaDeSuTopico() {
        var registro = new ConsumerRecord<>(Topicos.VENTAS_CONFIRMADAS, 7, 42L, "local-1", "{}");

        var destino = KafkaConfig.destinoDlq(registro, new IllegalStateException("base caida"));

        assertThat(destino.topic()).isEqualTo("pos.ventas.confirmadas.dlq");
        assertThat(destino.partition()).isEqualTo(-1);
    }

    @Test
    void lasVentasSeRepartenEnDoceParticionesYCadaTopicoTieneSuColaMuerta() {
        var topicos = KafkaConfig.listaTopicos();

        var ventas = topicos.stream()
                .filter(t -> t.name().equals(Topicos.VENTAS_CONFIRMADAS)).findFirst().orElseThrow();
        assertThat(ventas.numPartitions()).isEqualTo(12);

        assertThat(topicos).extracting(NewTopic::name)
                .contains(Topicos.VENTAS_CONFIRMADAS + Topicos.DLQ, Topicos.AUDITORIA + Topicos.DLQ);
        assertThat(topicos).hasSize(KafkaConfig.PARTICIONES.size() * 2);
    }
}
