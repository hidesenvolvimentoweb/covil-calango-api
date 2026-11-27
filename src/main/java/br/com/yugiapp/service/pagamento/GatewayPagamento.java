package br.com.yugiapp.service.pagamento;

import java.util.Optional;

/**
 * Provedor de pagamentos (PSP). Isola o restante da API do provedor escolhido: trocar de provedor significa
 * implementar esta interface, sem mudar regras de comanda e pagamento.
 */
public interface GatewayPagamento {

    /** Identificador gravado em {@code TB_PAGAMENTO.GATEWAY}. */
    String nome();

    /** Indica se as credenciais do provedor foram configuradas. */
    boolean isConfigurado();

    /** Cria uma cobrança PIX (QR Code dinâmico) com valor e expiração. */
    CobrancaPix criarCobrancaPix(SolicitacaoCobrancaPix solicitacao);

    /**
     * Cobra à vista no cartão de crédito ou débito (token gerado no app). A resposta já traz o resultado: aprovado, recusado,
     * em análise ou aguardando a verificação do banco (3DS).
     */
    SituacaoPagamento pagarComCartao(SolicitacaoPagamentoCartao solicitacao);

    /** Cria a preferência do Checkout Pro para o cliente pagar na própria conta do provedor. */
    PreferenciaCheckout criarPreferencia(SolicitacaoPreferencia solicitacao);

    /** Encerra uma preferência: o link deixa de aceitar pagamentos. */
    void expirarPreferencia(String preferenciaId);

    /**
     * Pagamento criado no provedor com a referência informada, quando a API ainda não sabe o id dele (Checkout Pro ou
     * resposta perdida ao cobrar o cartão). Havendo vários, prefere o aprovado; senão, o mais recente.
     */
    Optional<SituacaoPagamento> buscarPorReferencia(String referencia);

    /** Consulta a situação atual do pagamento diretamente no provedor (fonte da verdade). */
    SituacaoPagamento consultar(String gatewayId);

    /** Cancela uma cobrança ainda não paga. */
    void cancelar(String gatewayId);

    /** Devolve ao cliente o valor total de um pagamento aprovado. */
    void estornar(String gatewayId, String idempotencyKey);
}
