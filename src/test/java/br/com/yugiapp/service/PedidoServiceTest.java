package br.com.yugiapp.service;

import br.com.yugiapp.converter.PedidoConverter;
import br.com.yugiapp.dto.PedidoRequestDTO;
import br.com.yugiapp.dto.ProdutoRequestDTO;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.model.Produto;
import br.com.yugiapp.repository.PagamentoRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoServiceTest {

    private static final String COMANDA = "202615-10";

    private final PedidoRepository pedidoRepository = mock(PedidoRepository.class);
    private final ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
    private final ComandaService comandaService = mock(ComandaService.class);
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private PedidoService service;

    @BeforeEach
    void configurar() {
        service = new PedidoService(pedidoRepository, produtoRepository, comandaService,
                mock(PedidoSpecifications.class), mock(PedidoConverter.class), pagamentoRepository,
                mock(br.com.yugiapp.repository.EventoParticipanteRepository.class),
                mock(br.com.yugiapp.repository.PacoteRepository.class));
        when(comandaService.travarParaAtualizacao(COMANDA)).thenReturn(Comanda.builder().numero(COMANDA)
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
        when(pedidoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(Pedido.builder().id(1L).status(atual).build()));
        assertThat(service.alterarStatus(1L, novo).getStatus()).isEqualTo(novo);
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELADO, EM_ANDAMENTO", "FINALIZADO, CANCELADO", "PRONTO, CANCELADO",
            "SOLICITADO, PRONTO", "SOLICITADO, FINALIZADO", "EM_ANDAMENTO, EM_ANDAMENTO"})
    void recusaTransicoesForaDoFluxo(StatusPedidoEnum atual, StatusPedidoEnum novo) {
        when(pedidoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(Pedido.builder().id(1L).status(atual).build()));
        assertThatThrownBy(() -> service.alterarStatus(1L, novo))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(pedidoRepository, never()).save(any());
    }

    private void produtoComEstoque(Integer estoque) {
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(Produto.builder().id(1L).nome("Água")
                .valor(5.0).estoque(estoque).build()));
    }

    @Test
    void produtoSemControleDeEstoqueNaoDaBaixa() {
        service.save(pedido(3L));
        verify(produtoRepository, never()).baixarEstoque(any(), anyInt());
    }

    @Test
    void daBaixaNoEstoqueAoRegistrarOPedido() {
        produtoComEstoque(10);
        when(produtoRepository.baixarEstoque(1L, 5)).thenReturn(1);

        service.save(pedido(5L));

        verify(produtoRepository).baixarEstoque(1L, 5);
        verify(pedidoRepository).save(any());
    }

    @Test
    void recusaPedidoSemUnidadesSuficientesSemInformarOEstoque() {
        produtoComEstoque(3);
        when(produtoRepository.baixarEstoque(1L, 5)).thenReturn(0);

        assertThatThrownBy(() -> service.save(pedido(5L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, erro -> {
                    assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(erro.getReason()).contains("Água").doesNotContain("3");
                });
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void recusaProdutoEsgotado() {
        produtoComEstoque(0);

        assertThatThrownBy(() -> service.save(pedido(1L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getReason()).isEqualTo("Água esgotou. Escolha outro produto"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLICITADO", "EM_ANDAMENTO"})
    void cancelamentoDevolveAsUnidadesAoEstoque(StatusPedidoEnum atual) {
        when(pedidoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(Pedido.builder().id(1L).status(atual)
                .produto(Produto.builder().id(1L).build()).quantidade(4L).build()));

        service.alterarStatus(1L, StatusPedidoEnum.CANCELADO);

        verify(produtoRepository).devolverEstoque(1L, 4);
    }

    @Test
    void demaisTransicoesNaoMexemNoEstoque() {
        when(pedidoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(Pedido.builder().id(1L)
                .status(StatusPedidoEnum.SOLICITADO).produto(Produto.builder().id(1L).build()).quantidade(4L).build()));

        service.alterarStatus(1L, StatusPedidoEnum.EM_ANDAMENTO);

        verify(produtoRepository, never()).devolverEstoque(any(), anyInt());
    }

    @ParameterizedTest
    @CsvSource({"SOLICITADO, true", "EM_ANDAMENTO, true", "PRONTO, false", "FINALIZADO, false", "CANCELADO, false"})
    void exclusaoSoDevolveOEstoqueDePedidosNaoEntregues(StatusPedidoEnum status, boolean devolve) {
        when(pedidoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(Pedido.builder().id(1L).status(status)
                .produto(Produto.builder().id(1L).build()).quantidade(2L).build()));

        service.delete(1L);

        verify(produtoRepository, devolve ? times(1) : never()).devolverEstoque(1L, 2);
    }

    @Test
    void limiteDoPedidoNoCardapioRespeitaOEstoqueSemExpoLo() {
        assertThat(Produto.builder().build().getQuantidadeMaximaPedido()).isEqualTo(PedidoService.QUANTIDADE_MAXIMA);
        assertThat(Produto.builder().estoque(100).build().getQuantidadeMaximaPedido())
                .isEqualTo(PedidoService.QUANTIDADE_MAXIMA);
        assertThat(Produto.builder().estoque(7).build().getQuantidadeMaximaPedido()).isEqualTo(7);
        assertThat(Produto.builder().estoque(0).build().getQuantidadeMaximaPedido()).isZero();
    }

    @Test
    void gravaOPrecoDoMomentoDoPedido() {
        Pedido salvo = service.save(pedido(2L));

        assertThat(salvo.getValorUnitario()).isEqualTo(10.0);
        // Mudar o preço do produto depois não altera o valor do pedido já feito.
        salvo.getProduto().setValor(15.0);
        assertThat(salvo.valorTotal()).isEqualByComparingTo("20.0");
    }

    @Test
    void pedidoAntigoSemPrecoGravadoUsaOPrecoDoProduto() {
        Pedido antigo = Pedido.builder().produto(Produto.builder().valor(7.5).build()).quantidade(2L).build();
        assertThat(antigo.valorTotal()).isEqualByComparingTo("15.0");
    }

    private Pedido pedidoDaComandaComPix(StatusPedidoEnum status) {
        Pedido pedido = Pedido.builder().id(1L).status(status).quantidade(1L)
                .produto(Produto.builder().id(1L).valor(10.0).build())
                .comanda(Comanda.builder().numero(COMANDA).build()).build();
        when(pedidoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.existsByComandaNumeroAndStatus(COMANDA, StatusPagamentoEnum.PENDENTE)).thenReturn(true);
        return pedido;
    }

    @Test
    void naoCancelaPedidoComPixPendente() {
        pedidoDaComandaComPix(StatusPedidoEnum.SOLICITADO);

        assertThatThrownBy(() -> service.alterarStatus(1L, StatusPedidoEnum.CANCELADO))
                .isInstanceOfSatisfying(ResponseStatusException.class, erro -> {
                    assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(erro.getReason()).contains("Cancele o pagamento antes de cancelar o pedido");
                });
        verify(pedidoRepository, never()).save(any());
        verify(produtoRepository, never()).devolverEstoque(any(), anyInt());
    }

    @Test
    void naoExcluiPedidoComPixPendente() {
        pedidoDaComandaComPix(StatusPedidoEnum.EM_ANDAMENTO);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(pedidoRepository, never()).delete(any());
    }

    @Test
    void comPixPendenteACozinhaSegueOFluxoEOCanceladoPodeSerExcluido() {
        // Avançar o pedido não muda o valor cobrado.
        pedidoDaComandaComPix(StatusPedidoEnum.SOLICITADO);
        assertThat(service.alterarStatus(1L, StatusPedidoEnum.EM_ANDAMENTO).getStatus())
                .isEqualTo(StatusPedidoEnum.EM_ANDAMENTO);

        // Um pedido cancelado já está fora do valor.
        pedidoDaComandaComPix(StatusPedidoEnum.CANCELADO);
        service.delete(1L);
        verify(pedidoRepository).delete(any());
    }
}
