package br.com.yugiapp.service;

import br.com.yugiapp.dto.PagamentoResponseDTO;
import br.com.yugiapp.enums.MetodoPagamentoEnum;
import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pagamento;
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.service.pagamento.CobrancaPix;
import br.com.yugiapp.service.pagamento.GatewayPagamento;
import br.com.yugiapp.service.pagamento.PagamentoAtualizadoEvent;
import br.com.yugiapp.service.pagamento.SituacaoPagamento;
import br.com.yugiapp.service.pagamento.SolicitacaoCobrancaPix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagamentoServiceTest {

    private static final String COMANDA = "202622-10";
    private static final String GATEWAY_ID = "123456789";
    private static final BigDecimal VALOR = new BigDecimal("25.50");

    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final ComandaRepository comandaRepository = mock(ComandaRepository.class);
    private final ComandaService comandaService = mock(ComandaService.class);
    private final GatewayPagamento gateway = mock(GatewayPagamento.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private PagamentoService service;
    private Comanda comanda;

    @BeforeEach
    void configurar() {
        service = new PagamentoService(pagamentoRepository, comandaRepository, comandaService, gateway, eventPublisher,
                mock(PlatformTransactionManager.class), 30, "cliente@covil.local");
        comanda = Comanda.builder().id(1L).numero(COMANDA).nomeCliente("Cliente")
                .status(StatusComandaEnum.ABERTA.getValue()).build();
        when(gateway.isConfigurado()).thenReturn(true);
        when(gateway.nome()).thenReturn("MERCADO_PAGO");
        when(comandaRepository.findByNumeroParaAtualizacao(COMANDA)).thenReturn(Optional.of(comanda));
        when(comandaService.calcularValorTotal(comanda)).thenReturn(VALOR);
        when(pagamentoRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            Pagamento pagamento = invocacao.getArgument(0);
            pagamento.setId(7L);
            return pagamento;
        });
    }

    private Pagamento pagamentoPendente(LocalDateTime expiracao) {
        return Pagamento.builder().id(7L).comanda(comanda).metodo(MetodoPagamentoEnum.PIX)
                .status(StatusPagamentoEnum.PENDENTE).valor(VALOR).gateway("MERCADO_PAGO").gatewayId(GATEWAY_ID)
                .idempotencyKey("chave").pixCopiaECola("000201").dataHoraCriacao(LocalDateTime.now().minusMinutes(10))
                .dataHoraExpiracao(expiracao).build();
    }

    private void notificacaoDe(Pagamento pagamento) {
        when(pagamentoRepository.findIdByGatewayAndGatewayId("MERCADO_PAGO", GATEWAY_ID)).thenReturn(Optional.of(7L));
        when(pagamentoRepository.findNumeroComandaById(7L)).thenReturn(Optional.of(COMANDA));
        when(pagamentoRepository.findByIdParaAtualizacao(7L)).thenReturn(Optional.of(pagamento));
    }

    private static SituacaoPagamento situacao(StatusPagamentoEnum status, String referencia, BigDecimal valor) {
        return new SituacaoPagamento(GATEWAY_ID, status, status.name().toLowerCase(), referencia, valor, null);
    }

    @Test
    void geraPixComOValorCalculadoNoServidor() {
        when(gateway.criarCobrancaPix(any())).thenReturn(new CobrancaPix(GATEWAY_ID, StatusPagamentoEnum.PENDENTE,
                "pending", "000201", "iVBOR"));

        PagamentoResponseDTO pix = service.gerarPix(COMANDA);

        ArgumentCaptor<SolicitacaoCobrancaPix> solicitacao = ArgumentCaptor.forClass(SolicitacaoCobrancaPix.class);
        verify(gateway).criarCobrancaPix(solicitacao.capture());
        assertThat(solicitacao.getValue().valor()).isEqualByComparingTo(VALOR);
        assertThat(solicitacao.getValue().referencia()).isEqualTo("PAG-7");
        assertThat(solicitacao.getValue().emailPagador()).isEqualTo("cliente@covil.local");
        assertThat(pix.getStatus()).isEqualTo(StatusPagamentoEnum.PENDENTE);
        assertThat(pix.getPixCopiaECola()).isEqualTo("000201");
        assertThat(pix.getPixQrCodeBase64()).isEqualTo("iVBOR");
    }

    @Test
    void reaproveitaPixPendenteDoMesmoValor() {
        Pagamento pendente = pagamentoPendente(LocalDateTime.now().plusMinutes(20));
        when(pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(comanda, StatusPagamentoEnum.PENDENTE))
                .thenReturn(Optional.of(pendente));

        PagamentoResponseDTO pix = service.gerarPix(COMANDA);

        assertThat(pix.getId()).isEqualTo(7L);
        verify(gateway, never()).criarCobrancaPix(any());
        verify(gateway, never()).cancelar(any());
    }

    @Test
    void substituiPixPendenteQuandoOValorMudou() {
        Pagamento pendente = pagamentoPendente(LocalDateTime.now().plusMinutes(20));
        pendente.setValor(new BigDecimal("40.00"));
        when(pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(comanda, StatusPagamentoEnum.PENDENTE))
                .thenReturn(Optional.of(pendente));
        when(gateway.consultar(GATEWAY_ID)).thenReturn(situacao(StatusPagamentoEnum.CANCELADO, "PAG-7", new BigDecimal("40.00")));
        when(gateway.criarCobrancaPix(any())).thenReturn(new CobrancaPix("999", StatusPagamentoEnum.PENDENTE,
                "pending", "000202", "iVBOR"));

        service.gerarPix(COMANDA);

        verify(gateway).cancelar(GATEWAY_ID);
        assertThat(pendente.getStatus()).isEqualTo(StatusPagamentoEnum.CANCELADO);
        verify(gateway).criarCobrancaPix(any());
    }

    @Test
    void recusaPixDeComandaSemValorOuFechada() {
        when(comandaService.calcularValorTotal(comanda)).thenReturn(BigDecimal.ZERO.setScale(2));
        assertThatThrownBy(() -> service.gerarPix(COMANDA))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        comanda.setStatus(StatusComandaEnum.FECHADA.getValue());
        assertThatThrownBy(() -> service.gerarPix(COMANDA))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(gateway, never()).criarCobrancaPix(any());
    }

    @Test
    void pixIndisponivelSemCredenciais() {
        when(gateway.isConfigurado()).thenReturn(false);
        assertThatThrownBy(() -> service.gerarPix(COMANDA))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void notificacaoDePagamentoAprovadoFechaAComanda() {
        Pagamento pendente = pagamentoPendente(LocalDateTime.now().plusMinutes(20));
        notificacaoDe(pendente);
        when(gateway.consultar(GATEWAY_ID)).thenReturn(situacao(StatusPagamentoEnum.PAGO, "PAG-7", new BigDecimal("25.5")));

        assertThat(service.processarNotificacao(GATEWAY_ID)).isTrue();

        assertThat(pendente.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
        assertThat(pendente.getDataHoraPagamento()).isNotNull();
        verify(comandaService).fechar(COMANDA, FormaPagamentoEnum.PIX);
        ArgumentCaptor<PagamentoAtualizadoEvent> evento = ArgumentCaptor.forClass(PagamentoAtualizadoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().status()).isEqualTo(StatusPagamentoEnum.PAGO);
        assertThat(evento.getValue().comandaFechada()).isTrue();
    }

    @Test
    void naoFechaComandaComValorOuReferenciaDivergente() {
        Pagamento pendente = pagamentoPendente(LocalDateTime.now().plusMinutes(20));
        notificacaoDe(pendente);

        when(gateway.consultar(GATEWAY_ID)).thenReturn(situacao(StatusPagamentoEnum.PAGO, "PAG-7", new BigDecimal("1.00")));
        service.processarNotificacao(GATEWAY_ID);
        when(gateway.consultar(GATEWAY_ID)).thenReturn(situacao(StatusPagamentoEnum.PAGO, "PAG-8", VALOR));
        service.processarNotificacao(GATEWAY_ID);

        assertThat(pendente.getStatus()).isEqualTo(StatusPagamentoEnum.PENDENTE);
        verify(comandaService, never()).fechar(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void notificacaoDePagamentoDesconhecidoEhIgnorada() {
        when(pagamentoRepository.findIdByGatewayAndGatewayId("MERCADO_PAGO", "1")).thenReturn(Optional.empty());

        assertThat(service.processarNotificacao("1")).isFalse();
        verify(gateway, never()).consultar(any());
    }

    @Test
    void pagamentoAprovadoDepoisDeCanceladoAindaEhRegistrado() {
        Pagamento cancelado = pagamentoPendente(LocalDateTime.now().minusMinutes(1));
        cancelado.setStatus(StatusPagamentoEnum.CANCELADO);
        notificacaoDe(cancelado);
        when(gateway.consultar(GATEWAY_ID)).thenReturn(situacao(StatusPagamentoEnum.PAGO, "PAG-7", VALOR));

        service.processarNotificacao(GATEWAY_ID);

        assertThat(cancelado.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
        verify(comandaService).fechar(COMANDA, FormaPagamentoEnum.PIX);
    }

    @Test
    void reconciliacaoEncerraPixVencidoQueOProvedorAindaMostraPendente() {
        Pagamento vencido = pagamentoPendente(LocalDateTime.now().minusMinutes(10));
        notificacaoDe(vencido);
        when(gateway.consultar(GATEWAY_ID)).thenReturn(situacao(StatusPagamentoEnum.PENDENTE, "PAG-7", VALOR));

        service.reconciliar(7L);

        verify(gateway).cancelar(GATEWAY_ID);
        assertThat(vencido.getStatus()).isEqualTo(StatusPagamentoEnum.EXPIRADO);
        verify(comandaService, never()).fechar(any(), any());
    }

    @Test
    void pagamentoAprovadoNaoVoltaParaCancelado() {
        Pagamento pago = pagamentoPendente(LocalDateTime.now().plusMinutes(20));
        pago.setStatus(StatusPagamentoEnum.PAGO);
        notificacaoDe(pago);
        when(gateway.consultar(GATEWAY_ID)).thenReturn(situacao(StatusPagamentoEnum.CANCELADO, "PAG-7", VALOR));

        service.processarNotificacao(GATEWAY_ID);

        assertThat(pago.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
    }
}
