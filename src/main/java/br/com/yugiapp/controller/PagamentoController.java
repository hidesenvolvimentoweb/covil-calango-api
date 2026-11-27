package br.com.yugiapp.controller;

import br.com.yugiapp.config.security.AcessoService;
import br.com.yugiapp.dto.PagamentoCartaoRequestDTO;
import br.com.yugiapp.dto.PagamentoConfiguracaoDTO;
import br.com.yugiapp.dto.PagamentoResponseDTO;
import br.com.yugiapp.service.ComandaNotificacaoService;
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
    private final ComandaNotificacaoService comandaNotificacaoService;

    /** Gera o PIX da comanda (ou devolve o PIX pendente, se ainda for válido para o valor atual). */
    @PostMapping("/comanda/{numero}/pix")
    public PagamentoResponseDTO gerarPix(@PathVariable String numero) {
        acessoService.validarComanda(numero, WEB_COMANDAS);
        return pagamentoService.gerarPix(numero);
    }

    /** Formas de pagamento disponíveis no app e a chave pública do formulário de cartão. */
    @GetMapping("/configuracao")
    public PagamentoConfiguracaoDTO configuracao() {
        return pagamentoService.getConfiguracao();
    }

    /**
     * Paga a comanda à vista no cartão de crédito ou débito. Só o cliente, pelo app: a equipe não digita o cartão do cliente.
     * O resultado vem na resposta (e também em /topic/pagamentos/{numero}).
     */
    @PostMapping("/comanda/{numero}/cartao")
    public PagamentoResponseDTO pagarComCartao(@PathVariable String numero, @RequestBody PagamentoCartaoRequestDTO request) {
        acessoService.validarComanda(numero);
        return pagamentoService.pagarComCartao(numero, request);
    }

    /** Link para o cliente pagar na própria conta do Mercado Pago (cartão salvo lá). Só o cliente, pelo app. */
    @PostMapping("/comanda/{numero}/mercado-pago")
    public PagamentoResponseDTO pagarComMercadoPago(@PathVariable String numero) {
        acessoService.validarComanda(numero);
        return pagamentoService.gerarPagamentoMercadoPago(numero);
    }

    /** Estorna um pagamento aprovado e reabre a comanda. Somente o administrador (JwtSecurityConfig). */
    @PostMapping("/{id}/estorno")
    public void estornar(@PathVariable Long id) {
        String numero = pagamentoService.estornar(id);
        comandaNotificacaoService.notificarAlteracao(numero);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Não há pagamento pendente para esta comanda"));
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

    /**
     * Página de retorno do Checkout Pro (back_urls), aberta no navegador do celular ao fim do pagamento: reabre o app
     * em "Minha comanda" pelo deep link. Não altera nada: a confirmação vem só do webhook e da consulta ao provedor.
     */
    @GetMapping(value = "/retorno/mercadopago", produces = "text/html;charset=UTF-8")
    public String retornoMercadoPago(@RequestParam(name = "resultado", required = false) String resultado) {
        String mensagem = switch (resultado == null ? "" : resultado) {
            case "aprovado" -> "Pagamento aprovado! Sua comanda será fechada em instantes.";
            case "pendente" -> "Pagamento em análise. Avisaremos no app assim que for confirmado.";
            case "recusado" -> "O pagamento não foi concluído. Volte ao app para tentar de novo ou escolher outra forma.";
            default -> "Volte ao app para acompanhar o pagamento da sua comanda.";
        };
        return PAGINA_RETORNO.formatted(DEEP_LINK_APP, DEEP_LINK_APP, mensagem, DEEP_LINK_APP);
    }

    /** Esquema registrado no app (cordova-plugin-customurlscheme, URL_SCHEME=covilcalango). */
    static final String DEEP_LINK_APP = "covilcalango://minha-comanda";

    private static final String PAGINA_RETORNO = """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta http-equiv="refresh" content="1;url=%s">
              <title>Covil do Calango</title>
              <style>
                body { font-family: system-ui, sans-serif; background: #111; color: #eee; margin: 0;
                       min-height: 100vh; display: flex; align-items: center; justify-content: center; text-align: center; }
                main { padding: 24px; max-width: 360px; }
                a { display: inline-block; margin-top: 16px; padding: 12px 20px; border-radius: 8px;
                    background: #2e7d32; color: #fff; text-decoration: none; font-weight: 600; }
              </style>
              <script>setTimeout(function () { window.location.href = '%s'; }, 300);</script>
            </head>
            <body>
              <main>
                <h1>Covil do Calango</h1>
                <p>%s</p>
                <a href="%s">Voltar ao app</a>
              </main>
            </body>
            </html>
            """;

    private static String texto(JsonNode no) {
        return no == null || no.isMissingNode() || no.isNull() ? null : no.asText();
    }
}
