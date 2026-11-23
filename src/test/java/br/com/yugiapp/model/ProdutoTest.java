package br.com.yugiapp.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** O cardápio do app devolve a entidade: o estoque não pode aparecer no JSON, só o limite por pedido. */
    @Test
    void jsonDoCardapioNaoExpoeOEstoque() throws Exception {
        JsonNode json = objectMapper.valueToTree(Produto.builder().id(1L).nome("Água").estoque(7).build());

        assertThat(json.has("estoque")).isFalse();
        assertThat(json.get("quantidadeMaximaPedido").asLong()).isEqualTo(7);
    }

    @Test
    void quantidadeMaximaPedidoEnviadaPeloClienteEIgnorada() throws Exception {
        Produto produto = objectMapper.readValue("{\"id\":1,\"estoque\":50,\"quantidadeMaximaPedido\":99}", Produto.class);

        assertThat(produto.getEstoque()).isNull();
        assertThat(produto.getQuantidadeMaximaPedido()).isEqualTo(Produto.QUANTIDADE_MAXIMA_POR_PEDIDO);
    }
}
