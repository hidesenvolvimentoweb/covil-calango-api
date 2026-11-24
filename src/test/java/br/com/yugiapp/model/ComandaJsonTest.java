package br.com.yugiapp.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** O app exibe comanda.usuario.nome ("Olá, Fulano!"), recebida pelo WebSocket como a entidade em JSON. */
class ComandaJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private JsonNode json(Comanda comanda) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(comanda));
    }

    @Test
    void comandaDeVisitanteLevaONomeDoClienteEmUsuario() throws Exception {
        JsonNode json = json(Comanda.builder().numero("20261124-1").nomeCliente("Yugi").build());

        assertThat(json.path("usuario").path("nome").asText()).isEqualTo("Yugi");
        assertThat(json.path("nomeCliente").asText()).isEqualTo("Yugi");
    }

    @Test
    void comandaAntigaDoUsuarioGenericoUsaONomeInformado() throws Exception {
        Usuario visitante = Usuario.builder().id(1L).nome("Visitante").email("segredo@exemplo.com").build();
        JsonNode json = json(Comanda.builder().usuario(visitante).nomeCliente("Kaiba").build());

        assertThat(json.path("usuario").path("nome").asText()).isEqualTo("Kaiba");
        assertThat(json.path("usuario").path("id").asLong()).isEqualTo(1L);
        assertThat(json.toString()).doesNotContain("segredo@exemplo.com");
    }

    @Test
    void semNomeOUsuarioFicaNulo() throws Exception {
        assertThat(json(Comanda.builder().numero("1").build()).path("usuario").isNull()).isTrue();
    }
}
