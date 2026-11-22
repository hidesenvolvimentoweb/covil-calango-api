package br.com.yugiapp.service.pagamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Dados para criar uma cobrança PIX no provedor.
 *
 * @param referencia      identificador do pagamento na API (external_reference)
 * @param idempotencyKey  chave que impede cobranças duplicadas em caso de nova tentativa
 */
public record SolicitacaoCobrancaPix(
        String referencia,
        BigDecimal valor,
        String descricao,
        String nomePagador,
        String emailPagador,
        OffsetDateTime expiraEm,
        String idempotencyKey) {
}
