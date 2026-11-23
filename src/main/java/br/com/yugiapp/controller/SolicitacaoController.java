package br.com.yugiapp.controller;

import br.com.yugiapp.config.websocket.WebSocketSecurityInterceptor;
import br.com.yugiapp.dto.SolicitacaoFilterRequestDTO;
import br.com.yugiapp.dto.SolicitacaoRequestDTO;
import br.com.yugiapp.enums.StatusSolicitacaoEnum;
import br.com.yugiapp.model.Solicitacao;
import br.com.yugiapp.service.ComandaNotificacaoService;
import br.com.yugiapp.service.SolicitacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api/solicitacao")
@RequiredArgsConstructor
public class SolicitacaoController {

    /** Limite de solicitações de comanda por conexão do WebSocket, contra o envio em massa. */
    static final int MAXIMO_SOLICITACOES_POR_CONEXAO = 5;
    private static final String ATRIBUTO_SOLICITACOES = "covil.solicitacoesEnviadas";

    private final SolicitacaoService solicitacaoService;

    private final SimpMessagingTemplate messagingTemplate;

    private final ComandaNotificacaoService comandaNotificacaoService;

    @GetMapping
    public List<Solicitacao> getAll(SolicitacaoFilterRequestDTO solicitacaoFilterRequestDTO) {
        return solicitacaoService.getAllByFilters(solicitacaoFilterRequestDTO);
    }

    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String simpSessionId = Optional.ofNullable(headerAccessor.getSessionId()).orElseThrow();
        String destination = headerAccessor.getDestination();
        // A inscrição em /topic/session/* é reescrita para o tópico exclusivo da sessão (WebSocketSecurityInterceptor).
        if (WebSocketSecurityInterceptor.TOPICO_SESSAO.equals(destination)) {
            messagingTemplate.convertAndSend(WebSocketSecurityInterceptor.PREFIXO_SESSAO + simpSessionId, simpSessionId);
            return;
        }

        if (destination != null && destination.startsWith("/topic/comanda/")) {
            String requestId = destination.substring("/topic/comanda/".length());
            solicitacaoService.findBySessionId(requestId)
                    .filter(solicitacao -> !StatusSolicitacaoEnum.EM_ANALISE.getValue().equals(solicitacao.getStatus()))
                    .ifPresent(solicitacao -> messagingTemplate.convertAndSend(destination, solicitacaoService.comToken(solicitacao)));
        }
    }

    /**
     * Nova solicitação de comanda feita pelo app (sem login). A equipe é avisada pelo tópico /topic/solicitacoes,
     * que leva apenas o resumo: os dados de contato e o requestId ficam na consulta REST, que exige permissão.
     */
    @MessageMapping("/solicitacao/solicitar")
    public void solicitarComanda(SolicitacaoRequestDTO solicitacaoRequestDTO, SimpMessageHeaderAccessor headerAccessor) {
        if (!dentroDoLimiteDaConexao(headerAccessor)) {
            log.warn("Sessão {} excedeu o limite de {} solicitações de comanda", headerAccessor.getSessionId(),
                    MAXIMO_SOLICITACOES_POR_CONEXAO);
            return;
        }
        solicitacaoService.solicitar(solicitacaoRequestDTO)
                .ifPresent(solicitacao -> messagingTemplate.convertAndSend("/topic/solicitacoes", resumo(solicitacao)));
    }

    /** Solicitação inválida enviada pelo WebSocket: não há resposta ao cliente, apenas o registro no log. */
    @MessageExceptionHandler(ResponseStatusException.class)
    public void handleSolicitacaoInvalida(ResponseStatusException exception) {
        log.warn("Solicitação de comanda recusada: {}", exception.getReason());
    }

    /**
     * Aceite e recusa são REST (e não mensagens do websocket) para passarem pela autorização por permissão.
     * O resultado segue para o app pelo tópico /topic/comanda/{sessionId}; no aceite ele leva o token do visitante.
     */
    @PostMapping("/{id}/aceitar")
    public void aceitarSolicitacao(@PathVariable Long id) {
        Solicitacao solicitacao = solicitacaoService.getById(id);
        Solicitacao aceita = solicitacaoService.aceitarSolicitacao(solicitacao);
        messagingTemplate.convertAndSend("/topic/comanda/" + solicitacao.getSessionId(), aceita);
        // A comanda aberta no aceite aparece na hora na tela de comandas.
        if (aceita.getComanda() != null) {
            comandaNotificacaoService.notificarAlteracao(aceita.getComanda().getNumero());
        }
    }

    @PostMapping("/{id}/recusar")
    public void recusarSolicitacao(@PathVariable Long id) {
        Solicitacao solicitacao = solicitacaoService.getById(id);
        messagingTemplate.convertAndSend("/topic/comanda/" + solicitacao.getSessionId(), solicitacaoService.recusarSolicitacao(solicitacao));
    }

    private boolean dentroDoLimiteDaConexao(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> atributos = headerAccessor.getSessionAttributes();
        if (atributos == null) {
            return true;
        }
        AtomicInteger enviadas = (AtomicInteger) atributos.computeIfAbsent(ATRIBUTO_SOLICITACOES, chave -> new AtomicInteger());
        return enviadas.incrementAndGet() <= MAXIMO_SOLICITACOES_POR_CONEXAO;
    }

    private Map<String, Object> resumo(Solicitacao solicitacao) {
        return Map.of("id", solicitacao.getId(), "nome", solicitacao.getNome(), "status", solicitacao.getStatus());
    }

}
