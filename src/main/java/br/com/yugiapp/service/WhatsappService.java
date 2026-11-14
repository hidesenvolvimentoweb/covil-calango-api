package br.com.yugiapp.service;

import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Envia notificações ao WhatsApp do cliente através da API do wpp-bot (POST /api/v1/messages).
 * O envio é assíncrono e falhas apenas são registradas em log: a indisponibilidade do bot
 * nunca impede a movimentação do pedido.
 */
@Slf4j
@Service
public class WhatsappService {

    private static final String ENDPOINT_MENSAGENS = "/api/v1/messages";
    private static final int MIN_DIGITOS_CELULAR = 10;
    private static final int MAX_DIGITOS_CELULAR = 13;

    private final String botUrl;
    private final String botToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public WhatsappService(@Value("${app.wpp-bot.url:}") String botUrl,
                           @Value("${app.wpp-bot.token:}") String botToken,
                           ObjectMapper objectMapper) {
        this.botUrl = botUrl.endsWith("/") ? botUrl.substring(0, botUrl.length() - 1) : botUrl;
        this.botToken = botToken;
        this.objectMapper = objectMapper;
    }

    /**
     * Mantém apenas os dígitos do celular informado. Devolve vazio quando o número não tem
     * entre 10 (DDD + fixo) e 13 dígitos (DDI + DDD + celular).
     */
    public static Optional<String> normalizarCelular(String celular) {
        return Optional.ofNullable(celular)
                .map(c -> c.replaceAll("\\D", ""))
                .filter(c -> c.length() >= MIN_DIGITOS_CELULAR && c.length() <= MAX_DIGITOS_CELULAR);
    }

    /**
     * Avisa o cliente sobre a movimentação do pedido, se ele consentiu em receber notificações pelo WhatsApp.
     */
    public void notificarAlteracaoStatusPedido(Pedido pedido) {
        Comanda comanda = pedido.getComanda();
        if (comanda == null || !comanda.isNotificarWhatsapp() || comanda.getCelular() == null) {
            return;
        }
        montarMensagemStatusPedido(pedido).ifPresent(mensagem -> enviarMensagem(comanda.getCelular(), mensagem));
    }

    /**
     * Envia uma mensagem de texto para o celular informado.
     */
    public void enviarMensagem(String celular, String mensagem) {
        if (botUrl.isBlank() || botToken.isBlank()) {
            log.debug("Integração com o wpp-bot não configurada (WPP_BOT_URL/WPP_BOT_API_TOKEN); mensagem não enviada");
            return;
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(botUrl + ENDPOINT_MENSAGENS))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + botToken)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("phone", celular, "message", mensagem))))
                    .build();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.error("Falha ao montar a requisição de envio ao wpp-bot", exception);
            return;
        }

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 201) {
                        log.warn("wpp-bot recusou o envio da mensagem (status {}): {}", response.statusCode(), response.body());
                    }
                })
                .exceptionally(exception -> {
                    log.error("Falha ao enviar mensagem pelo wpp-bot", exception);
                    return null;
                });
    }

    private Optional<String> montarMensagemStatusPedido(Pedido pedido) {
        String saudacao = "Olá, " + pedido.getComanda().getNomeCliente() + "! ";
        String descricaoPedido = "Seu pedido de *" + pedido.getQuantidade() + "x " + pedido.getProduto().getNome() + "*";
        return Optional.ofNullable(switch (pedido.getStatus()) {
            case EM_ANDAMENTO -> saudacao + "👨‍🍳 " + descricaoPedido + " já está sendo preparado.";
            case PRONTO -> saudacao + "😋 " + descricaoPedido + " está pronto para retirada no balcão!";
            case FINALIZADO -> saudacao + "✅ " + descricaoPedido + " foi finalizado. Que tal pedir mais um? 😏";
            case CANCELADO -> saudacao + "😕 " + descricaoPedido + " foi cancelado. Em caso de dúvidas, fale com a gente no balcão.";
            case SOLICITADO -> null;
        });
    }

}
