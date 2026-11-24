package br.com.yugiapp.service;

import br.com.yugiapp.dto.EventoFilterRequestDTO;
import br.com.yugiapp.dto.EventoRequestDTO;
import br.com.yugiapp.dto.EventoResponseDTO;
import br.com.yugiapp.enums.TipoEventoEnum;
import br.com.yugiapp.model.Evento;
import br.com.yugiapp.repository.EventoParticipanteRepository;
import br.com.yugiapp.repository.EventoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventoServiceTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 28, 19, 0);

    private final EventoRepository eventoRepository = mock(EventoRepository.class);
    private final ConfiguracaoEventoService configuracao = mock(ConfiguracaoEventoService.class);
    private final EventoParticipanteRepository participanteRepository = mock(EventoParticipanteRepository.class);
    private EventoService service;

    @BeforeEach
    void configurar() {
        // 24/11 às 23:30 em Brasília = 25/11 02:30 UTC: "hoje" ainda é dia 24 no estabelecimento.
        service = new EventoService(eventoRepository, participanteRepository, configuracao, BRASILIA,
                Clock.fixed(Instant.parse("2026-11-25T02:30:00Z"), ZoneOffset.UTC));
        when(eventoRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(configuracao.getDiasFechados()).thenReturn(EnumSet.of(DayOfWeek.MONDAY));
    }

    private static EventoRequestDTO.EventoRequestDTOBuilder torneio() {
        return EventoRequestDTO.builder().nome(" Torneio Locals ").tipo(TipoEventoEnum.TORNEIO)
                .dataHoraInicio(INICIO).dataHoraFim(INICIO.plusHours(4));
    }

    private void assertBadRequest(EventoRequestDTO request, String mensagem) {
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, erro -> {
                    assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(erro.getReason()).isEqualTo(mensagem);
                });
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void cadastraOEventoNormalizandoOsCampos() {
        EventoResponseDTO criado = service.create(torneio()
                .descricao("  ")
                .dataHoraInicio(INICIO.withSecond(42))
                .valorInscricao(0.0)
                .build());

        assertThat(criado.getNome()).isEqualTo("Torneio Locals");
        assertThat(criado.getDescricao()).isNull();
        assertThat(criado.getDataHoraInicio()).isEqualTo(INICIO);
        // Inscrição zero é o mesmo que gratuito.
        assertThat(criado.getValorInscricao()).isNull();
    }

    @Test
    void tipoPadraoEhOutros() {
        assertThat(service.create(torneio().tipo(null).build()).getTipo()).isEqualTo(TipoEventoEnum.OUTROS);
    }

    @Test
    void validaOsDadosDoEvento() {
        assertBadRequest(torneio().nome(" ").build(), "Nome do evento é obrigatório");
        assertBadRequest(torneio().dataHoraFim(null).build(), "Informe o início e o fim do evento");
        assertBadRequest(torneio().dataHoraFim(INICIO).build(), "O fim do evento deve ser depois do início");
        // Segundos são descartados antes de comparar: 19:00:30 não termina depois de 19:00.
        assertBadRequest(torneio().dataHoraFim(INICIO.withSecond(30)).build(),
                "O fim do evento deve ser depois do início");
        assertBadRequest(torneio().dataHoraFim(INICIO.plusDays(32)).build(), "O evento deve durar no máximo 31 dias");
        assertBadRequest(torneio().valorInscricao(-1.0).build(), "Valor da inscrição deve ser maior ou igual a zero");
        assertBadRequest(torneio().vagas(0).build(), "O número de vagas deve ser maior que zero");
        assertBadRequest(torneio().descricao("a".repeat(EventoService.TAMANHO_MAXIMO_DESCRICAO + 1)).build(),
                "A descrição do evento deve ter no máximo 2000 caracteres");
    }

    @Test
    void alteraOEventoExistente() {
        when(eventoRepository.findById(7L)).thenReturn(Optional.of(Evento.builder().id(7L).nome("Antigo")
                .tipo(TipoEventoEnum.OUTROS).dataHoraInicio(INICIO).dataHoraFim(INICIO.plusHours(1)).build()));

        EventoResponseDTO alterado = service.update(7L, torneio().vagas(16).valorInscricao(20.0).build());

        assertThat(alterado.getId()).isEqualTo(7L);
        assertThat(alterado.getNome()).isEqualTo("Torneio Locals");
        assertThat(alterado.getVagas()).isEqualTo(16);
        assertThat(alterado.getValorInscricao()).isEqualTo(20.0);
    }

    @Test
    void naoCadastraEventoEmDiaQueALojaNaoAbre() {
        // 30/11/2026 é uma segunda-feira.
        LocalDateTime segunda = LocalDateTime.of(2026, 11, 30, 14, 0);
        assertBadRequest(torneio().dataHoraInicio(segunda).dataHoraFim(segunda.plusHours(4)).build(),
                "A loja não abre nesse dia (segunda-feira)");
    }

    @Test
    void alteracaoSoValidaODiaQuandoEleMuda() {
        LocalDateTime segunda = LocalDateTime.of(2026, 11, 30, 14, 0);
        when(eventoRepository.findById(3L)).thenReturn(Optional.of(Evento.builder().id(3L).nome("Antigo")
                .tipo(TipoEventoEnum.OUTROS).dataHoraInicio(segunda).dataHoraFim(segunda.plusHours(2)).build()));

        // Mesmo dia (cadastrado antes de a segunda ser fechada): continua editável.
        assertThat(service.update(3L, torneio().dataHoraInicio(segunda.plusHours(1))
                .dataHoraFim(segunda.plusHours(5)).build()).getNome()).isEqualTo("Torneio Locals");
        // Mudar para outra segunda não é permitido.
        assertThatThrownBy(() -> service.update(3L, torneio().dataHoraInicio(segunda.plusDays(7))
                .dataHoraFim(segunda.plusDays(7).plusHours(4)).build()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void comParticipantesOHorarioMudaMasODiaNao() {
        when(eventoRepository.findById(7L)).thenReturn(Optional.of(Evento.builder().id(7L).nome("Locals")
                .tipo(TipoEventoEnum.TORNEIO).dataHoraInicio(INICIO).dataHoraFim(INICIO.plusHours(4)).build()));
        when(participanteRepository.existsByEventoId(7L)).thenReturn(true);

        assertThat(service.update(7L, torneio().dataHoraInicio(INICIO.plusHours(1)).build()).getDataHoraInicio())
                .isEqualTo(INICIO.plusHours(1));
        assertThatThrownBy(() -> service.update(7L, torneio().dataHoraInicio(INICIO.plusDays(1))
                .dataHoraFim(INICIO.plusDays(1).plusHours(4)).build()))
                .isInstanceOfSatisfying(ResponseStatusException.class, erro -> assertThat(erro.getReason())
                        .isEqualTo("O evento tem participantes. Remova os participantes antes de mudar o dia do evento"));
    }

    @Test
    void exclusaoComParticipantesSoNoEventoEncerrado() {
        // "Agora" é 24/11 às 23:30: o evento de amanhã ainda não terminou; o de ontem, sim.
        when(participanteRepository.existsByEventoId(any())).thenReturn(true);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(Evento.builder().id(1L).dataHoraInicio(INICIO)
                .dataHoraFim(INICIO.plusHours(4)).build()));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOfSatisfying(ResponseStatusException.class,
                erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(eventoRepository, never()).delete(any());

        LocalDateTime ontem = LocalDateTime.of(2026, 11, 23, 19, 0);
        Evento encerrado = Evento.builder().id(2L).dataHoraInicio(ontem).dataHoraFim(ontem.plusHours(4)).build();
        when(eventoRepository.findById(2L)).thenReturn(Optional.of(encerrado));
        service.delete(2L);
        verify(participanteRepository).deleteByEventoId(2L);
        verify(eventoRepository).delete(encerrado);
    }

    @Test
    void eventoInexistenteRetorna404() {
        when(eventoRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void hojeUsaODiaDoEstabelecimento() {
        when(eventoRepository.findNoPeriodo(any(), any())).thenReturn(List.of());

        service.getAllHoje();

        ArgumentCaptor<LocalDateTime> inicio = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fim = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(eventoRepository).findNoPeriodo(inicio.capture(), fim.capture());
        assertThat(inicio.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 24, 0, 0));
        assertThat(fim.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 25, 0, 0));
    }

    /** O Spring cria o serviço como na subida da API (dois construtores: precisa saber qual usar). */
    @Test
    void springConsegueCriarOServico() {
        new ApplicationContextRunner()
                .withBean(EventoRepository.class, () -> eventoRepository)
                .withBean(ConfiguracaoEventoService.class, () -> configuracao)
                .withBean(EventoParticipanteRepository.class, () -> participanteRepository)
                .withUserConfiguration(EventoService.class)
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    assertThat(contexto).hasSingleBean(EventoService.class);
                });
    }

    @Test
    void consultaDoCalendarioExigeUmPeriodoValido() {
        assertThatThrownBy(() -> service.getAll(new EventoFilterRequestDTO()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.getAll(EventoFilterRequestDTO.builder().inicio(INICIO).fim(INICIO).build()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.getAll(EventoFilterRequestDTO.builder().inicio(INICIO)
                .fim(INICIO.plusDays(401)).build()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getReason()).isEqualTo("Consulte no máximo um ano de eventos por vez"));

        when(eventoRepository.findNoPeriodo(INICIO, INICIO.plusDays(42)))
                .thenReturn(List.of(Evento.builder().id(1L).nome("Board Games").build()));
        assertThat(service.getAll(EventoFilterRequestDTO.builder().inicio(INICIO).fim(INICIO.plusDays(42)).build()))
                .extracting(EventoResponseDTO::getNome).containsExactly("Board Games");
    }
}
