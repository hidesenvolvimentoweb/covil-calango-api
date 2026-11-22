package br.com.yugiapp.service.pagamento;

import br.com.yugiapp.enums.StatusPagamentoEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Integração com a API de pagamentos do Mercado Pago (Checkout Transparente / API de Pagamentos).
 *
 * @see <a href="https://www.mercadopago.com.br/developers/pt/reference/payments/_payments/post">Criar pagamento</a>
 */
@Slf4j
@Component
public class MercadoPagoGateway implements GatewayPagamento {

    public static final String NOME = "MERCADO_PAGO";
    /** Rota do webhook nesta API (ver PagamentoController). */
    public static final String ROTA_WEBHOOK = "/api/pagamento/webhook/mercadopago";

    private static final String ENDPOINT_PAGAMENTOS = "/v1/payments";
    /** Formato aceito pelo Mercado Pago em date_of_expiration (ex.: 2026-11-22T20:30:00.000-03:00). */
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final int TIMEOUT_CONEXAO_MS = 5_000;
    private static final int TIMEOUT_LEITURA_MS = 20_000;

    private final RestClient restClient;
    private final boolean configurado;
    /** URL completa do webhook; nula sem API_URL (ex.: ambiente local), quando só a reconciliação confirma o PIX. */
    private final String urlNotificacao;

    @Autowired
    public MercadoPagoGateway(RestClient.Builder builder,
                              @Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                              @Value("${app.mercadopago.access-token:}") String accessToken,
                              @Value("${app.api.url:}") String apiUrl) {
        this(criarRestClient(builder, baseUrl, accessToken), !accessToken.isBlank(), montarUrlNotificacao(apiUrl));
    }

    MercadoPagoGateway(RestClient restClient, boolean configurado, String urlNotificacao) {
        this.restClient = restClient;
        this.configurado = configurado;
        this.urlNotificacao = urlNotificacao;
    }

    /**
     * {@code source_news=webhooks} faz o Mercado Pago enviar somente notificações no formato Webhooks (assinadas),
     * sem as do formato IPN legado.
     */
    static String montarUrlNotificacao(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return null;
        }
        String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        return base + ROTA_WEBHOOK + "?source_news=webhooks";
    }

    private static RestClient criarRestClient(RestClient.Builder builder, String baseUrl, String accessToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_CONEXAO_MS);
        requestFactory.setReadTimeout(TIMEOUT_LEITURA_MS);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public boolean isConfigurado() {
        return configurado;
    }

    @Override
    public CobrancaPix criarCobrancaPix(SolicitacaoCobrancaPix solicitacao) {
        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("email", solicitacao.emailPagador());
        if (solicitacao.nomePagador() != null && !solicitacao.nomePagador().isBlank()) {
            pagador.put("first_name", solicitacao.nomePagador());
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("transaction_amount", solicitacao.valor());
        corpo.put("description", solicitacao.descricao());
        corpo.put("payment_method_id", "pix");
        corpo.put("external_reference", solicitacao.referencia());
        corpo.put("date_of_expiration", FORMATO_DATA.format(solicitacao.expiraEm()));
        corpo.put("payer", pagador);
        if (urlNotificacao != null) {
            corpo.put("notification_url", urlNotificacao);
        }

        JsonNode resposta = chamar("criar o PIX", () -> restClient.post()
                .uri(ENDPOINT_PAGAMENTOS)
                .header("X-Idempotency-Key", solicitacao.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class));

        JsonNode dadosPix = resposta.path("point_of_interaction").path("transaction_data");
        String copiaECola = texto(dadosPix, "qr_code");
        if (copiaECola == null) {
            throw new GatewayPagamentoException("O Mercado Pago não devolveu o QR Code do PIX", 0, null);
        }
        String statusGateway = texto(resposta, "status");
        return new CobrancaPix(
                resposta.path("id").asText(),
                converterStatus(statusGateway, texto(resposta, "status_detail")),
                statusGateway,
                copiaECola,
                texto(dadosPix, "qr_code_base64"));
    }

    @Override
    public SituacaoPagamento consultar(String gatewayId) {
        JsonNode resposta = chamar("consultar o pagamento", () -> restClient.get()
                .uri(ENDPOINT_PAGAMENTOS + "/{id}", gatewayId)
                .retrieve()
                .body(JsonNode.class));
        String statusGateway = texto(resposta, "status");
        JsonNode valor = resposta.path("transaction_amount");
        return new SituacaoPagamento(
                resposta.path("id").asText(gatewayId),
                converterStatus(statusGateway, texto(resposta, "status_detail")),
                statusGateway,
                texto(resposta, "external_reference"),
                valor.isNumber() ? valor.decimalValue() : null,
                data(texto(resposta, "date_approved")));
    }

    @Override
    public void cancelar(String gatewayId) {
        chamar("cancelar o pagamento", () -> restClient.put()
                .uri(ENDPOINT_PAGAMENTOS + "/{id}", gatewayId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "cancelled"))
                .retrieve()
                .body(JsonNode.class));
    }

    /**
     * Converte o status do Mercado Pago. O PIX não pago no prazo fica "cancelled" com detalhe "expired".
     *
     * @see <a href="https://www.mercadopago.com.br/developers/pt/reference/payments/_payments_id/get">Status do pagamento</a>
     */
    static StatusPagamentoEnum converterStatus(String status, String detalhe) {
        if (status == null) {
            return StatusPagamentoEnum.PENDENTE;
        }
        return switch (status) {
            case "approved", "in_mediation" -> StatusPagamentoEnum.PAGO;
            case "cancelled" -> "expired".equals(detalhe) ? StatusPagamentoEnum.EXPIRADO : StatusPagamentoEnum.CANCELADO;
            case "rejected" -> StatusPagamentoEnum.CANCELADO;
            case "refunded", "charged_back" -> StatusPagamentoEnum.ESTORNADO;
            case "pending", "in_process", "authorized" -> StatusPagamentoEnum.PENDENTE;
            default -> {
                log.warn("Status desconhecido do Mercado Pago: {} ({})", status, detalhe);
                yield StatusPagamentoEnum.PENDENTE;
            }
        };
    }

    private JsonNode chamar(String operacao, Supplier<JsonNode> chamada) {
        if (!configurado) {
            throw new GatewayPagamentoException("Mercado Pago não configurado (MERCADOPAGO_ACCESS_TOKEN)", 0, null);
        }
        try {
            JsonNode resposta = chamada.get();
            if (resposta == null) {
                throw new GatewayPagamentoException("Resposta vazia do Mercado Pago ao " + operacao, 0, null);
            }
            return resposta;
        } catch (RestClientResponseException exception) {
            log.error("Mercado Pago recusou {} (status {}): {}", operacao, exception.getStatusCode().value(),
                    exception.getResponseBodyAsString());
            throw new GatewayPagamentoException("Mercado Pago recusou " + operacao, exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.error("Falha de comunicação com o Mercado Pago ao {}", operacao, exception);
            throw new GatewayPagamentoException("Falha de comunicação com o Mercado Pago ao " + operacao, 0, exception);
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
    }

    private static OffsetDateTime data(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor);
        } catch (DateTimeParseException exception) {
            log.warn("Data inválida devolvida pelo Mercado Pago: {}", valor);
            return null;
        }
    }
}
