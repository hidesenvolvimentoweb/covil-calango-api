package br.com.yugiapp.service;

import br.com.yugiapp.dto.RelatorioDiarioDTO;
import br.com.yugiapp.dto.RelatorioDiarioDTO.Agrupamento;
import br.com.yugiapp.enums.CategoriaProdutoEnum;
import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.model.Produto;
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Dados gravados em UTC (como no container) e relatório no fuso de Brasília (UTC-3). */
class RelatorioServiceTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DIA = LocalDate.of(2026, 11, 23);
    /** 23/11 00:00 em Brasília = 03:00 UTC. */
    private static final LocalDateTime INICIO_UTC = LocalDateTime.of(2026, 11, 23, 3, 0);
    private static final LocalDateTime FIM_UTC = LocalDateTime.of(2026, 11, 24, 3, 0);

    private final ComandaRepository comandaRepository = mock(ComandaRepository.class);
    private final PedidoRepository pedidoRepository = mock(PedidoRepository.class);
    private RelatorioService service;

    private final Produto agua = Produto.builder().id(1L).nome("Água").valor(5.0).categoria(CategoriaProdutoEnum.BEBIDAS).build();
    private final Produto burguer = Produto.builder().id(2L).nome("X-Calango").valor(30.0)
            .categoria(CategoriaProdutoEnum.HAMBURGUER).build();

    /** Fechada às 22:30 de Brasília (01:30 UTC do dia seguinte): conta no dia 23. */
    private final Comanda noite = comanda(10L, StatusComandaEnum.FECHADA, LocalDateTime.of(2026, 11, 23, 23, 0),
            LocalDateTime.of(2026, 11, 24, 1, 30), FormaPagamentoEnum.PIX);
    private final Comanda tarde = comanda(11L, StatusComandaEnum.FECHADA, LocalDateTime.of(2026, 11, 23, 18, 0),
            LocalDateTime.of(2026, 11, 23, 19, 0), FormaPagamentoEnum.DINHEIRO);
    private final Comanda aberta = comanda(12L, StatusComandaEnum.ABERTA, LocalDateTime.of(2026, 11, 23, 20, 0),
            null, null);
    /** Fechada três dias antes: só entra na tendência. */
    private final Comanda anterior = comanda(9L, StatusComandaEnum.FECHADA, LocalDateTime.of(2026, 11, 20, 18, 0),
            LocalDateTime.of(2026, 11, 20, 20, 0), FormaPagamentoEnum.CREDITO);

    @BeforeEach
    void configurar() {
        service = new RelatorioService(comandaRepository, pedidoRepository, BRASILIA, ZoneOffset.UTC,
                Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneOffset.UTC));

        when(comandaRepository.findFechadasNoPeriodo(eq(StatusComandaEnum.FECHADA.getValue()),
                eq(LocalDateTime.of(2026, 11, 17, 3, 0)), eq(FIM_UTC))).thenReturn(List.of(anterior, tarde, noite));
        when(comandaRepository.findEntradasNoPeriodo(INICIO_UTC, FIM_UTC)).thenReturn(List.of(tarde, noite, aberta));

        Pedido aguaTarde = pedido(StatusPedidoEnum.FINALIZADO, agua, 2, tarde, LocalDateTime.of(2026, 11, 23, 18, 10));
        Pedido burguerNoite = pedido(StatusPedidoEnum.FINALIZADO, burguer, 1, noite, LocalDateTime.of(2026, 11, 23, 23, 5));
        Pedido aguaNoite = pedido(StatusPedidoEnum.PRONTO, agua, 3, noite, LocalDateTime.of(2026, 11, 23, 23, 10));
        Pedido cancelado = pedido(StatusPedidoEnum.CANCELADO, burguer, 4, noite, LocalDateTime.of(2026, 11, 23, 23, 20));
        Pedido emAberto = pedido(StatusPedidoEnum.SOLICITADO, burguer, 1, aberta, LocalDateTime.of(2026, 11, 23, 20, 30));
        Pedido antigo = pedido(StatusPedidoEnum.FINALIZADO, burguer, 2, anterior, LocalDateTime.of(2026, 11, 20, 18, 30));

        when(pedidoRepository.findDoPeriodo(INICIO_UTC, FIM_UTC))
                .thenReturn(List.of(aguaTarde, burguerNoite, aguaNoite, cancelado, emAberto));
        when(pedidoRepository.findByComandaIn(any()))
                .thenReturn(List.of(antigo, aguaTarde, burguerNoite, aguaNoite, cancelado));
    }

    private static Comanda comanda(Long id, StatusComandaEnum status, LocalDateTime entrada, LocalDateTime saida,
                                   FormaPagamentoEnum forma) {
        return Comanda.builder().id(id).numero("2026-" + id).status(status.getValue()).dataHoraEntrada(entrada)
                .dataHoraSaida(saida).formaPagamento(forma).build();
    }

    private static Pedido pedido(StatusPedidoEnum status, Produto produto, long quantidade, Comanda comanda,
                                 LocalDateTime dataHora) {
        return Pedido.builder().status(status).produto(produto).quantidade(quantidade).comanda(comanda)
                .dataHora(dataHora).build();
    }

    @Test
    void arrecadadoConsideraAsComandasFechadasNoDiaDoEstabelecimento() {
        RelatorioDiarioDTO relatorio = service.gerarDiario(DIA);

        // tarde: 2 águas = 10,00 · noite: 1 burguer + 3 águas = 45,00 (o cancelado não conta)
        assertThat(relatorio.arrecadado()).isEqualByComparingTo("55.00");
        assertThat(relatorio.comandasFechadas()).isEqualTo(2);
        assertThat(relatorio.ticketMedio()).isEqualByComparingTo("27.50");
        assertThat(relatorio.comandasAbertas()).isEqualTo(1);
        assertThat(relatorio.comandasAtendidas()).isEqualTo(3);
        // tarde: 60 min · noite: 150 min
        assertThat(relatorio.permanenciaMediaMinutos()).isEqualTo(105);
    }

    @Test
    void resumeOsPedidosDoDiaSemOsCancelados() {
        RelatorioDiarioDTO relatorio = service.gerarDiario(DIA);

        assertThat(relatorio.pedidosRegistrados()).isEqualTo(5);
        assertThat(relatorio.pedidosEntregues()).isEqualTo(2);
        assertThat(relatorio.pedidosCancelados()).isEqualTo(1);
        assertThat(relatorio.itensVendidos()).isEqualTo(7);
        // 10 + 30 + 15 + 30 (pedido da comanda ainda aberta)
        assertThat(relatorio.vendido()).isEqualByComparingTo("85.00");
        assertThat(relatorio.produtos()).extracting(p -> p.nome() + ":" + p.quantidade())
                .containsExactly("Água:5", "X-Calango:2");
        assertThat(relatorio.categorias()).extracting(Agrupamento::rotulo).containsExactly("Hamburguer", "Bebidas");
    }

    @Test
    void agrupaPorFormaDePagamentoPorHoraEPorDia() {
        RelatorioDiarioDTO relatorio = service.gerarDiario(DIA);

        assertThat(relatorio.formasPagamento()).extracting(f -> f.chave() + ":" + f.valor())
                .containsExactly("PIX:45.00", "DEBITO:0.00", "CREDITO:0.00", "DINHEIRO:10.00");
        // Horas de Brasília: fechamentos às 16h (19 UTC) e 22h (01 UTC); pedidos às 15h, 17h e 20h.
        assertThat(relatorio.porHora()).hasSize(24);
        assertThat(relatorio.porHora().get(22).arrecadado()).isEqualByComparingTo("45.00");
        assertThat(relatorio.porHora().get(16).arrecadado()).isEqualByComparingTo("10.00");
        assertThat(relatorio.porHora().get(20).pedidos()).isEqualTo(2);
        assertThat(relatorio.tendencia()).hasSize(RelatorioService.DIAS_TENDENCIA);
        assertThat(relatorio.tendencia().get(6).data()).isEqualTo(DIA);
        assertThat(relatorio.tendencia().get(6).arrecadado()).isEqualByComparingTo("55.00");
        assertThat(relatorio.tendencia().get(3).arrecadado()).isEqualByComparingTo("60.00");
    }

    @Test
    void diaSemMovimentoVemZerado() {
        when(comandaRepository.findFechadasNoPeriodo(anyString(), any(), any())).thenReturn(List.of());
        when(comandaRepository.findEntradasNoPeriodo(any(), any())).thenReturn(List.of());
        when(pedidoRepository.findDoPeriodo(any(), any())).thenReturn(List.of());

        RelatorioDiarioDTO relatorio = service.gerarDiario(DIA);

        assertThat(relatorio.arrecadado()).isEqualByComparingTo("0");
        assertThat(relatorio.ticketMedio()).isEqualByComparingTo("0");
        assertThat(relatorio.permanenciaMediaMinutos()).isNull();
        assertThat(relatorio.produtos()).isEmpty();
    }

    @Test
    void usaOPrecoGravadoNoPedidoENaoOPrecoAtualDoProduto() {
        // A água custava 5,00 quando foi pedida; depois o preço do produto subiu para 50,00.
        Produto aguaReajustada = Produto.builder().id(1L).nome("Água").valor(50.0).categoria(CategoriaProdutoEnum.BEBIDAS)
                .build();
        Pedido aguaTarde = pedido(StatusPedidoEnum.FINALIZADO, aguaReajustada, 2, tarde, LocalDateTime.of(2026, 11, 23, 18, 10));
        aguaTarde.setValorUnitario(5.0);
        when(pedidoRepository.findDoPeriodo(INICIO_UTC, FIM_UTC)).thenReturn(List.of(aguaTarde));
        when(pedidoRepository.findByComandaIn(any())).thenReturn(List.of(aguaTarde));

        RelatorioDiarioDTO relatorio = service.gerarDiario(DIA);

        assertThat(relatorio.arrecadado()).isEqualByComparingTo("10.00");
        assertThat(relatorio.vendido()).isEqualByComparingTo("10.00");
    }

    /** O Spring cria o serviço como na subida da API (dois construtores: precisa saber qual usar). */
    @Test
    void springConsegueCriarOServico() {
        new ApplicationContextRunner()
                .withBean(ComandaRepository.class, () -> comandaRepository)
                .withBean(PedidoRepository.class, () -> pedidoRepository)
                .withUserConfiguration(RelatorioService.class)
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    assertThat(contexto).hasSingleBean(RelatorioService.class);
                });
    }

    @Test
    void hojeUsaOFusoDoEstabelecimento() {
        RelatorioService madrugada = new RelatorioService(comandaRepository, pedidoRepository, BRASILIA, ZoneOffset.UTC,
                Clock.fixed(Instant.parse("2026-11-24T01:00:00Z"), ZoneOffset.UTC));

        assertThat(madrugada.hoje()).isEqualTo(DIA);
    }
}
