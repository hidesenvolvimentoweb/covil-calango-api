package br.com.yugiapp.controller;

import br.com.yugiapp.dto.SolicitacaoFilterRequestDTO;
import br.com.yugiapp.dto.SolicitacaoRequestDTO;
import br.com.yugiapp.enums.StatusSolicitacaoEnum;
import br.com.yugiapp.model.Solicitacao;
import br.com.yugiapp.service.SolicitacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/solicitacao")
@RequiredArgsConstructor
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public List<Solicitacao> getAll(SolicitacaoFilterRequestDTO solicitacaoFilterRequestDTO) {
        return solicitacaoService.getAllByFilters(solicitacaoFilterRequestDTO);
    }

    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String simpSessionId = Optional.ofNullable(headerAccessor.getSessionId()).orElseThrow();
        String destination = headerAccessor.getDestination();
        if ("/topic/session/*".equals(destination)) {
            messagingTemplate.convertAndSend("/topic/session/" + simpSessionId, simpSessionId);
            return;
        }

        if (destination != null && destination.startsWith("/topic/comanda/")) {
            String requestId = destination.substring("/topic/comanda/".length());
            solicitacaoService.findBySessionId(requestId)
                    .filter(solicitacao -> !StatusSolicitacaoEnum.EM_ANALISE.getValue().equals(solicitacao.getStatus()))
                    .ifPresent(solicitacao -> messagingTemplate.convertAndSend(destination, solicitacaoService.comToken(solicitacao)));
        }
    }

    @MessageMapping("/solicitacao/solicitar")
    public void solicitarComanda(SolicitacaoRequestDTO solicitacaoRequestDTO, Message<String> message) {
        messagingTemplate.convertAndSend("/topic/solicitacoes", solicitacaoService.save(
                solicitacaoService.buildNovaSolicitacao(solicitacaoRequestDTO, (String) message.getHeaders().get("simpSessionId"))));
    }

    /**
     * Aceite e recusa são REST (e não mensagens do websocket) para passarem pela autorização por permissão.
     * O resultado segue para o app pelo tópico /topic/comanda/{sessionId}; no aceite ele leva o token do visitante.
     */
    @PostMapping("/{id}/aceitar")
    public void aceitarSolicitacao(@PathVariable Long id) {
        Solicitacao solicitacao = solicitacaoService.getById(id);
        messagingTemplate.convertAndSend("/topic/comanda/" + solicitacao.getSessionId(), solicitacaoService.aceitarSolicitacao(solicitacao));
    }

    @PostMapping("/{id}/recusar")
    public void recusarSolicitacao(@PathVariable Long id) {
        Solicitacao solicitacao = solicitacaoService.getById(id);
        messagingTemplate.convertAndSend("/topic/comanda/" + solicitacao.getSessionId(), solicitacaoService.recusarSolicitacao(solicitacao));
    }

}
