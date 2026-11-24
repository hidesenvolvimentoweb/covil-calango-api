package br.com.yugiapp.service;

import br.com.yugiapp.dto.EventoParticipanteResponseDTO;
import br.com.yugiapp.dto.EventoResponseDTO;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Evento;
import br.com.yugiapp.model.EventoParticipante;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.repository.EventoParticipanteRepository;
import br.com.yugiapp.repository.EventoRepository;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticipacaoEventoServiceTest {

    private static final String COMANDA = "20261124-7";
    /** 24/11/2026 às 15:00 em Brasília. */
    private static final Instant AGORA = Instant.parse("2026-11-24T18:00:00Z");
    private static final LocalDateTime HOJE_14H = LocalDateTime.of(2026, 11, 24, 14, 0);

    private final EventoRepository eventoRepository = mock(EventoRepository.class);
    private final EventoParticipanteRepository participanteRepository = mock(EventoParticipanteRepository.class);
    private final ComandaService comandaService = mock(ComandaService.class);
    private final PedidoRepository pedidoRepository = mock(PedidoRepository.class);
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private ParticipacaoEventoService service;
    private Comanda comanda;

    @BeforeEach
    void configurar() {
        service = new ParticipacaoEventoService(eventoRepository, participanteRepository, comandaService,
                pedidoRepository, pagamentoRepository, ZoneId.of("America/Sao_Paulo"), Clock.fixed(AGORA, ZoneOffset.UTC));
        comanda = Comanda.builder().id(3L).numero(COMANDA).nomeCliente("Yugi")
                .status(StatusComandaEnum.ABERTA.getValue()).build();
        when(comandaService.findByNumero(COMANDA)).thenReturn(Optional.of(comanda));
        when(comandaService.travarParaAtualizacao(COMANDA)).thenReturn(comanda);
        when(participanteRepository.findByEventoIdAndComandaNumero(any(), any())).thenReturn(Optional.empty());
        when(participanteRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(pedidoRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private Evento evento(LocalDateTime inicio, LocalDateTime fim, Double valor, Integer vagas) {
        Evento evento = Evento.builder().id(1L).nome("Torneio Locals").dataHoraInicio(inicio).dataHoraFim(fim)
                .valorInscricao(valor).vagas(vagas).build();
        when(eventoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(evento));
        return evento;
    }

    private void assertConflito(Runnable acao, String mensagem) {
        assertThatThrownBy(acao::run).isInstanceOfSatisfying(ResponseStatusException.class, erro -> {
            assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(erro.getReason()).isEqualTo(mensagem);
        });
    }

    @Test
    void participarDeEventoPagoLancaAInscricaoNaComanda() {
        evento(HOJE_14H, HOJE_14H.plusHours(6), 25.0, null);

        EventoParticipante participante = service.participar(1L, COMANDA);

        ArgumentCaptor<Pedido> pedido = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(pedido.capture());
        assertThat(pedido.getValue().getDescricao()).isEqualTo("Evento - Torneio Locals");
        assertThat(pedido.getValue().getProduto()).isNull();
        assertThat(pedido.getValue().getValorUnitario()).isEqualTo(25.0);
        assertThat(pedido.getValue().getQuantidade()).isEqualTo(1L);
        // Não vai para a fila da cozinha.
        assertThat(pedido.getValue().getStatus()).isEqualTo(StatusPedidoEnum.FINALIZADO);
        assertThat(pedido.getValue().getComanda()).isSameAs(comanda);
        assertThat(participante.getPedido()).isSameAs(pedido.getValue());
        assertThat(participante.getComanda()).isSameAs(comanda);
    }

    @Test
    void eventoGratuitoNaoLancaNadaNaComanda() {
        evento(HOJE_14H, HOJE_14H.plusHours(6), null, null);

        assertThat(service.participar(1L, COMANDA).getPedido()).isNull();
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void soParticipaDosEventosDeHojeQueAindaNaoTerminaram() {
        evento(HOJE_14H.plusDays(1), HOJE_14H.plusDays(1).plusHours(4), null, null);
        assertConflito(() -> service.participar(1L, COMANDA), "Só é possível participar dos eventos de hoje");

        evento(HOJE_14H.minusHours(4), HOJE_14H, null, null);
        assertConflito(() -> service.participar(1L, COMANDA), "Este evento já terminou");

        // Começou ontem e termina hoje às 16h: ainda vale.
        evento(HOJE_14H.minusDays(1).plusHours(6), HOJE_14H.plusHours(2), null, null);
        assertThat(service.participar(1L, COMANDA)).isNotNull();
    }

    @Test
    void respeitaAsVagasEAInscricaoUnica() {
        evento(HOJE_14H, HOJE_14H.plusHours(6), null, 2);
        when(participanteRepository.countByEventoId(1L)).thenReturn(2L);
        assertConflito(() -> service.participar(1L, COMANDA), "Não há mais vagas para este evento");

        when(participanteRepository.findByEventoIdAndComandaNumero(1L, COMANDA))
                .thenReturn(Optional.of(EventoParticipante.builder().build()));
        assertConflito(() -> service.participar(1L, COMANDA), "Você já está participando deste evento");
    }

    @Test
    void naoParticipaComComandaFechadaOuPixPendente() {
        evento(HOJE_14H, HOJE_14H.plusHours(6), 25.0, null);
        when(pagamentoRepository.existsByComandaNumeroAndStatus(COMANDA, StatusPagamentoEnum.PENDENTE)).thenReturn(true);
        assertConflito(() -> service.participar(1L, COMANDA),
                "Há um pagamento PIX em andamento para esta comanda. Cancele o PIX para participar do evento");

        comanda.setStatus(StatusComandaEnum.FECHADA.getValue());
        assertConflito(() -> service.participar(1L, COMANDA),
                "Sua comanda foi finalizada. Gere uma nova comanda para participar dos eventos");
        verify(participanteRepository, never()).save(any());
    }

    @Test
    void sairRemoveAInscricaoEOValorDaComanda() {
        Pedido pedido = Pedido.builder().id(9L).comanda(comanda).build();
        EventoParticipante participante = EventoParticipante.builder().id(4L).comanda(comanda).pedido(pedido).build();
        when(participanteRepository.findByEventoIdAndComandaNumero(1L, COMANDA)).thenReturn(Optional.of(participante));

        service.sair(1L, COMANDA);

        verify(participanteRepository).delete(participante);
        verify(pedidoRepository).delete(pedido);
    }

    @Test
    void equipeRemoveParticipanteDeComandaFechadaSemMexerNoValorPago() {
        comanda.setStatus(StatusComandaEnum.FECHADA.getValue());
        Pedido pedido = Pedido.builder().id(9L).comanda(comanda).build();
        EventoParticipante participante = EventoParticipante.builder().id(4L).comanda(comanda).pedido(pedido).build();
        when(participanteRepository.findByIdAndEventoId(4L, 1L)).thenReturn(Optional.of(participante));

        service.removerParticipante(1L, 4L);

        verify(participanteRepository).delete(participante);
        verify(pedidoRepository, never()).delete(any());
    }

    @Test
    void removerTodosAcertaAsComandasAbertasEMantemAsFechadas() {
        evento(HOJE_14H, HOJE_14H.plusHours(6), 25.0, null);
        Comanda fechada = Comanda.builder().numero("20261124-2").status(StatusComandaEnum.FECHADA.getValue()).build();
        when(comandaService.travarParaAtualizacao("20261124-2")).thenReturn(fechada);
        Pedido pedidoAberta = Pedido.builder().id(9L).comanda(comanda).build();
        Pedido pedidoFechada = Pedido.builder().id(10L).comanda(fechada).build();
        EventoParticipante daAberta = EventoParticipante.builder().id(4L).comanda(comanda).pedido(pedidoAberta).build();
        EventoParticipante daFechada = EventoParticipante.builder().id(5L).comanda(fechada).pedido(pedidoFechada).build();
        when(participanteRepository.findByEventoIdOrderByDataHoraInscricaoAscIdAsc(1L)).thenReturn(List.of(daAberta, daFechada));

        assertThat(service.removerTodos(1L)).containsExactly(daAberta, daFechada);

        verify(participanteRepository).delete(daAberta);
        verify(participanteRepository).delete(daFechada);
        // Só a inscrição da comanda aberta sai do valor; a da fechada já foi paga.
        verify(pedidoRepository).delete(pedidoAberta);
        verify(pedidoRepository, never()).delete(pedidoFechada);
    }

    @Test
    void removerTodosNaoRemoveNadaSeUmaComandaTiverPixPendente() {
        evento(HOJE_14H, HOJE_14H.plusHours(6), 25.0, null);
        EventoParticipante participante = EventoParticipante.builder().id(4L).comanda(comanda)
                .pedido(Pedido.builder().id(9L).comanda(comanda).build()).build();
        when(participanteRepository.findByEventoIdOrderByDataHoraInscricaoAscIdAsc(1L)).thenReturn(List.of(participante));
        when(pagamentoRepository.existsByComandaNumeroAndStatus(COMANDA, StatusPagamentoEnum.PENDENTE)).thenReturn(true);

        // A exceção desfaz a transação inteira (nenhum participante removido).
        assertConflito(() -> service.removerTodos(1L), "Há um pagamento PIX em andamento para a comanda " + COMANDA
                + ". Cancele o pagamento antes de remover a inscrição do evento");
    }

    @Test
    void listaOsParticipantesSemExporAComandaParaOApp() {
        when(eventoRepository.existsById(1L)).thenReturn(true);
        Comanda outra = Comanda.builder().numero("20261124-8").nomeCliente("Kaiba").build();
        when(participanteRepository.findByEventoIdOrderByDataHoraInscricaoAscIdAsc(1L)).thenReturn(List.of(
                EventoParticipante.builder().id(1L).comanda(comanda).build(),
                EventoParticipante.builder().id(2L).comanda(outra).build()));

        List<EventoParticipanteResponseDTO> app = service.getParticipantes(1L, false, COMANDA);
        assertThat(app).extracting(EventoParticipanteResponseDTO::getNome).containsExactly("Yugi", "Kaiba");
        assertThat(app).extracting(EventoParticipanteResponseDTO::getComanda).containsOnlyNulls();
        assertThat(app).extracting(EventoParticipanteResponseDTO::isVoce).containsExactly(true, false);

        assertThat(service.getParticipantes(1L, true, null))
                .extracting(EventoParticipanteResponseDTO::getComanda).containsExactly(COMANDA, "20261124-8");
    }

    @Test
    void completaOsEventosComOsParticipantes() {
        List<EventoResponseDTO> eventos = List.of(EventoResponseDTO.builder().id(1L).build(),
                EventoResponseDTO.builder().id(2L).build());
        when(participanteRepository.contarPorEvento(any())).thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));
        when(participanteRepository.findEventosDaComanda(any(), any())).thenReturn(List.of(1L));

        service.comParticipacao(eventos, COMANDA);

        assertThat(eventos).extracting(EventoResponseDTO::getTotalParticipantes).containsExactly(3L, 0L);
        assertThat(eventos).extracting(EventoResponseDTO::getParticipando).containsExactly(true, false);
    }

    @Test
    void springConsegueCriarOServico() {
        new ApplicationContextRunner()
                .withBean(EventoRepository.class, () -> eventoRepository)
                .withBean(EventoParticipanteRepository.class, () -> participanteRepository)
                .withBean(ComandaService.class, () -> comandaService)
                // O ComandaService recebe o EntityManager por @PersistenceContext.
                .withBean(jakarta.persistence.EntityManagerFactory.class, () -> mock(jakarta.persistence.EntityManagerFactory.class))
                .withBean(PedidoRepository.class, () -> pedidoRepository)
                .withBean(PagamentoRepository.class, () -> pagamentoRepository)
                .withUserConfiguration(ParticipacaoEventoService.class)
                .run(contexto -> assertThat(contexto).hasNotFailed());
    }
}
