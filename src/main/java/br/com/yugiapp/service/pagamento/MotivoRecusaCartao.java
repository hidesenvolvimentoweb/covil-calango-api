package br.com.yugiapp.service.pagamento;

import java.util.Map;

/**
 * Mensagens para o cliente a partir do motivo da recusa do cartão (status_detail do Mercado Pago).
 *
 * @see <a href="https://www.mercadopago.com.br/developers/pt/docs/checkout-api/response-handling/collection-results">Respostas de pagamento</a>
 */
public final class MotivoRecusaCartao {

    /** O provedor recusou a requisição antes de cobrar (ex.: token do cartão inválido ou expirado). */
    public static final String DADOS_INVALIDOS = "dados_invalidos";

    private static final String PADRAO = "O pagamento foi recusado. Tente outro cartão ou pague com PIX.";

    private static final Map<String, String> MENSAGENS = Map.ofEntries(
            Map.entry(DADOS_INVALIDOS, "Não foi possível processar o cartão. Confira os dados e tente novamente."),
            Map.entry("cc_rejected_insufficient_amount", "Saldo ou limite insuficiente no cartão."),
            Map.entry("cc_rejected_bad_filled_security_code", "O código de segurança está incorreto."),
            Map.entry("cc_rejected_bad_filled_date", "A data de validade está incorreta."),
            Map.entry("cc_rejected_bad_filled_card_number", "Confira o número do cartão."),
            Map.entry("cc_rejected_bad_filled_other", "Confira os dados do cartão."),
            Map.entry("cc_rejected_call_for_authorize",
                    "O banco precisa autorizar este pagamento. Fale com o banco e tente de novo."),
            Map.entry("cc_rejected_card_disabled", "O cartão está bloqueado ou não foi ativado. Fale com o banco."),
            Map.entry("cc_rejected_duplicated_payment",
                    "Já existe um pagamento igual a este. Use outro cartão ou outra forma de pagamento."),
            Map.entry("cc_rejected_high_risk", "O pagamento foi recusado pela análise de segurança. Use outro cartão ou pague com PIX."),
            Map.entry("cc_rejected_blacklist", "O pagamento foi recusado pela análise de segurança. Use outro cartão ou pague com PIX."),
            Map.entry("cc_rejected_max_attempts", "Limite de tentativas atingido. Use outro cartão."),
            Map.entry("cc_rejected_3ds_challenge", "A verificação do banco não foi concluída. Tente novamente."),
            Map.entry("cc_rejected_3ds_mandatory", "O banco exige uma verificação que não foi concluída. Tente outro cartão."),
            Map.entry("cc_amount_rate_limit_exceeded", "O valor passou do limite do cartão. Use outro cartão."));

    private MotivoRecusaCartao() {
    }

    public static String mensagem(String motivo) {
        return motivo == null ? PADRAO : MENSAGENS.getOrDefault(motivo, PADRAO);
    }
}
