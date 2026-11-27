package br.com.yugiapp.service.pagamento;

import br.com.yugiapp.enums.StatusPagamentoEnum;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Situação de um pagamento consultada no provedor.
 *
 * @param referencia    external_reference informado na criação, para conferir que o pagamento é o esperado
 * @param aprovadoEm    momento da aprovação; nulo enquanto não aprovado
 * @param statusDetalhe detalhe do status (ex.: cc_rejected_insufficient_amount, pending_challenge)
 * @param bandeira      bandeira do cartão (payment_method_id), quando pago com cartão
 * @param cartaoFinal   últimos 4 dígitos do cartão, quando pago com cartão
 * @param desafioUrl    endereço da verificação do banco (3DS), quando ela está pendente
 * @param desafioCreq   dados enviados ao endereço da verificação do banco (3DS)
 * @param tipoCartao    tipo do cartão (payment_type_id: credit_card ou debit_card), quando pago com cartão
 */
public record SituacaoPagamento(
        String gatewayId,
        StatusPagamentoEnum status,
        String statusGateway,
        String referencia,
        BigDecimal valor,
        OffsetDateTime aprovadoEm,
        String statusDetalhe,
        String bandeira,
        String cartaoFinal,
        String desafioUrl,
        String desafioCreq,
        String tipoCartao) {

    /** Situação de um pagamento sem cartão (ex.: PIX). */
    public SituacaoPagamento(String gatewayId, StatusPagamentoEnum status, String statusGateway, String referencia,
                             BigDecimal valor, OffsetDateTime aprovadoEm) {
        this(gatewayId, status, statusGateway, referencia, valor, aprovadoEm, null, null, null, null, null, null);
    }
}
