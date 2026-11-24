package br.com.yugiapp.controller;

import br.com.yugiapp.config.security.AcessoService;
import br.com.yugiapp.config.security.Permissoes;
import br.com.yugiapp.dto.EventoConfiguracaoResponseDTO;
import br.com.yugiapp.dto.EventoFilterRequestDTO;
import br.com.yugiapp.dto.EventoParticipanteResponseDTO;
import br.com.yugiapp.dto.EventoRequestDTO;
import br.com.yugiapp.dto.EventoResponseDTO;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.EventoParticipante;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.service.ComandaNotificacaoService;
import br.com.yugiapp.service.ConfiguracaoEventoService;
import br.com.yugiapp.service.EventoService;
import br.com.yugiapp.service.ParticipacaoEventoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static br.com.yugiapp.config.security.Permissoes.WEB_EVENTOS;

/**
 * Eventos da loja. Consulta: equipe (web-eventos) e clientes do app (app-eventos); cadastro, alteração, exclusão e
 * remoção de participantes: somente a equipe; participar e sair: o cliente do app, com a própria comanda (ver
 * JwtSecurityConfig).
 */
@RestController
@RequestMapping("/api/evento")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final ConfiguracaoEventoService configuracaoEventoService;
    private final ParticipacaoEventoService participacaoEventoService;
    private final AcessoService acessoService;
    private final ComandaNotificacaoService comandaNotificacaoService;
    private final SimpMessagingTemplate messagingTemplate;

    /** Eventos do período exibido no calendário ({@code ?inicio=...&fim=...}, data e hora sem fuso). */
    @GetMapping
    public List<EventoResponseDTO> getAll(EventoFilterRequestDTO filter) {
        return participacaoEventoService.comParticipacao(eventoService.getAll(filter), comandaDoVisitante());
    }

    /** Eventos acontecendo hoje, no fuso do estabelecimento. */
    @GetMapping("/hoje")
    public List<EventoResponseDTO> getAllHoje() {
        return participacaoEventoService.comParticipacao(eventoService.getAllHoje(), comandaDoVisitante());
    }

    /** Funcionamento da loja para o cadastro e o calendário (horário de abertura, dias fechados etc.). */
    @GetMapping("/configuracao")
    public EventoConfiguracaoResponseDTO getConfiguracao() {
        return configuracaoEventoService.getConfiguracao();
    }

    @GetMapping(path = "{id}")
    public EventoResponseDTO getById(@PathVariable Long id) {
        return participacaoEventoService.comParticipacao(eventoService.getResponseById(id), comandaDoVisitante());
    }

    @PostMapping
    public EventoResponseDTO salvar(@RequestBody EventoRequestDTO evento) {
        return eventoService.create(evento);
    }

    @PutMapping(path = "{id}")
    public EventoResponseDTO atualizar(@PathVariable Long id, @RequestBody EventoRequestDTO evento) {
        return eventoService.update(id, evento);
    }

    @DeleteMapping(path = "{id}")
    public void delete(@PathVariable Long id) {
        eventoService.delete(id);
    }

    /** Participantes do evento; o número da comanda só vai para a equipe. */
    @GetMapping(path = "{id}/participantes")
    public List<EventoParticipanteResponseDTO> getParticipantes(@PathVariable Long id) {
        boolean equipe = acessoService.possuiAlguma(Permissoes.exigir(WEB_EVENTOS));
        return participacaoEventoService.getParticipantes(id, equipe, comandaDoVisitante());
    }

    /** O cliente participa de um evento de hoje com a própria comanda (a inscrição paga entra na comanda). */
    @PostMapping(path = "{id}/participacao")
    public void participar(@PathVariable Long id) {
        String comanda = comandaDoVisitante();
        if (comanda == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gere uma comanda para participar dos eventos");
        }
        notificar(participacaoEventoService.participar(id, comanda), false);
    }

    /** O cliente sai do evento: o valor da inscrição sai da comanda. */
    @DeleteMapping(path = "{id}/participacao")
    public void sair(@PathVariable Long id) {
        String comanda = comandaDoVisitante();
        if (comanda == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Você não está participando deste evento");
        }
        notificar(participacaoEventoService.sair(id, comanda), true);
    }

    /** A equipe remove um participante (e o valor da inscrição da comanda, se ela ainda estiver aberta). */
    @DeleteMapping(path = "{id}/participantes/{participanteId}")
    public void removerParticipante(@PathVariable Long id, @PathVariable Long participanteId) {
        notificar(participacaoEventoService.removerParticipante(id, participanteId), true);
    }

    /** A equipe remove todos os participantes (ex.: antes de excluir o evento), com as mesmas regras de um só. */
    @DeleteMapping(path = "{id}/participantes")
    public void removerTodos(@PathVariable Long id) {
        participacaoEventoService.removerTodos(id).forEach(participante -> notificar(participante, true));
    }

    private String comandaDoVisitante() {
        return acessoService.comandaDoVisitante().orElse(null);
    }

    /**
     * Avisa a tela de comandas da equipe e, quando a inscrição sai da comanda, o app do cliente (tópico dos pedidos
     * da comanda): o pedido chega como cancelado e deixa de entrar no total.
     */
    private void notificar(EventoParticipante participante, boolean removido) {
        String numero = participante.getComanda().getNumero();
        Pedido pedido = participante.getPedido();
        if (removido && pedido != null && !StatusComandaEnum.FECHADA.getValue().equals(participante.getComanda().getStatus())) {
            pedido.setStatus(StatusPedidoEnum.CANCELADO);
            messagingTemplate.convertAndSend("/topic/pedidos/comanda/" + numero, pedido);
        }
        comandaNotificacaoService.notificarAlteracao(numero);
    }
}
