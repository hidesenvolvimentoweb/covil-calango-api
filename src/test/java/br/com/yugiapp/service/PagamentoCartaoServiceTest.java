package br.com.yugiapp.service;

import br.com.yugiapp.dto.PagamentoCartaoRequestDTO;
import br.com.yugiapp.dto.PagamentoResponseDTO;
import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.MetodoPagamentoEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pagamento;
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.service.pagamento.GatewayPagamento;
import br.com.yugiapp.service.pagamento.GatewayPagamentoException;
import br.com.yugiapp.service.pagamento.PreferenciaCheckout;
import br.com.yugiapp.service.pagamento.SituacaoPagamento;
import br.com.yugiapp.service.pagamento.SolicitacaoPagamentoCartao;
import br.com.yugiapp.service.pagamento.SolicitacaoPreferencia;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cartão de crédito à vista pelo app, pagamento pela conta do Mercado Pago e estorno pelo administrador. */
class PagamentoCartaoServiceTest {

    private static final String COMANDA = "202622-10";
    private static final String GATEWAY_ID = "987654321";
    private static final BigDecimal VALOR = new BigDecimal("25.50");

    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final ComandaRepository comandaRepository = mock(ComandaRepository.class);
    private final ComandaService comandaService = mock(ComandaService.class);
    private final GatewayPagamento gateway = mock(GatewayPagamento.class);
    /** Sem cartões salvos por padrão (Optional vazio). */
    private final CartaoSalvoService cartaoSalvoService = mock(CartaoSalvoService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private PagamentoService service;
    private Comanda comanda;

    @BeforeEach
    void configurar() {
        service = new PagamentoService(pagamentoRepository, comandaRepository, comandaService, gateway, cartaoSalvoService,
                eventPublisher, mock(PlatformTransactionManager.class), 30, "cliente@covil.local");
        service.setPublicKey("TEST-chave-publica");
        comanda = Comanda.builder().id(1L).numero(COMANDA).nomeCliente("Cliente")
                .status(StatusComandaEnum.ABERTA.getValue()).build();
        when(gateway.isConfigurado()).thenReturn(true);
        when(gateway.nome()).thenReturn("MERCADO_PAGO");
        when(comandaRepository.findByNumeroParaAtualizacao(COMANDA)).thenReturn(Optional.of(comanda));
        when(comandaService.calcularValorTotal(comanda)).thenReturn(VALOR);
        when(pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(any(), any())).thenReturn(Optional.empty());
        when(pagamentoRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            Pagamento pagamento = invocacao.getArgument(0);
            pagamento.setId(7L);
            return pagamento;
        });
    }

    private static PagamentoCartaoRequestDTO cartao() {
        return PagamentoCartaoRequestDTO.builder().token("tok_123").bandeira("Visa").emissorId("25")
                .email(" titular@email.com ").tipoDocumento("cpf").documento("123.456.789-09").build();
    }

    private static SituacaoPagamento cartaoNoProvedor(StatusPagamentoEnum status, String statusGateway, String detalhe) {
        return new SituacaoPagamento(GATEWAY_ID, status, statusGateway, "PAG-7", VALOR, null, detalhe, "visa", "4242",
                null, null, "credit_card");
    }

    @Test
    void cartaoAprovadoFechaAComandaComoCreditoSemGuardarOCartao() {
        when(gateway.pagarComCartao(any())).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.PAGO, "approved", "accredited"));

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, cartao());

        ArgumentCaptor<SolicitacaoPagamentoCartao> solicitacao = ArgumentCaptor.forClass(SolicitacaoPagamentoCartao.class);
        verify(gateway).pagarComCartao(solicitacao.capture());
        // O valor é o da comanda, calculado no servidor; o documento vai só com os dígitos.
        assertThat(solicitacao.getValue().valor()).isEqualByComparingTo(VALOR);
        assertThat(solicitacao.getValue().referencia()).isEqualTo("PAG-7");
        assertThat(solicitacao.getValue().bandeira()).isEqualTo("visa");
        assertThat(solicitacao.getValue().emailPagador()).isEqualTo("titular@email.com");
        assertThat(solicitacao.getValue().tipoDocumento()).isEqualTo("CPF");
        assertThat(solicitacao.getValue().documento()).isEqualTo("12345678909");
        assertThat(solicitacao.getValue().idempotencyKey()).isNotBlank();
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
        assertThat(pagamento.getMetodo()).isEqualTo(MetodoPagamentoEnum.CARTAO_CREDITO);
        assertThat(pagamento.getCartaoBandeira()).isEqualTo("visa");
        assertThat(pagamento.getCartaoFinal()).isEqualTo("4242");
        assertThat(pagamento.getCartaoTipo()).isEqualTo(FormaPagamentoEnum.CREDITO);
        verify(comandaService).fechar(COMANDA, FormaPagamentoEnum.CREDITO);
    }

    @Test
    void cartaoDeDebitoAprovadoFechaAComandaComoDebito() {
        when(gateway.pagarComCartao(any())).thenReturn(new SituacaoPagamento(GATEWAY_ID, StatusPagamentoEnum.PAGO,
                "approved", "PAG-7", VALOR, null, "accredited", "debvisa", "4242", null, null, "debit_card"));

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, cartao());

        assertThat(pagamento.getCartaoTipo()).isEqualTo(FormaPagamentoEnum.DEBITO);
        verify(comandaService).fechar(COMANDA, FormaPagamentoEnum.DEBITO);
    }

    @Test
    void cartaoRecusadoMantemAComandaAbertaEExplicaOMotivo() {
        when(gateway.pagarComCartao(any())).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.RECUSADO, "rejected",
                "cc_rejected_insufficient_amount"));

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, cartao());

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoEnum.RECUSADO);
        assertThat(pagamento.getMensagemRecusa()).isEqualTo("Saldo ou limite insuficiente no cartão.");
        verify(comandaService, never()).fechar(any(), any());
    }

    @Test
    void verificacaoDoBancoPendenteDevolveODesafioEMantemOPagamentoPendente() {
        when(gateway.pagarComCartao(any())).thenReturn(new SituacaoPagamento(GATEWAY_ID, StatusPagamentoEnum.PENDENTE,
                "pending", "PAG-7", VALOR, null, "pending_challenge", "visa", "4242", "https://banco/3ds", "creq-123",
                "credit_card"));

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, cartao());

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoEnum.PENDENTE);
        assertThat(pagamento.getDesafioUrl()).isEqualTo("https://banco/3ds");
        assertThat(pagamento.getDesafioCreq()).isEqualTo("creq-123");
        verify(comandaService, never()).fechar(any(), any());
    }

    @Test
    void tokenRecusadoPeloProvedorViraRecusaSemCobranca() {
        when(gateway.pagarComCartao(any())).thenThrow(new GatewayPagamentoException("recusado", 400, null));

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, cartao());

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoEnum.RECUSADO);
        assertThat(pagamento.getMensagemRecusa()).startsWith("Não foi possível processar o cartão");
    }

    @Test
    void respostaPerdidaDeixaOPagamentoPendenteEAReconciliacaoOEncontraPelaReferencia() {
        when(gateway.pagarComCartao(any())).thenThrow(new GatewayPagamentoException("timeout", 0, null));

        PagamentoResponseDTO resposta = service.pagarComCartao(COMANDA, cartao());
        assertThat(resposta.getStatus()).isEqualTo(StatusPagamentoEnum.PENDENTE);

        Pagamento pendente = Pagamento.builder().id(7L).comanda(comanda).metodo(MetodoPagamentoEnum.CARTAO_CREDITO)
                .status(StatusPagamentoEnum.PENDENTE).valor(VALOR).gateway("MERCADO_PAGO").idempotencyKey("chave")
                .dataHoraCriacao(LocalDateTime.now().minusMinutes(2)).dataHoraExpiracao(LocalDateTime.now().plusMinutes(13))
                .build();
        when(pagamentoRepository.findNumeroComandaById(7L)).thenReturn(Optional.of(COMANDA));
        when(pagamentoRepository.findByIdParaAtualizacao(7L)).thenReturn(Optional.of(pendente));
        when(gateway.buscarPorReferencia("PAG-7"))
                .thenReturn(Optional.of(cartaoNoProvedor(StatusPagamentoEnum.PAGO, "approved", "accredited")));

        service.reconciliar(7L);

        assertThat(pendente.getGatewayId()).isEqualTo(GATEWAY_ID);
        assertThat(pendente.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
        verify(comandaService).fechar(COMANDA, FormaPagamentoEnum.CREDITO);
    }

    @Test
    void pixPendenteEhCanceladoAntesDeCobrarOCartao() {
        Pagamento pix = Pagamento.builder().id(6L).comanda(comanda).metodo(MetodoPagamentoEnum.PIX)
                .status(StatusPagamentoEnum.PENDENTE).valor(VALOR).gateway("MERCADO_PAGO").gatewayId("111")
                .idempotencyKey("chave-pix").dataHoraCriacao(LocalDateTime.now().minusMinutes(3))
                .dataHoraExpiracao(LocalDateTime.now().plusMinutes(27)).build();
        when(pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(comanda, StatusPagamentoEnum.PENDENTE))
                .thenReturn(Optional.of(pix));
        when(gateway.consultar("111")).thenReturn(new SituacaoPagamento("111", StatusPagamentoEnum.CANCELADO,
                "cancelled", "PAG-6", VALOR, null));
        when(gateway.pagarComCartao(any())).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.PAGO, "approved", "accredited"));

        service.pagarComCartao(COMANDA, cartao());

        verify(gateway).cancelar("111");
        assertThat(pix.getStatus()).isEqualTo(StatusPagamentoEnum.CANCELADO);
    }

    @Test
    void recusaDadosIncompletosDoCartao() {
        PagamentoCartaoRequestDTO semDocumento = cartao();
        semDocumento.setDocumento("123");
        PagamentoCartaoRequestDTO semEmail = cartao();
        semEmail.setEmail("sem-arroba");
        PagamentoCartaoRequestDTO semToken = cartao();
        semToken.setToken(" ");

        for (PagamentoCartaoRequestDTO invalido : new PagamentoCartaoRequestDTO[]{semDocumento, semEmail, semToken, null}) {
            assertThatThrownBy(() -> service.pagarComCartao(COMANDA, invalido))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(gateway, never()).pagarComCartao(any());
    }

    private static final String CHAVE_APARELHO = "chave-do-aparelho-0123456789abcdef0123456789";

    @Test
    void celularComCartoesSalvosCobraComoClienteSalvoSemPedirEmailNemDocumento() {
        when(cartaoSalvoService.clienteDoAparelho(CHAVE_APARELHO)).thenReturn(Optional.of("cliente-1"));
        when(gateway.pagarComCartao(any())).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.PAGO, "approved", "accredited"));
        PagamentoCartaoRequestDTO cartaoSalvo = PagamentoCartaoRequestDTO.builder().token("tok_salvo").bandeira("visa")
                .build();

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, cartaoSalvo, CHAVE_APARELHO);

        ArgumentCaptor<SolicitacaoPagamentoCartao> solicitacao = ArgumentCaptor.forClass(SolicitacaoPagamentoCartao.class);
        verify(gateway).pagarComCartao(solicitacao.capture());
        assertThat(solicitacao.getValue().clienteId()).isEqualTo("cliente-1");
        assertThat(solicitacao.getValue().documento()).isNull();
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
        // Não pediu para salvar: a resposta não fala do cartão salvo.
        assertThat(pagamento.getCartaoSalvo()).isNull();
        verify(cartaoSalvoService, never()).salvar(any(), any());
    }

    @Test
    void salvaOCartaoNoCelularDepoisDaCobrancaAprovada() {
        when(gateway.pagarComCartao(any())).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.PAGO, "approved", "accredited"));
        when(cartaoSalvoService.salvar(CHAVE_APARELHO, "tok_123")).thenReturn(true);
        PagamentoCartaoRequestDTO novo = cartao();
        novo.setSalvarCartao(true);

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, novo, CHAVE_APARELHO);

        assertThat(pagamento.getCartaoSalvo()).isTrue();
        verify(cartaoSalvoService).salvar(CHAVE_APARELHO, "tok_123");
    }

    @Test
    void cartaoRecusadoNaoEhSalvo() {
        when(gateway.pagarComCartao(any())).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.RECUSADO, "rejected",
                "cc_rejected_other_reason"));
        PagamentoCartaoRequestDTO novo = cartao();
        novo.setSalvarCartao(true);

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, novo, CHAVE_APARELHO);

        assertThat(pagamento.getCartaoSalvo()).isFalse();
        verify(cartaoSalvoService, never()).salvar(any(), any());
    }

    @Test
    void falhaAoSalvarOCartaoNaoAfetaOPagamentoAprovado() {
        when(gateway.pagarComCartao(any())).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.PAGO, "approved", "accredited"));
        when(cartaoSalvoService.salvar(any(), any())).thenThrow(new IllegalStateException("banco fora do ar"));
        PagamentoCartaoRequestDTO novo = cartao();
        novo.setSalvarCartao(true);

        PagamentoResponseDTO pagamento = service.pagarComCartao(COMANDA, novo, CHAVE_APARELHO);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
        assertThat(pagamento.getCartaoSalvo()).isFalse();
        verify(comandaService).fechar(COMANDA, FormaPagamentoEnum.CREDITO);
    }

    @Test
    void semAChavePublicaOCartaoFicaIndisponivel() {
        service.setPublicKey(" ");

        assertThat(service.getConfiguracao().cartao()).isFalse();
        assertThat(service.getConfiguracao().publicKey()).isNull();
        assertThatThrownBy(() -> service.pagarComCartao(COMANDA, cartao()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void geraOLinkDoMercadoPagoComOValorDaComanda() {
        when(gateway.criarPreferencia(any())).thenReturn(new PreferenciaCheckout("pref-1", "https://mp/checkout"));

        PagamentoResponseDTO pagamento = service.gerarPagamentoMercadoPago(COMANDA);

        ArgumentCaptor<SolicitacaoPreferencia> solicitacao = ArgumentCaptor.forClass(SolicitacaoPreferencia.class);
        verify(gateway).criarPreferencia(solicitacao.capture());
        assertThat(solicitacao.getValue().valor()).isEqualByComparingTo(VALOR);
        assertThat(solicitacao.getValue().referencia()).isEqualTo("PAG-7");
        assertThat(pagamento.getMetodo()).isEqualTo(MetodoPagamentoEnum.CARTEIRA_MERCADO_PAGO);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoEnum.PENDENTE);
        assertThat(pagamento.getUrlCheckout()).isEqualTo("https://mp/checkout");
    }

    private Pagamento linkMercadoPagoPendente() {
        Pagamento pagamento = Pagamento.builder().id(7L).comanda(comanda).metodo(MetodoPagamentoEnum.CARTEIRA_MERCADO_PAGO)
                .status(StatusPagamentoEnum.PENDENTE).valor(VALOR).gateway("MERCADO_PAGO").idempotencyKey("chave")
                .preferenciaId("pref-1").urlCheckout("https://mp/checkout")
                .dataHoraCriacao(LocalDateTime.now().minusMinutes(2)).dataHoraExpiracao(LocalDateTime.now().plusMinutes(28))
                .build();
        when(pagamentoRepository.findIdByGatewayAndGatewayId(anyString(), anyString())).thenReturn(Optional.empty());
        when(pagamentoRepository.findNumeroComandaById(7L)).thenReturn(Optional.of(COMANDA));
        when(pagamentoRepository.findByIdParaAtualizacao(7L)).thenReturn(Optional.of(pagamento));
        return pagamento;
    }

    @Test
    void pagamentoFeitoNoMercadoPagoEhAssociadoPelaReferenciaEFechaAComanda() {
        Pagamento link = linkMercadoPagoPendente();
        when(gateway.consultar(GATEWAY_ID)).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.PAGO, "approved", "accredited"));

        assertThat(service.processarNotificacao(GATEWAY_ID)).isTrue();

        assertThat(link.getGatewayId()).isEqualTo(GATEWAY_ID);
        assertThat(link.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
        verify(comandaService).fechar(COMANDA, FormaPagamentoEnum.CREDITO);
    }

    @Test
    void cartaoRecusadoNoMercadoPagoDeixaOClienteTentarOutroNoMesmoLink() {
        Pagamento link = linkMercadoPagoPendente();
        when(gateway.consultar(GATEWAY_ID)).thenReturn(cartaoNoProvedor(StatusPagamentoEnum.RECUSADO, "rejected",
                "cc_rejected_high_risk"));

        service.processarNotificacao(GATEWAY_ID);

        assertThat(link.getStatus()).isEqualTo(StatusPagamentoEnum.PENDENTE);
        assertThat(link.getGatewayId()).isNull();
        verify(comandaService, never()).fechar(any(), any());
    }

    @Test
    void cancelarOLinkDoMercadoPagoEncerraAPreferencia() {
        Pagamento link = linkMercadoPagoPendente();
        when(pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(comanda, StatusPagamentoEnum.PENDENTE))
                .thenReturn(Optional.of(link));
        when(gateway.buscarPorReferencia("PAG-7")).thenReturn(Optional.empty());

        service.cancelarPixPendente(COMANDA);

        verify(gateway).expirarPreferencia("pref-1");
        assertThat(link.getStatus()).isEqualTo(StatusPagamentoEnum.CANCELADO);
    }

    private Pagamento pagamentoAprovado() {
        comanda.setStatus(StatusComandaEnum.FECHADA.getValue());
        comanda.setFormaPagamento(FormaPagamentoEnum.CREDITO);
        comanda.setDataHoraSaida(LocalDateTime.now());
        Pagamento pagamento = Pagamento.builder().id(7L).comanda(comanda).metodo(MetodoPagamentoEnum.CARTAO_CREDITO)
                .status(StatusPagamentoEnum.PAGO).valor(VALOR).gateway("MERCADO_PAGO").gatewayId(GATEWAY_ID)
                .idempotencyKey("chave").dataHoraCriacao(LocalDateTime.now().minusMinutes(5))
                .dataHoraExpiracao(LocalDateTime.now().plusMinutes(10)).build();
        when(pagamentoRepository.findNumeroComandaById(7L)).thenReturn(Optional.of(COMANDA));
        when(pagamentoRepository.findByIdParaAtualizacao(7L)).thenReturn(Optional.of(pagamento));
        return pagamento;
    }

    @Test
    void estornoDevolveOValorEReabreAComanda() {
        Pagamento pago = pagamentoAprovado();

        assertThat(service.estornar(7L)).isEqualTo(COMANDA);

        verify(gateway).estornar(GATEWAY_ID, "estorno-chave");
        assertThat(pago.getStatus()).isEqualTo(StatusPagamentoEnum.ESTORNADO);
        assertThat(comanda.getStatus()).isEqualTo(StatusComandaEnum.ABERTA.getValue());
        assertThat(comanda.getFormaPagamento()).isNull();
        assertThat(comanda.getDataHoraSaida()).isNull();
    }

    @Test
    void soPagamentoAprovadoPodeSerEstornado() {
        Pagamento recusado = pagamentoAprovado();
        recusado.setStatus(StatusPagamentoEnum.RECUSADO);

        assertThatThrownBy(() -> service.estornar(7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(gateway, never()).estornar(any(), any());
    }

    @Test
    void estornoRecusadoPeloProvedorNaoReabreAComanda() {
        Pagamento pago = pagamentoAprovado();
        org.mockito.Mockito.doThrow(new GatewayPagamentoException("recusado", 400, null))
                .when(gateway).estornar(any(), any());

        assertThatThrownBy(() -> service.estornar(7L)).isInstanceOf(ResponseStatusException.class);

        assertThat(pago.getStatus()).isEqualTo(StatusPagamentoEnum.PAGO);
        assertThat(comanda.getStatus()).isEqualTo(StatusComandaEnum.FECHADA.getValue());
    }
}
