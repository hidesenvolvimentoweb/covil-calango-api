package br.com.yugiapp.service;

import br.com.yugiapp.dto.EventoFilterRequestDTO;
import br.com.yugiapp.dto.EventoRequestDTO;
import br.com.yugiapp.dto.EventoResponseDTO;
import br.com.yugiapp.enums.TipoEventoEnum;
import br.com.yugiapp.model.Evento;
import br.com.yugiapp.repository.EventoParticipanteRepository;
import br.com.yugiapp.repository.EventoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Eventos da loja. As datas são o horário local do estabelecimento (ver {@link Evento}); "hoje" também é o dia no
 * fuso do estabelecimento ({@code app.fuso-horario}), e não o da JVM (UTC no container).
 */
@Service
public class EventoService {

    static final int TAMANHO_MAXIMO_NOME = 255;
    static final int TAMANHO_MAXIMO_DESCRICAO = 2000;
    /** Duração máxima de um evento: evita que uma data digitada errada ocupe o calendário por meses. */
    static final Duration DURACAO_MAXIMA = Duration.ofDays(31);
    /** Período máximo de uma consulta do calendário (a maior visualização é o mês, com folga para a lista). */
    static final Duration PERIODO_MAXIMO_CONSULTA = Duration.ofDays(400);

    private static final Locale PT_BR = new Locale("pt", "BR");

    private final EventoRepository eventoRepository;
    private final EventoParticipanteRepository participanteRepository;
    private final ConfiguracaoEventoService configuracaoEventoService;
    private final ZoneId fusoEstabelecimento;
    private final Clock clock;

    /** Construtor usado pelo Spring (com dois construtores, ele precisa da indicação explícita). */
    @Autowired
    public EventoService(EventoRepository eventoRepository, EventoParticipanteRepository participanteRepository,
                         ConfiguracaoEventoService configuracaoEventoService,
                         @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(eventoRepository, participanteRepository, configuracaoEventoService, ZoneId.of(fusoHorario),
                Clock.systemUTC());
    }

    EventoService(EventoRepository eventoRepository, EventoParticipanteRepository participanteRepository,
                  ConfiguracaoEventoService configuracaoEventoService,
                  ZoneId fusoEstabelecimento, Clock clock) {
        this.eventoRepository = eventoRepository;
        this.configuracaoEventoService = configuracaoEventoService;
        this.participanteRepository = participanteRepository;
        this.fusoEstabelecimento = fusoEstabelecimento;
        this.clock = clock;
    }

    /** Eventos do período exibido no calendário. */
    @Transactional(readOnly = true)
    public List<EventoResponseDTO> getAll(EventoFilterRequestDTO filter) {
        LocalDateTime inicio = filter == null ? null : filter.getInicio();
        LocalDateTime fim = filter == null ? null : filter.getFim();
        if (inicio == null || fim == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o período dos eventos");
        }
        if (!fim.isAfter(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O fim do período deve ser depois do início");
        }
        if (Duration.between(inicio, fim).compareTo(PERIODO_MAXIMO_CONSULTA) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulte no máximo um ano de eventos por vez");
        }
        return eventoRepository.findNoPeriodo(inicio, fim).stream().map(this::toResponse).toList();
    }

    /** Eventos que acontecem hoje (inclusive os que começaram ontem e ainda não terminaram). */
    @Transactional(readOnly = true)
    public List<EventoResponseDTO> getAllHoje() {
        LocalDate hoje = hoje();
        return eventoRepository.findNoPeriodo(hoje.atStartOfDay(), hoje.plusDays(1).atStartOfDay()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Hoje no fuso do estabelecimento. */
    LocalDate hoje() {
        return LocalDate.now(clock.withZone(fusoEstabelecimento));
    }

    /** Agora no fuso do estabelecimento (as datas dos eventos são o horário local da loja). */
    LocalDateTime agora() {
        return LocalDateTime.now(clock.withZone(fusoEstabelecimento));
    }

    @Transactional(readOnly = true)
    public Evento getById(Long id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado"));
    }

    @Transactional(readOnly = true)
    public EventoResponseDTO getResponseById(Long id) {
        return toResponse(getById(id));
    }

    @Transactional
    public EventoResponseDTO create(EventoRequestDTO request) {
        Evento evento = fromRequest(request);
        exigirLojaAberta(evento.getDataHoraInicio());
        evento.setId(null);
        return toResponse(eventoRepository.save(evento));
    }

    @Transactional
    public EventoResponseDTO update(Long id, EventoRequestDTO request) {
        Evento evento = getById(id);
        Evento updated = fromRequest(request);
        // Só valida o dia quando ele muda: um evento antigo em um dia que passou a ser fechado continua editável.
        if (!updated.getDataHoraInicio().toLocalDate().equals(evento.getDataHoraInicio().toLocalDate())) {
            // Os inscritos pagaram a inscrição na comanda daquele dia: mudar o dia os deixaria cobrados por um evento
            // de outra data. O horário dentro do mesmo dia pode mudar.
            if (participanteRepository.existsByEventoId(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O evento tem participantes. Remova os participantes antes de mudar o dia do evento");
            }
            exigirLojaAberta(updated.getDataHoraInicio());
        }
        evento.setNome(updated.getNome());
        evento.setTipo(updated.getTipo());
        evento.setDescricao(updated.getDescricao());
        evento.setDataHoraInicio(updated.getDataHoraInicio());
        evento.setDataHoraFim(updated.getDataHoraFim());
        evento.setValorInscricao(updated.getValorInscricao());
        if (updated.getVagas() != null) {
            long participantes = participanteRepository.countByEventoId(id);
            if (participantes > updated.getVagas()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O evento já tem " + participantes
                        + " participantes: informe pelo menos " + participantes + " vagas");
            }
        }
        evento.setVagas(updated.getVagas());
        return toResponse(eventoRepository.save(evento));
    }

    @Transactional
    public void delete(Long id) {
        Evento evento = getById(id);
        if (participanteRepository.existsByEventoId(id)) {
            // Evento que ainda não terminou: os inscritos têm a inscrição na comanda; a equipe os remove antes
            // (o que acerta as comandas abertas).
            if (evento.getDataHoraFim().isAfter(agora())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O evento tem participantes. Use \"Remover todos\" antes de excluir o evento");
            }
            // Evento encerrado: os clientes participaram e as inscrições cobradas continuam nas comandas.
            participanteRepository.deleteByEventoId(id);
        }
        eventoRepository.delete(evento);
    }

    private Evento fromRequest(EventoRequestDTO request) {
        if (request == null || request.getNome() == null || request.getNome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do evento é obrigatório");
        }
        String nome = request.getNome().trim();
        if (nome.length() > TAMANHO_MAXIMO_NOME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O nome do evento deve ter no máximo " + TAMANHO_MAXIMO_NOME + " caracteres");
        }
        String descricao = trimToNull(request.getDescricao());
        if (descricao != null && descricao.length() > TAMANHO_MAXIMO_DESCRICAO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A descrição do evento deve ter no máximo " + TAMANHO_MAXIMO_DESCRICAO + " caracteres");
        }
        // Minutos bastam para o calendário; segundos e frações vindos do navegador são descartados antes de validar.
        LocalDateTime inicio = semSegundos(request.getDataHoraInicio());
        LocalDateTime fim = semSegundos(request.getDataHoraFim());
        if (inicio == null || fim == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o início e o fim do evento");
        }
        if (!fim.isAfter(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O fim do evento deve ser depois do início");
        }
        if (Duration.between(inicio, fim).compareTo(DURACAO_MAXIMA) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O evento deve durar no máximo " + DURACAO_MAXIMA.toDays() + " dias");
        }
        if (request.getValorInscricao() != null && request.getValorInscricao() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor da inscrição deve ser maior ou igual a zero");
        }
        if (request.getVagas() != null && request.getVagas() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O número de vagas deve ser maior que zero");
        }
        return Evento.builder()
                .nome(nome)
                .tipo(request.getTipo() != null ? request.getTipo() : TipoEventoEnum.OUTROS)
                .descricao(descricao)
                .dataHoraInicio(inicio)
                .dataHoraFim(fim)
                // Zero e vazio significam o mesmo: evento gratuito.
                .valorInscricao(request.getValorInscricao() == null || request.getValorInscricao() == 0
                        ? null : request.getValorInscricao())
                .vagas(request.getVagas())
                .build();
    }

    /** Não há eventos nos dias sem expediente (parâmetro LOJA_DIAS_FECHADOS). */
    private void exigirLojaAberta(LocalDateTime inicio) {
        DayOfWeek dia = inicio.getDayOfWeek();
        if (configuracaoEventoService.getDiasFechados().contains(dia)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A loja não abre nesse dia (" + dia.getDisplayName(TextStyle.FULL, PT_BR) + ")");
        }
    }

    private static LocalDateTime semSegundos(LocalDateTime dataHora) {
        return dataHora == null ? null : dataHora.truncatedTo(ChronoUnit.MINUTES);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private EventoResponseDTO toResponse(Evento evento) {
        return EventoResponseDTO.builder()
                .id(evento.getId())
                .nome(evento.getNome())
                .tipo(evento.getTipo())
                .descricao(evento.getDescricao())
                .dataHoraInicio(evento.getDataHoraInicio())
                .dataHoraFim(evento.getDataHoraFim())
                .valorInscricao(evento.getValorInscricao())
                .vagas(evento.getVagas())
                .build();
    }
}
