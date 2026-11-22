package br.com.yugiapp.controller;

import br.com.yugiapp.config.security.AcessoService;
import br.com.yugiapp.dto.PagamentoResponseDTO;
import br.com.yugiapp.service.PagamentoService;
import br.com.yugiapp.service.pagamento.MercadoPagoWebhookValidator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

import static br.com.yugiapp.config.security.Permissoes.*;

/**
 * Pagamento da comanda. O visitante só acessa a própria comanda (AcessoService); a equipe acessa qualquer uma.
 * <p>
 * Fluxo do PIX no app: {@code POST /comanda/{numero}/pix} devolve o QR Code e o "copia e cola"; o app se inscreve em
 * {@code /topic/pagamentos/{numero}} e recebe o pagamento aprovado. Com a aprovação, a comanda é fechada e
 * {@code /topic/comandas/{numero}} é avisado, como no fechamento pela equipe.
 */
@Slf4j
@RestController
@RequestMapping("/api/pagamento")
@RequiredArgsConstructor
public class PagamentoController {

    private static final Pattern ID_PAGAMENTO_VALIDO = Pattern.compile("[0-9A-Za-z-]{1,64}");

    private final PagamentoService pagamentoService;
    private final AcessoService acessoService;
    private final MercadoPagoWebhookValidator mercadoPagoWebhookValidator;

    /** Gera o PIX da comanda (ou devolve o PIX pendente, se ainda for válido para o valor atual). */
    @PostMapping("/comanda/{numero}/pix")
    public PagamentoResponseDTO gerarPix(@PathVariable String numero) {
        acessoService.validarComanda(numero, WEB_COMANDAS);
        return pagamentoService.gerarPix(numero);
    }

    /** Último pagamento da comanda; 204 se ainda não houve tentativa. */
    @GetMapping("/comanda/{numero}")
    public ResponseEntity<PagamentoResponseDTO> consultar(@PathVariable String numero) {
        acessoService.validarComanda(numero, WEB_COMANDAS, WEB_DASHBOARD);
        return pagamentoService.consultarUltimo(numero)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Cancela o PIX pendente (o cliente desistiu ou quer adicionar pedidos). */
    @DeleteMapping("/comanda/{numero}/pix")
    public PagamentoResponseDTO cancelarPix(@PathVariable String numero) {
        acessoService.validarComanda(numero, WEB_COMANDAS);
        return pagamentoService.cancelarPixPendente(numero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Não há PIX pendente para esta comanda"));
    }

    /**
     * Notificações (Webhooks) do Mercado Pago. Rota pública: a assinatura é conferida quando
     * MERCADOPAGO_WEBHOOK_SECRET está configurado e o pagamento é sempre consultado no Mercado Pago.
     * Responde 200 rapidamente; um erro (5xx) faz o Mercado Pago reenviar a notificação.
     */
    @PostMapping("/webhook/mercadopago")
    public ResponseEntity<Void> webhookMercadoPago(@RequestParam(name = "data.id", required = false) String dataIdParametro,
                                                   @RequestParam(name = "type", required = false) String tipoParametro,
                                                   @RequestHeader(name = "x-signature", required = false) String assinatura,
                                                   @RequestHeader(name = "x-request-id", required = false) String requestId,
                                                   @RequestBody(required = false) JsonNode corpo) {
        String tipo = tipoParametro != null ? tipoParametro : texto(corpo == null ? null : corpo.path("type"));
        String dataId = dataIdParametro != null ? dataIdParametro : texto(corpo == null ? null : corpo.path("data").path("id"));
        if (!"payment".equals(tipo) || dataId == null || !ID_PAGAMENTO_VALIDO.matcher(dataId).matches()) {
            return ResponseEntity.ok().build();
        }
        if (!mercadoPagoWebhookValidator.validar(assinatura, requestId, dataId)) {
            log.warn("Webhook do Mercado Pago com assinatura inválida recusado (pagamento {}, request-id {})", dataId, requestId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        pagamentoService.processarNotificacao(dataId);
        return ResponseEntity.ok().build();
    }

    private static String texto(JsonNode no) {
        return no == null || no.isMissingNode() || no.isNull() ? null : no.asText();
    }
}
