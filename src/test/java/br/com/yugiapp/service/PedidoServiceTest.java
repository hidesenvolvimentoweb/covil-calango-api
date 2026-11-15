package br.com.yugiapp.service;

import br.com.yugiapp.converter.PedidoConverter;
import br.com.yugiapp.dto.PedidoRequestDTO;
import br.com.yugiapp.dto.ProdutoRequestDTO;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.model.Produto;
import br.com.yugiapp.repository.PedidoRepository;
import br.com.yugiapp.repository.ProdutoRepository;
import br.com.yugiapp.specs.PedidoSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoServiceTest {

    private static final String COMANDA = "202615-10";

    private final PedidoRepository pedidoRepository = mock(PedidoRepository.class);
    private final ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
    private final ComandaService comandaService = mock(ComandaService.class);
    private PedidoService service;

    @BeforeEach
    void configurar() {
        service = new PedidoService(pedidoRepository, produtoRepository, comandaService,
                mock(PedidoSpecifications.class), mock(PedidoConverter.class));
        when(comandaService.getByNumero(COMANDA)).thenReturn(Comanda.builder().numero(COMANDA)
                .status(StatusComandaEnum.ABERTA.getValue()).build());
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(Produto.builder().id(1L).valor(10.0).build()));
        when(pedidoRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private PedidoRequestDTO pedido(Long quantidade) {
        return PedidoRequestDTO.builder()
                .comanda(COMANDA)
                .produto(ProdutoRequestDTO.builder().id(1L).build())
                .quantidade(quantidade)
                .build();
    }

    private void assertBadRequest(PedidoRequestDTO pedido) {
        assertThatThrownBy(() -> service.save(pedido))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(pedidoRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -10, PedidoService.QUANTIDADE_MAXIMA + 1})
    void recusaQuantidadeForaDoLimite(long quantidade) {
        assertBadRequest(pedido(quantidade));
    }

    @Test
    void recusaPedidoIncompleto() {
        assertBadRequest(pedido(null));
        PedidoRequestDTO semProduto = pedido(1L);
        semProduto.setProduto(null);
        assertBadRequest(semProduto);
        PedidoRequestDTO semComanda = pedido(1L);
        semComanda.setComanda(" ");
        assertBadRequest(semComanda);
        PedidoRequestDTO observacaoLonga = pedido(1L);
        observacaoLonga.setObservacao("a".repeat(PedidoService.TAMANHO_MAXIMO_OBSERVACAO + 1));
        assertBadRequest(observacaoLonga);
    }

    @Test
    void recusaProdutoInexistente() {
        PedidoRequestDTO pedido = pedido(1L);
        pedido.getProduto().setId(99L);
        assertThatThrownBy(() -> service.save(pedido))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void salvaPedidoValido() {
        Pedido salvo = service.save(pedido(PedidoService.QUANTIDADE_MAXIMA));
        assertThat(salvo.getStatus()).isEqualTo(StatusPedidoEnum.SOLICITADO);
        assertThat(salvo.getQuantidade()).isEqualTo(PedidoService.QUANTIDADE_MAXIMA);
        assertThat(salvo.getProduto().getValor()).isEqualTo(10.0);
    }

    @ParameterizedTest
    @CsvSource({
            "SOLICITADO, EM_ANDAMENTO", "SOLICITADO, CANCELADO",
            "EM_ANDAMENTO, PRONTO", "EM_ANDAMENTO, CANCELADO",
            "PRONTO, FINALIZADO"})
    void permiteAsTransicoesDoFluxoDaCozinha(StatusPedidoEnum atual, StatusPedidoEnum novo) {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(Pedido.builder().id(1L).status(atual).build()));
        assertThat(service.alterarStatus(1L, novo).getStatus()).isEqualTo(novo);
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELADO, EM_ANDAMENTO", "FINALIZADO, CANCELADO", "PRONTO, CANCELADO",
            "SOLICITADO, PRONTO", "SOLICITADO, FINALIZADO", "EM_ANDAMENTO, EM_ANDAMENTO"})
    void recusaTransicoesForaDoFluxo(StatusPedidoEnum atual, StatusPedidoEnum novo) {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(Pedido.builder().id(1L).status(atual).build()));
        assertThatThrownBy(() -> service.alterarStatus(1L, novo))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(pedidoRepository, never()).save(any());
    }
}
