package br.com.yugiapp.service;

import br.com.yugiapp.dto.CartoesSalvosDTO;
import br.com.yugiapp.model.CartaoAparelho;
import br.com.yugiapp.repository.CartaoAparelhoRepository;
import br.com.yugiapp.service.pagamento.CartaoSalvo;
import br.com.yugiapp.service.pagamento.GatewayPagamento;
import br.com.yugiapp.service.pagamento.GatewayPagamentoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cartões salvos no celular do cliente (sem login), identificados pela chave do aparelho. */
class CartaoSalvoServiceTest {

    private static final String CHAVE = "chave-do-aparelho-0123456789abcdef0123456789";
    private static final CartaoSalvo VISA = new CartaoSalvo("card-1", "visa", "Visa", "credit_card", "5682", 11, 2030);

    private final CartaoAparelhoRepository repository = mock(CartaoAparelhoRepository.class);
    private final GatewayPagamento gateway = mock(GatewayPagamento.class);
    private CartaoSalvoService service;

    @BeforeEach
    void configurar() {
        service = new CartaoSalvoService(repository, gateway, "test_payer_{id}@testuser.com");
        when(gateway.nome()).thenReturn("MERCADO_PAGO");
        when(repository.findByChaveHashAndGateway(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(repository.saveAndFlush(any())).thenAnswer(invocacao -> {
            CartaoAparelho aparelho = invocacao.getArgument(0);
            aparelho.setId(42L);
            return aparelho;
        });
    }

    private CartaoAparelho aparelhoComCliente() {
        CartaoAparelho aparelho = CartaoAparelho.builder().id(42L).chaveHash(CartaoSalvoService.hash(CHAVE))
                .gateway("MERCADO_PAGO").clienteGatewayId("cliente-1").dataHoraCriacao(LocalDateTime.now())
                .dataHoraUltimoUso(LocalDateTime.now()).build();
        when(repository.findByChaveHashAndGateway(CartaoSalvoService.hash(CHAVE), "MERCADO_PAGO"))
                .thenReturn(Optional.of(aparelho));
        return aparelho;
    }

    @Test
    void primeiroCartaoCriaOAparelhoEOClienteComEmailTecnicoSemGuardarAChave() {
        when(gateway.criarCliente("test_payer_42@testuser.com")).thenReturn("cliente-1");
        when(gateway.salvarCartao("cliente-1", "tok_1")).thenReturn(VISA);

        assertThat(service.salvar(CHAVE, "tok_1")).isTrue();

        ArgumentCaptor<CartaoAparelho> aparelho = ArgumentCaptor.forClass(CartaoAparelho.class);
        verify(repository).saveAndFlush(aparelho.capture());
        // Só o hash da chave vai para o banco.
        assertThat(aparelho.getValue().getChaveHash()).hasSize(64).isNotEqualTo(CHAVE);
        assertThat(aparelho.getValue().getClienteGatewayId()).isEqualTo("cliente-1");
        verify(gateway).salvarCartao("cliente-1", "tok_1");
    }

    @Test
    void aparelhoQueJaTemClienteSoSalvaOCartao() {
        aparelhoComCliente();
        when(gateway.salvarCartao("cliente-1", "tok_2")).thenReturn(VISA);

        assertThat(service.salvar(CHAVE, "tok_2")).isTrue();

        verify(gateway, never()).criarCliente(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void clienteQueSumiuDoProvedorEhCriadoDeNovo() {
        CartaoAparelho aparelho = aparelhoComCliente();
        when(gateway.salvarCartao("cliente-1", "tok_3")).thenThrow(new GatewayPagamentoException("404", 404, null));
        when(gateway.criarCliente("test_payer_42@testuser.com")).thenReturn("cliente-2");
        when(gateway.salvarCartao("cliente-2", "tok_3")).thenReturn(VISA);

        assertThat(service.salvar(CHAVE, "tok_3")).isTrue();

        assertThat(aparelho.getClienteGatewayId()).isEqualTo("cliente-2");
    }

    @Test
    void falhaDoProvedorAoSalvarSoDeixaOCartaoSemSalvar() {
        aparelhoComCliente();
        when(gateway.salvarCartao(any(), any())).thenThrow(new GatewayPagamentoException("recusado", 400, null));

        assertThat(service.salvar(CHAVE, "tok_4")).isFalse();
    }

    @Test
    void chaveInvalidaNaoSalvaNemConsultaOProvedor() {
        assertThat(service.salvar("curta", "tok_5")).isFalse();
        assertThat(service.salvar(null, "tok_5")).isFalse();
        assertThat(service.clienteDoAparelho("com espaço e muito longa ................")).isEmpty();

        verify(gateway, never()).salvarCartao(any(), any());
        assertThatThrownBy(() -> service.listar("curta"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void listaOsCartoesDoAparelho() {
        aparelhoComCliente();
        when(gateway.listarCartoes("cliente-1")).thenReturn(List.of(VISA));

        CartoesSalvosDTO cartoes = service.listar(CHAVE);

        assertThat(cartoes.clienteId()).isEqualTo("cliente-1");
        assertThat(cartoes.cartoes()).containsExactly(VISA);
    }

    @Test
    void aparelhoSemCartoesSalvosDevolveListaVazia() {
        CartoesSalvosDTO cartoes = service.listar(CHAVE);

        assertThat(cartoes.clienteId()).isNull();
        assertThat(cartoes.cartoes()).isEmpty();
        verify(gateway, never()).listarCartoes(any());
    }

    @Test
    void clienteQueSumiuDoProvedorDeixaDeSerUsado() {
        CartaoAparelho aparelho = aparelhoComCliente();
        when(gateway.listarCartoes("cliente-1")).thenThrow(new GatewayPagamentoException("404", 404, null));

        assertThat(service.listar(CHAVE).cartoes()).isEmpty();
        assertThat(aparelho.getClienteGatewayId()).isNull();
        verify(repository, times(1)).save(aparelho);
    }

    @Test
    void removeOCartaoDoAparelho() {
        aparelhoComCliente();

        service.excluir(CHAVE, "card-1");

        verify(gateway).excluirCartao("cliente-1", "card-1");
    }

    @Test
    void naoRemoveCartaoDeAparelhoSemCartoes() {
        assertThatThrownBy(() -> service.excluir(CHAVE, "card-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(gateway, never()).excluirCartao(any(), any());
    }

    @Test
    void cobrancaUsaOClienteDoAparelho() {
        aparelhoComCliente();

        assertThat(service.clienteDoAparelho(CHAVE)).contains("cliente-1");
    }

    @Test
    void semModeloDeEmailUsaOPadrao() {
        CartaoSalvoService padrao = new CartaoSalvoService(repository, gateway, "");
        when(gateway.criarCliente("cliente-42@covildocalango.tech")).thenReturn("cliente-1");
        when(gateway.salvarCartao("cliente-1", "tok_6")).thenReturn(VISA);

        assertThat(padrao.salvar(CHAVE, "tok_6")).isTrue();
    }
}
