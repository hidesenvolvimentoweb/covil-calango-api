package br.com.yugiapp.dto;

/**
 * O que o app pode oferecer na hora de pagar a comanda.
 *
 * @param publicKey chave pública do Mercado Pago, usada pelo formulário de cartão no app (não é segredo)
 */
public record PagamentoConfiguracaoDTO(boolean pix, boolean cartao, boolean mercadoPago, String publicKey) {
}
