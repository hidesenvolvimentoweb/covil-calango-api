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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Participação nos eventos pelo app. O cliente participa com a comanda aberta e apenas dos eventos de hoje (no fuso
 * do estabelecimento) que ainda não terminaram. Nos eventos pagos, a inscrição entra na comanda como um pedido
 * "Evento - nome do evento" com o valor da inscrição; sair do evento (ou ser removido pela equipe) tira esse valor.
 */
@Service
public class ParticipacaoEventoService {

    static final String PREFIXO_PEDIDO = "Evento - ";
    /** Tamanho da coluna DESCRICAO do pedido. */
    private static final int TAMANHO_MAXIMO_DESCRICAO_PEDIDO = 255;

    private final EventoRepository eventoRepository;
    private final EventoParticipanteRepository participanteRepository;
    private final ComandaService comandaService;
    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ZoneId fusoEstabelecimento;
    private final Clock clock;

    /** Construtor usado pelo Spring (com dois construtores, ele precisa da indicação explícita). */
    @Autowired
    public ParticipacaoEventoService(EventoRepository eventoRepository, EventoParticipanteRepository participanteRepository,
                                     ComandaService comandaService, PedidoRepository pedidoRepository,
                                     PagamentoRepository pagamentoRepository,
                                     @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(eventoRepository, participanteRepository, comandaService, pedidoRepository, pagamentoRepository,
                ZoneId.of(fusoHorario), Clock.systemUTC());
    }

    ParticipacaoEventoService(EventoRepository eventoRepository, EventoParticipanteRepository participanteRepository,
                              ComandaService comandaService, PedidoRepository pedidoRepository,
                              PagamentoRepository pagamentoRepository, ZoneId fusoEstabelecimento, Clock clock) {
        this.eventoRepository = eventoRepository;
        this.participanteRepository = participanteRepository;
        this.comandaService = comandaService;
        this.pedidoRepository = pedidoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.fusoEstabelecimento = fusoEstabelecimento;
        this.clock = clock;
    }

    /** Inscreve a comanda no evento e, se ele for pago, lança a inscrição na comanda. */
    @Transactional
    public EventoParticipante participar(Long eventoId, String numeroComanda) {
        Comanda comanda = comandaAberta(numeroComanda);
        // Bloqueia o evento: duas inscrições simultâneas não ultrapassam o número de vagas.
        Evento evento = eventoRepository.findByIdParaAtualizacao(eventoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado"));
        exigirEventoDeHoje(evento);
        if (participanteRepository.findByEventoIdAndComandaNumero(eventoId, comanda.getNumero()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já está participando deste evento");
        }
        if (evento.getVagas() != null && participanteRepository.countByEventoId(eventoId) >= evento.getVagas()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não há mais vagas para este evento");
        }
        Pedido pedido = null;
        if (evento.getValorInscricao() != null && evento.getValorInscricao() > 0) {
            // O valor do PIX já foi fixado: a inscrição ficaria fora da cobrança.
            if (pagamentoRepository.existsByComandaNumeroAndStatus(comanda.getNumero(), StatusPagamentoEnum.PENDENTE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Há um pagamento PIX em andamento para esta comanda. Cancele o PIX para participar do evento");
            }
            pedido = pedidoRepository.save(Pedido.builder()
                    .dataHora(LocalDateTime.now())
                    // Não passa pela cozinha: entra na comanda já finalizado.
                    .status(StatusPedidoEnum.FINALIZADO)
                    .descricao(descricaoDoPedido(evento))
                    .quantidade(1L)
                    .valorUnitario(evento.getValorInscricao())
                    .comanda(comanda)
                    .build());
        }
        return participanteRepository.save(EventoParticipante.builder()
                .evento(evento)
                .comanda(comanda)
                .pedido(pedido)
                .dataHoraInscricao(LocalDateTime.now())
                .build());
    }

    /** O cliente sai do evento: a inscrição e o valor dela saem da comanda. */
    @Transactional
    public EventoParticipante sair(Long eventoId, String numeroComanda) {
        EventoParticipante participante = participanteRepository.findByEventoIdAndComandaNumero(eventoId, numeroComanda)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Você não está participando deste evento"));
        if (comandaFechada(comandaService.travarParaAtualizacao(numeroComanda))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sua comanda foi finalizada");
        }
        remover(participante);
        return participante;
    }

    /**
     * A equipe remove um participante. Com a comanda aberta, o valor da inscrição sai dela; com a comanda já fechada
     * (e paga), só a participação é removida e o valor cobrado continua no histórico.
     */
    @Transactional
    public EventoParticipante removerParticipante(Long eventoId, Long participanteId) {
        EventoParticipante participante = participanteRepository.findByIdAndEventoId(participanteId, eventoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participante não encontrado"));
        removerPelaEquipe(participante);
        return participante;
    }

    /**
     * A equipe remove todos os participantes (ex.: para excluir o evento), com as mesmas regras da remoção de um. Se
     * alguma comanda aberta tiver PIX pendente, nada é removido e a mensagem indica a comanda.
     */
    @Transactional
    public List<EventoParticipante> removerTodos(Long eventoId) {
        eventoRepository.findByIdParaAtualizacao(eventoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado"));
        List<EventoParticipante> participantes = participanteRepository.findByEventoIdOrderByDataHoraInscricaoAscIdAsc(eventoId);
        // Bloqueia as comandas sempre na mesma ordem (número), para que duas remoções não se travem mutuamente.
        participantes.stream()
                .sorted(Comparator.comparing(participante -> participante.getComanda().getNumero()))
                .forEach(this::removerPelaEquipe);
        return participantes;
    }

    /**
     * Com a comanda aberta, o valor da inscrição sai dela; com a comanda já fechada (e paga), só a participação é
     * removida e o valor cobrado continua no histórico.
     */
    private void removerPelaEquipe(EventoParticipante participante) {
        if (comandaFechada(comandaService.travarParaAtualizacao(participante.getComanda().getNumero()))) {
            participanteRepository.delete(participante);
        } else {
            remover(participante);
        }
    }

    @Transactional(readOnly = true)
    public List<EventoParticipanteResponseDTO> getParticipantes(Long eventoId, boolean equipe, String comandaDoVisitante) {
        if (!eventoRepository.existsById(eventoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado");
        }
        return participanteRepository.findByEventoIdOrderByDataHoraInscricaoAscIdAsc(eventoId).stream()
                .map(participante -> EventoParticipanteResponseDTO.builder()
                        .id(participante.getId())
                        .nome(participante.getComanda().getNomeCliente())
                        // O número da comanda dá acesso a ela no app: só a equipe o recebe.
                        .comanda(equipe ? participante.getComanda().getNumero() : null)
                        .dataHoraInscricao(participante.getDataHoraInscricao())
                        .voce(comandaDoVisitante != null && comandaDoVisitante.equals(participante.getComanda().getNumero()))
                        .build())
                .toList();
    }

    /** Completa os eventos com o total de participantes e, no app, se a comanda de quem consulta está inscrita. */
    @Transactional(readOnly = true)
    public <T extends Iterable<EventoResponseDTO>> T comParticipacao(T eventos, String comandaDoVisitante) {
        Set<Long> ids = new HashSet<>();
        eventos.forEach(evento -> ids.add(evento.getId()));
        if (ids.isEmpty()) {
            return eventos;
        }
        Map<Long, Long> totais = participanteRepository.contarPorEvento(ids).stream()
                .collect(Collectors.toMap(linha -> (Long) linha[0], linha -> (Long) linha[1]));
        Set<Long> inscritos = comandaDoVisitante == null ? Set.of()
                : new HashSet<>(participanteRepository.findEventosDaComanda(comandaDoVisitante, ids));
        eventos.forEach(evento -> {
            evento.setTotalParticipantes(totais.getOrDefault(evento.getId(), 0L));
            evento.setParticipando(comandaDoVisitante == null ? null : inscritos.contains(evento.getId()));
        });
        return eventos;
    }

    public EventoResponseDTO comParticipacao(EventoResponseDTO evento, String comandaDoVisitante) {
        comParticipacao(List.of(evento), comandaDoVisitante);
        return evento;
    }

    private void remover(EventoParticipante participante) {
        Pedido pedido = participante.getPedido();
        if (pedido != null && pagamentoRepository.existsByComandaNumeroAndStatus(
                participante.getComanda().getNumero(), StatusPagamentoEnum.PENDENTE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Há um pagamento PIX em andamento para a comanda "
                    + participante.getComanda().getNumero() + ". Cancele o pagamento antes de remover a inscrição do evento");
        }
        participanteRepository.delete(participante);
        if (pedido != null) {
            participanteRepository.flush();
            pedidoRepository.delete(pedido);
        }
    }

    /** Comanda bloqueada: o fechamento e a geração do PIX esperam a inscrição terminar (e vice-versa). */
    private Comanda comandaAberta(String numeroComanda) {
        Comanda comanda = null;
        if (numeroComanda != null && comandaService.findByNumero(numeroComanda).isPresent()) {
            comanda = comandaService.travarParaAtualizacao(numeroComanda);
        }
        if (comanda == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gere uma comanda para participar dos eventos");
        }
        if (comandaFechada(comanda)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sua comanda foi finalizada. Gere uma nova comanda para participar dos eventos");
        }
        return comanda;
    }

    private static boolean comandaFechada(Comanda comanda) {
        return StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus());
    }

    /** Só os eventos de hoje que ainda não terminaram (um torneio que vira a noite vale nos dois dias). */
    private void exigirEventoDeHoje(Evento evento) {
        LocalDateTime agora = LocalDateTime.now(clock.withZone(fusoEstabelecimento));
        LocalDate hoje = agora.toLocalDate();
        boolean deHoje = evento.getDataHoraInicio().isBefore(hoje.plusDays(1).atStartOfDay())
                && evento.getDataHoraFim().isAfter(hoje.atStartOfDay());
        if (!deHoje) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Só é possível participar dos eventos de hoje");
        }
        if (!evento.getDataHoraFim().isAfter(agora)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este evento já terminou");
        }
    }

    static String descricaoDoPedido(Evento evento) {
        String descricao = PREFIXO_PEDIDO + evento.getNome();
        return descricao.length() > TAMANHO_MAXIMO_DESCRICAO_PEDIDO
                ? descricao.substring(0, TAMANHO_MAXIMO_DESCRICAO_PEDIDO) : descricao;
    }
}
