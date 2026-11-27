package br.com.yugiapp.service.pagamento;

import java.math.BigDecimal;

/**
 * Pagamento à vista com cartão de crédito ou débito. O cartão chega como token, gerado no app pelo formulário do provedor: o
 * número, a validade e o código de segurança nunca passam pela API.
 *
 * @param token          token do cartão (uso único)
 * @param bandeira       payment_method_id informado pelo formulário (ex.: visa, master)
 * @param emissorId      issuer_id informado pelo formulário (pode ser nulo)
 * @param tipoDocumento  tipo do documento do titular (ex.: CPF)
 * @param idempotencyKey chave que impede cobranças duplicadas em caso de nova tentativa
 */
public record SolicitacaoPagamentoCartao(
        String referencia,
        BigDecimal valor,
        String descricao,
        String token,
        String bandeira,
        String emissorId,
        String emailPagador,
        String tipoDocumento,
        String documento,
        String idempotencyKey) {
}
