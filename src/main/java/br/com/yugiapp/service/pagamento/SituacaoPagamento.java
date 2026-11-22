package br.com.yugiapp.service.pagamento;

import br.com.yugiapp.enums.StatusPagamentoEnum;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Situação de um pagamento consultada no provedor.
 *
 * @param referencia  external_reference informado na criação, para conferir que o pagamento é o esperado
 * @param aprovadoEm  momento da aprovação; nulo enquanto não aprovado
 */
public record SituacaoPagamento(
        String gatewayId,
        StatusPagamentoEnum status,
        String statusGateway,
        String referencia,
        BigDecimal valor,
        OffsetDateTime aprovadoEm) {
}
