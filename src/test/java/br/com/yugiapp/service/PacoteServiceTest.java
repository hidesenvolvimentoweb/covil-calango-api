package br.com.yugiapp.service;

import br.com.yugiapp.enums.CategoriaPacoteEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPacoteEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pacote;
import br.com.yugiapp.model.Parametro;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.repository.PacoteRepository;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PacoteServiceTest {

    private static final String COMANDA = "20261125-3";
    private static final String FOTO = "/api/arquivos/pacotes/foto.jpg";

    private final PacoteRepository pacoteRepository = mock(PacoteRepository.class);
    private final ComandaService comandaService = mock(ComandaService.class);
    private final PedidoRepository pedidoRepository = mock(PedidoRepository.class);
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final ParametroService parametroService = mock(ParametroService.class);
    private final ArquivoService arquivoService = mock(ArquivoService.class);
    private final MockMultipartFile foto = new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[]{1, 2});
    private PacoteService service;
    private Comanda comanda;

    @BeforeEach
    void configurar() {
        service = new PacoteService(pacoteRepository, comandaService, pedidoRepository, pagamentoRepository,
                parametroService, arquivoService);
        comanda = Comanda.builder().id(1L).numero(COMANDA).nomeCliente("Yugi")
                .status(StatusComandaEnum.ABERTA.getValue()).build();
        when(comandaService.findByNumero(COMANDA)).thenReturn(Optional.of(comanda));
        when(comandaService.travarParaAtualizacao(COMANDA)).thenReturn(comanda);
        when(pacoteRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(pacoteRepository.saveAndFlush(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(pedidoRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(arquivoService.salvarImagem(any(), eq(ArquivoService.PASTA_PACOTES))).thenReturn(FOTO);
    }

    private void taxa(String valor) {
        when(parametroService.findByNome(PacoteService.PARAMETRO_VALOR))
                .thenReturn(Optional.of(Parametro.builder().nome(PacoteService.PARAMETRO_VALOR).descricao(valor).build()));
    }

    private Pacote aguardando(Pedido pedido) {
        Pacote pacote = Pacote.builder().id(9L).codigo("ABC234").status(StatusPacoteEnum.AGUARDANDO_RETIRADA)
                .comanda(comanda).pedido(pedido).build();
        when(pacoteRepository.findByIdParaAtualizacao(9L)).thenReturn(Optional.of(pacote));
        return pacote;
    }

    private static void assertErro(Runnable acao, HttpStatus status, String mensagem) {
        assertThatThrownBy(acao::run).isInstanceOfSatisfying(ResponseStatusException.class, erro -> {
            assertThat(erro.getStatusCode()).isEqualTo(status);
            assertThat(erro.getReason()).isEqualTo(mensagem);
        });
    }

    @Test
    void depositarGeraOCodigoELancaATaxaNaComanda() {
        taxa("0,50");

        Pacote pacote = service.depositar(COMANDA, CategoriaPacoteEnum.PLAYMAT, " Playmat azul ", " Joey ", foto);

        assertThat(pacote.getCodigo()).hasSize(PacoteService.TAMANHO_CODIGO).matches("[A-HJKMNP-Z2-9]+");
        assertThat(pacote.getDescricao()).isEqualTo("Playmat azul");
        assertThat(pacote.getDestinatario()).isEqualTo("Joey");
        assertThat(pacote.getStatus()).isEqualTo(StatusPacoteEnum.AGUARDANDO_RETIRADA);
        assertThat(pacote.getFotoDeposito()).isEqualTo(FOTO);
        ArgumentCaptor<Pedido> pedido = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(pedido.capture());
        assertThat(pedido.getValue().getDescricao()).isEqualTo("Pacote " + pacote.getCodigo());
        assertThat(pedido.getValue().getValorUnitario()).isEqualTo(0.5);
        assertThat(pedido.getValue().getStatus()).isEqualTo(StatusPedidoEnum.FINALIZADO);
        assertThat(pedido.getValue().getComanda()).isEqualTo(comanda);
        assertThat(pacote.getPedido()).isEqualTo(pedido.getValue());
    }

    @Test
    void taxaPadraoEGratuidade() {
        assertThat(service.getValorTaxa()).isEqualByComparingTo(new BigDecimal("0.50"));
        taxa("abc");
        assertThat(service.getValorTaxa()).isEqualByComparingTo(new BigDecimal("0.50"));
        taxa("R$ 1,25");
        assertThat(service.getValorTaxa()).isEqualByComparingTo(new BigDecimal("1.25"));

        taxa("0");
        Pacote pacote = service.depositar(COMANDA, CategoriaPacoteEnum.CARTA, "Carta", "Joey", foto);
        assertThat(pacote.getPedido()).isNull();
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void depositarValidaOsDadosEAComanda() {
        assertErro(() -> service.depositar(COMANDA, null, "Carta", "Joey", foto), HttpStatus.BAD_REQUEST,
                "Informe o que está sendo deixado");
        assertErro(() -> service.depositar(COMANDA, CategoriaPacoteEnum.CARTA, " ", "Joey", foto), HttpStatus.BAD_REQUEST,
                "Descreva o que está sendo deixado");
        assertErro(() -> service.depositar(COMANDA, CategoriaPacoteEnum.CARTA, "Carta", null, foto), HttpStatus.BAD_REQUEST,
                "Informe quem vai receber o pacote");
        assertErro(() -> service.depositar(COMANDA, CategoriaPacoteEnum.CARTA, "Carta", "Joey", null),
                HttpStatus.BAD_REQUEST, "Tire uma foto do que está sendo deixado");

        when(pagamentoRepository.existsByComandaNumeroAndStatus(COMANDA, StatusPagamentoEnum.PENDENTE)).thenReturn(true);
        assertErro(() -> service.depositar(COMANDA, CategoriaPacoteEnum.CARTA, "Carta", "Joey", foto), HttpStatus.CONFLICT,
                "Há um pagamento PIX em andamento para esta comanda. Cancele o PIX para deixar um pacote");

        comanda.setStatus(StatusComandaEnum.FECHADA.getValue());
        assertErro(() -> service.depositar(COMANDA, CategoriaPacoteEnum.CARTA, "Carta", "Joey", foto), HttpStatus.CONFLICT,
                "Sua comanda foi finalizada. Gere uma nova comanda para deixar um pacote");
        verify(pacoteRepository, never()).save(any());
        verify(arquivoService, never()).salvarImagem(any(), any());
    }

    @Test
    void retiradaConfereOCodigoEExigeAFoto() {
        Pacote pacote = aguardando(null);

        assertErro(() -> service.retirar(9L, "XYZ999", foto, "atendente@covil"), HttpStatus.BAD_REQUEST,
                "O código não confere com o pacote");
        assertErro(() -> service.retirar(9L, "abc234", null, "atendente@covil"), HttpStatus.BAD_REQUEST,
                "Tire uma foto do item sendo retirado");

        // O código digitado em minúsculas e com hífen/espaço é aceito.
        service.retirar(9L, " abc-234 ", foto, "atendente@covil");
        assertThat(pacote.getStatus()).isEqualTo(StatusPacoteEnum.RETIRADO);
        assertThat(pacote.getFotoRetirada()).isEqualTo(FOTO);
        assertThat(pacote.getRetiradoPor()).isEqualTo("atendente@covil");
        assertThat(pacote.getDataHoraRetirada()).isNotNull();

        assertErro(() -> service.retirar(9L, "ABC234", foto, "atendente@covil"), HttpStatus.CONFLICT,
                "Este pacote já foi retirado");
        assertErro(() -> service.cancelarPelaEquipe(9L), HttpStatus.CONFLICT, "Este pacote já foi retirado");
    }

    @Test
    void cancelarTiraATaxaDaComandaAberta() {
        Pedido pedido = Pedido.builder().id(4L).valorUnitario(0.5).build();
        Pacote pacote = aguardando(pedido);

        service.cancelarPeloCliente(9L, COMANDA);

        assertThat(pacote.getStatus()).isEqualTo(StatusPacoteEnum.CANCELADO);
        assertThat(pacote.getPedido()).isNull();
        assertThat(pacote.getTaxaRemovida()).isEqualTo(pedido);
        verify(pedidoRepository).delete(pedido);
    }

    @Test
    void clienteSoCancelaOsPacotesDaPropriaComanda() {
        aguardando(null);
        assertErro(() -> service.cancelarPeloCliente(9L, "20261125-99"), HttpStatus.NOT_FOUND, "Pacote não encontrado");
    }

    @Test
    void cancelarComPixPendenteNaoMexeNaComanda() {
        Pedido pedido = Pedido.builder().id(4L).valorUnitario(0.5).build();
        Pacote pacote = aguardando(pedido);
        when(pagamentoRepository.existsByComandaNumeroAndStatus(COMANDA, StatusPagamentoEnum.PENDENTE)).thenReturn(true);

        assertErro(() -> service.cancelarPelaEquipe(9L), HttpStatus.CONFLICT, "Há um pagamento PIX em andamento para a "
                + "comanda " + COMANDA + ". Cancele o pagamento antes de cancelar o pacote");
        assertThat(pacote.getStatus()).isEqualTo(StatusPacoteEnum.AGUARDANDO_RETIRADA);
        verify(pedidoRepository, never()).delete(any());
    }

    @Test
    void equipeCancelaPacoteDeComandaFechadaMantendoATaxa() {
        Pedido pedido = Pedido.builder().id(4L).valorUnitario(0.5).build();
        Pacote pacote = aguardando(pedido);
        comanda.setStatus(StatusComandaEnum.FECHADA.getValue());

        service.cancelarPelaEquipe(9L);

        assertThat(pacote.getStatus()).isEqualTo(StatusPacoteEnum.CANCELADO);
        assertThat(pacote.getPedido()).isEqualTo(pedido);
        verify(pedidoRepository, never()).delete(any());
        // O cliente, com a comanda fechada, não cancela mais.
        assertErro(() -> service.cancelarPeloCliente(9L, COMANDA), HttpStatus.CONFLICT, "Este pacote foi cancelado");
    }

    @Test
    void listagemDaEquipeNaoMostraOCodigoDosPacotesAguardando() {
        Pacote aguardando = Pacote.builder().id(1L).codigo("ABC234").status(StatusPacoteEnum.AGUARDANDO_RETIRADA)
                .comanda(comanda).build();
        Pacote retirado = Pacote.builder().id(2L).codigo("XYZ789").status(StatusPacoteEnum.RETIRADO)
                .comanda(comanda).build();
        when(pacoteRepository.findByStatusInOrderByDataHoraDepositoDescIdDesc(any(), any()))
                .thenReturn(java.util.List.of(aguardando, retirado));

        var pacotes = service.getAll(null);

        assertThat(pacotes).extracting(br.com.yugiapp.dto.PacoteResponseDTO::getCodigo)
                .containsExactly(null, "XYZ789");
    }

    @Test
    void consultaPorCodigoNormaliza() {
        Pacote pacote = Pacote.builder().id(9L).codigo("ABC234").status(StatusPacoteEnum.AGUARDANDO_RETIRADA)
                .comanda(comanda).build();
        when(pacoteRepository.findByCodigo("ABC234")).thenReturn(Optional.of(pacote));

        assertThat(service.getByCodigo(" abc 234 ").getComanda()).isEqualTo(COMANDA);
        assertErro(() -> service.getByCodigo("ZZZ999"), HttpStatus.NOT_FOUND, "Nenhum pacote com o código ZZZ999");
        assertErro(() -> service.getByCodigo("  "), HttpStatus.BAD_REQUEST, "Informe o código do pacote");
        // No app, a comanda não é exposta.
        assertThat(service.toResponse(pacote, false).getComanda()).isNull();
    }
}
