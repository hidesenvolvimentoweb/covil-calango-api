package br.com.yugiapp.controller;

import br.com.yugiapp.config.security.AcessoService;
import br.com.yugiapp.dto.PedidoRequestDTO;
import br.com.yugiapp.dto.PedidoFilterRequestDTO;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.service.ComandaNotificacaoService;
import br.com.yugiapp.service.PedidoService;
import br.com.yugiapp.service.WhatsappService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static br.com.yugiapp.config.security.Permissoes.*;

@RestController
@RequestMapping("/api/pedido")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WhatsappService whatsappService;
    private final AcessoService acessoService;
    private final ComandaNotificacaoService comandaNotificacaoService;

    @PostMapping
    public void salvar(@RequestBody PedidoRequestDTO pedido) {
        // O visitante só faz pedidos na própria comanda.
        acessoService.validarComanda(pedido.getComanda());
        // TODO: VALIDAR SE A COZINHA ESTÁ ABERTA
        // TODO: CRIAR MÓDULO DE COZINHA COM STATUS ABERTA E FECHADA PARA RECEBER PEDIDOS
        Pedido novoPedido = pedidoService.save(pedido);
        messagingTemplate.convertAndSend("/topic/pedidos", novoPedido);
        comandaNotificacaoService.notificarAlteracao(numeroDaComanda(novoPedido));
    }

    @DeleteMapping(path = "{id}")
    public void delete(@PathVariable Long id) {
        comandaNotificacaoService.notificarAlteracao(numeroDaComanda(pedidoService.delete(id)));
    }

    @GetMapping
    public List<Pedido> getAllByFilters(PedidoFilterRequestDTO pedidoFilterRequestDTO) {
        // O visitante só lista os pedidos da própria comanda; a equipe lista de qualquer uma.
        if (pedidoFilterRequestDTO.getComanda() == null) {
            acessoService.comandaDoVisitante().ifPresent(pedidoFilterRequestDTO::setComanda);
        }
        acessoService.validarComanda(pedidoFilterRequestDTO.getComanda(), WEB_PEDIDOS, WEB_DASHBOARD, WEB_COMANDAS);
        return pedidoService.getAllByFilters(pedidoFilterRequestDTO);
    }

    @GetMapping(path = "{id}")
    public Pedido getById(@PathVariable Long id) {
        return pedidoService.getById(id);
    }

    // TODO: ADICIONAR PARAMETRO E TRANSFORMAR ESSES 4 EM APENAS 1

    @PatchMapping(path = "/aceitar/{id}")
    public void aceitarPedido(@PathVariable Long id) {
        notificarAlteracaoStatus(pedidoService.alterarStatus(id, StatusPedidoEnum.EM_ANDAMENTO));
    }

    @PatchMapping(path = "/cancelar/{id}")
    public void cancelarPedido(@PathVariable Long id) {
        notificarAlteracaoStatus(pedidoService.alterarStatus(id, StatusPedidoEnum.CANCELADO));
    }

    @PatchMapping(path = "/concluir/{id}")
    public void concluirPedido(@PathVariable Long id) {
        notificarAlteracaoStatus(pedidoService.alterarStatus(id, StatusPedidoEnum.PRONTO));
    }

    @PatchMapping(path = "/finalizar/{id}")
    public void finalizarPedido(@PathVariable Long id) {
        notificarAlteracaoStatus(pedidoService.alterarStatus(id, StatusPedidoEnum.FINALIZADO));
    }

    /**
     * Notifica o cliente dono da comanda sobre a alteração de status do pedido:
     * em tempo real no app (tópico /topic/pedidos/comanda/{numeroComanda}) e,
     * se ele consentiu, pelo WhatsApp. A tela de comandas também é avisada (o cancelamento muda o valor).
     */
    private void notificarAlteracaoStatus(Pedido pedido) {
        String numero = numeroDaComanda(pedido);
        if (numero == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/pedidos/comanda/" + numero, pedido);
        whatsappService.notificarAlteracaoStatusPedido(pedido);
        comandaNotificacaoService.notificarAlteracao(numero);
    }

    private static String numeroDaComanda(Pedido pedido) {
        return pedido == null || pedido.getComanda() == null ? null : pedido.getComanda().getNumero();
    }
}
