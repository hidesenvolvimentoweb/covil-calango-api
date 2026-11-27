package br.com.yugiapp.dto;

import br.com.yugiapp.service.pagamento.CartaoSalvo;

import java.util.List;

/**
 * Cartões salvos no celular do cliente, no formato que o formulário de cartão do app recebe.
 *
 * @param clienteId id do cliente no provedor (o formulário do Mercado Pago o exige para mostrar os cartões); nulo se
 *                  o aparelho ainda não salvou cartões
 */
public record CartoesSalvosDTO(String clienteId, List<CartaoSalvo> cartoes) {
}
