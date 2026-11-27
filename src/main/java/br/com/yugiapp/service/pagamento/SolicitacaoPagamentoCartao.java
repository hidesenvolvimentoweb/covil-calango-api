package br.com.yugiapp.service.pagamento;

import java.math.BigDecimal;

/**
 * Pagamento à vista com cartão de crédito ou débito. O cartão chega como token, gerado no app pelo formulário do provedor: o
 * número, a validade e o código de segurança nunca passam pela API.
 *
 * @param token          token do cartão (uso único); com cartão salvo, gerado do cartão salvo e do código de segurança
 * @param bandeira       payment_method_id informado pelo formulário (ex.: visa, master)
 * @param emissorId      issuer_id informado pelo formulário (pode ser nulo)
 * @param emailPagador   e-mail do titular; nulo quando o pagador vai como cliente salvo no provedor
 * @param tipoDocumento  tipo do documento do titular (ex.: CPF); nulo com cartão salvo
 * @param idempotencyKey chave que impede cobranças duplicadas em caso de nova tentativa
 * @param clienteId      cliente no provedor com os cartões salvos do aparelho; nulo se o aparelho não salvou cartões
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
        String idempotencyKey,
        String clienteId) {

    public SolicitacaoPagamentoCartao(String referencia, BigDecimal valor, String descricao, String token,
                                      String bandeira, String emissorId, String emailPagador, String tipoDocumento,
                                      String documento, String idempotencyKey) {
        this(referencia, valor, descricao, token, bandeira, emissorId, emailPagador, tipoDocumento, documento,
                idempotencyKey, null);
    }
}
