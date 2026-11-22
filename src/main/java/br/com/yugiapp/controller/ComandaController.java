package br.com.yugiapp.controller;

import br.com.yugiapp.config.security.AcessoService;
import br.com.yugiapp.dto.ComandaFilterRequestDTO;
import br.com.yugiapp.dto.ComandaResponseDTO;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.service.ComandaService;
import br.com.yugiapp.service.PagamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;

import static br.com.yugiapp.config.security.Permissoes.*;

@Slf4j
@RestController
@RequestMapping("/api/comanda")
@RequiredArgsConstructor
public class ComandaController {

    public static final String TOPICO_COMANDAS = "/topic/comandas/";

    private final ComandaService comandaService;
    private final PagamentoService pagamentoService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AcessoService acessoService;

    /**
     * Ao se inscrever no tópico da comanda, o app recebe na hora o estado da comanda caso ela já
     * tenha sido fechada (ex.: o aparelho estava offline no momento do fechamento).
     */
    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        String destination = SimpMessageHeaderAccessor.wrap(event.getMessage()).getDestination();
        if (destination == null || !destination.startsWith(TOPICO_COMANDAS)) {
            return;
        }
        String numero = destination.substring(TOPICO_COMANDAS.length());
        comandaService.findByNumero(numero)
                .filter(comanda -> StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus()))
                .ifPresent(comanda -> messagingTemplate.convertAndSend(destination, comandaService.getResponseDTOByNumero(numero)));
    }

    @GetMapping
    public List<ComandaResponseDTO> getByFilters(ComandaFilterRequestDTO comandaFilterRequestDTO) {
        return comandaService.getAllByFilters(comandaFilterRequestDTO);
    }

    @GetMapping(path = "/gerar/{nome}")
    public ComandaResponseDTO gerarComandaEntrada(@PathVariable String nome) {
        return comandaService.gerarComandaDTO(nome);
    }

    /**
     * Fecha a comanda e avisa o app em tempo real (tópico /topic/comandas/{numero}),
     * que encerra a sessão do cliente e volta para a geração de comanda.
     * Um PIX pendente é cancelado antes (ex.: o cliente pagou no balcão), para não ser pago depois.
     */
    @PutMapping(path = "/fechar/{numero}")
    public void fecharComanda(@PathVariable String numero) {
        // Rota exclusiva da equipe (JwtSecurityConfig); o cliente fecha a comanda pelo pagamento.
        if (pagamentoService.possuiPixPendente(numero)) {
            try {
                pagamentoService.cancelarPixPendente(numero);
            } catch (RuntimeException exception) {
                // A reconciliação encerra o PIX quando ele expirar; o fechamento não depende do provedor.
                log.warn("Não foi possível cancelar o PIX pendente da comanda {} ao fechá-la: {}", numero, exception.getMessage());
            }
        }
        comandaService.alterarStatus(numero, StatusComandaEnum.FECHADA);
        messagingTemplate.convertAndSend(TOPICO_COMANDAS + numero, comandaService.getResponseDTOByNumero(numero));
    }

    @GetMapping(path = "{numero}")
    public ComandaResponseDTO getByNumero(@PathVariable String numero) {
        acessoService.validarComanda(numero, WEB_COMANDAS, WEB_DASHBOARD);
        return comandaService.getResponseDTOByNumero(numero);
    }

}
