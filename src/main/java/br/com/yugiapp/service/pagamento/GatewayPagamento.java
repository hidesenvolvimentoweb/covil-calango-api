package br.com.yugiapp.service.pagamento;

/**
 * Provedor de pagamentos (PSP). Isola o restante da API do provedor escolhido: trocar de provedor ou adicionar
 * cartão de crédito significa implementar/estender esta interface, sem mudar regras de comanda e pagamento.
 */
public interface GatewayPagamento {

    /** Identificador gravado em {@code TB_PAGAMENTO.GATEWAY}. */
    String nome();

    /** Indica se as credenciais do provedor foram configuradas. */
    boolean isConfigurado();

    /** Cria uma cobrança PIX (QR Code dinâmico) com valor e expiração. */
    CobrancaPix criarCobrancaPix(SolicitacaoCobrancaPix solicitacao);

    /** Consulta a situação atual do pagamento diretamente no provedor (fonte da verdade). */
    SituacaoPagamento consultar(String gatewayId);

    /** Cancela uma cobrança ainda não paga. */
    void cancelar(String gatewayId);
}
