package br.com.yugiapp.service.pagamento;

/**
 * Cartão salvo no provedor de pagamentos. Só dados para o cliente reconhecer o cartão: o número completo e o código
 * de segurança ficam no provedor (o código nem lá: é pedido de novo a cada compra).
 *
 * @param id           id do cartão no provedor
 * @param bandeira     payment_method_id (ex.: visa, master)
 * @param nomeBandeira nome para exibir (ex.: Visa)
 * @param tipo         credit_card ou debit_card
 * @param finalCartao  quatro últimos dígitos
 */
public record CartaoSalvo(
        String id,
        String bandeira,
        String nomeBandeira,
        String tipo,
        String finalCartao,
        Integer mesValidade,
        Integer anoValidade) {
}
