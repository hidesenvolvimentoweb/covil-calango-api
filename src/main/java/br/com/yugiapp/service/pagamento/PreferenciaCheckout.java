package br.com.yugiapp.service.pagamento;

/**
 * Preferência do Checkout Pro criada no provedor.
 *
 * @param url endereço que o app abre para o cliente pagar (init_point)
 */
public record PreferenciaCheckout(String id, String url) {
}
