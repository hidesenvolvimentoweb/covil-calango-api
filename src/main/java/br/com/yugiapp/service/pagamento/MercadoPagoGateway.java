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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Integração com a API de pagamentos do Mercado Pago (Checkout Transparente / API de Pagamentos).
 * <p>
 * Cada produto usa as credenciais da sua aplicação no Mercado Pago:
 * <ul>
 *   <li>PIX: aplicação do QR Code (MERCADOPAGO_QRCODE_ACCESS_TOKEN);</li>
 *   <li>cartão digitado: Checkout Transparente (MERCADOPAGO_CARTAO_ACCESS_TOKEN, par da MERCADOPAGO_CARTAO_PUBLIC_KEY que o
 *   app usa para gerar o token do cartão);</li>
 *   <li>preferência do Checkout Pro: Checkout Pro (MERCADOPAGO_PRO_ACCESS_TOKEN).</li>
 * </ul>
 * Sem o token do cartão ou do Checkout Pro, eles usam o MERCADOPAGO_QRCODE_ACCESS_TOKEN.
 * <p>
 * Consultas, cancelamentos e estornos só recebem o id do pagamento: tentam o token de cada aplicação, na ordem PIX,
 * cartão e Checkout Pro, até achar o pagamento (as aplicações podem ser de contas diferentes, como as de teste).
 *
 * @see <a href="https://www.mercadopago.com.br/developers/pt/reference/payments/_payments/post">Criar pagamento</a>
 */
@Slf4j
@Component
public class MercadoPagoGateway implements GatewayPagamento {

    public static final String NOME = "MERCADO_PAGO";
    /** Rota do webhook nesta API (ver PagamentoController). */
    public static final String ROTA_WEBHOOK = "/api/pagamento/webhook/mercadopago";
    /** Página para onde o Mercado Pago devolve o cliente ao fim do Checkout Pro; ela reabre o app (PagamentoController). */
    public static final String ROTA_RETORNO = "/api/pagamento/retorno/mercadopago";
    /**
     * Categoria do item no Mercado Pago (usada na análise antifraude): a comanda reúne consumo e entretenimento da
     * loja (jogos, torneios e lanches).
     */
    static final String CATEGORIA_ITEM = "entertainment";
    /** Limite do Mercado Pago para o nome na fatura do cartão. */
    private static final int TAMANHO_MAXIMO_DESCRITOR = 22;

    private static final String ENDPOINT_PAGAMENTOS = "/v1/payments";
    private static final String ENDPOINT_PREFERENCIAS = "/checkout/preferences";
    private static final String ENDPOINT_CLIENTES = "/v1/customers";
    /**
     * Na conta do Mercado Pago: cartão de crédito, débito ou o saldo da conta (sem boleto, PIX ou pré-pago). O saldo
     * (account_money) não pode ser excluído quando a preferência é só para quem tem conta (wallet_purchase): o
     * Mercado Pago recusa a preferência com "account_money cannot be excluded".
     */
    private static final List<String> TIPOS_FORA_DA_CARTEIRA =
            List.of("ticket", "bank_transfer", "atm", "prepaid_card");
    /** payment_method_id/payment_type_id do pagamento feito com o saldo da conta do Mercado Pago. */
    public static final String SALDO_EM_CONTA = "account_money";
    /** Formato aceito pelo Mercado Pago em date_of_expiration (ex.: 2026-11-22T20:30:00.000-03:00). */
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final int TIMEOUT_CONEXAO_MS = 5_000;
    private static final int TIMEOUT_LEITURA_MS = 20_000;

    /** Cliente com o token da aplicação do QR Code (PIX). */
    private final RestClient restClient;
    /** Cliente com o token da aplicação do Checkout Transparente (cartão digitado). */
    private final RestClient restClientCartao;
    /** Cliente com o token da aplicação do Checkout Pro (preferências). */
    private final RestClient restClientCheckoutPro;
    /** Clientes distintos, na ordem em que as consultas procuram o pagamento. */
    private final List<RestClient> clientesDeConsulta;
    private final boolean configurado;
    /** URL completa do webhook; nula sem API_URL (ex.: ambiente local), quando só a reconciliação confirma o PIX. */
    private final String urlNotificacao;
    /** Página de retorno do Checkout Pro; nula sem API_URL (o Mercado Pago não aceita localhost em back_urls). */
    private final String urlRetorno;
    /** Nome da loja na fatura do cartão (statement_descriptor); diminui as contestações. */
    private final String descritorFatura;

    @Autowired
    public MercadoPagoGateway(RestClient.Builder builder,
                              @Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                              @Value("${app.mercadopago.qrcode.access-token:}") String accessToken,
                              @Value("${app.mercadopago.cartao.access-token:}") String accessTokenCartao,
                              @Value("${app.mercadopago.checkout-pro.access-token:}") String accessTokenCheckoutPro,
                              @Value("${app.api.url:}") String apiUrl,
                              @Value("${app.mercadopago.statement-descriptor:COVILDOCALANGO}") String descritorFatura) {
        this(clientesPorToken(builder, baseUrl, accessToken, accessTokenCartao, accessTokenCheckoutPro),
                !normalizarToken(accessToken).isEmpty(), apiUrl, descritorFatura);
    }

    private MercadoPagoGateway(List<RestClient> clientes, boolean configurado, String apiUrl, String descritorFatura) {
        this(clientes.get(0), clientes.get(1), clientes.get(2), configurado, apiUrl, descritorFatura);
    }

    /**
     * Um cliente para cada token, na ordem PIX, cartão e Checkout Pro. O token vazio usa o do PIX, e tokens iguais
     * compartilham o mesmo cliente (a consulta não repete a chamada na mesma conta).
     */
    private static List<RestClient> clientesPorToken(RestClient.Builder builder, String baseUrl, String tokenPix,
                                                     String tokenCartao, String tokenCheckoutPro) {
        String pix = normalizarToken(tokenPix);
        Map<String, RestClient> porToken = new LinkedHashMap<>();
        return Stream.of(pix, normalizarToken(tokenCartao), normalizarToken(tokenCheckoutPro))
                .map(token -> token.isEmpty() ? pix : token)
                .map(token -> porToken.computeIfAbsent(token, chave -> criarRestClient(builder, baseUrl, chave)))
                .toList();
    }

    /**
     * Remove espaços, quebras de linha (ex.: o "\r" de um .env salvo no Windows) e aspas em volta do token, que
     * fariam o Mercado Pago recusar a autenticação (401).
     */
    public static String normalizarToken(String token) {
        if (token == null) {
            return "";
        }
        String normalizado = token.strip();
        if (normalizado.length() >= 2 && (normalizado.startsWith("\"") && normalizado.endsWith("\"")
                || normalizado.startsWith("'") && normalizado.endsWith("'"))) {
            normalizado = normalizado.substring(1, normalizado.length() - 1).strip();
        }
        return normalizado;
    }

    MercadoPagoGateway(RestClient restClient, boolean configurado, String apiUrl) {
        this(restClient, restClient, restClient, configurado, apiUrl, "COVILDOCALANGO");
    }

    MercadoPagoGateway(RestClient restClient, RestClient restClientCartao, RestClient restClientCheckoutPro,
                       boolean configurado, String apiUrl, String descritorFatura) {
        this.restClient = restClient;
        this.restClientCartao = restClientCartao;
        this.restClientCheckoutPro = restClientCheckoutPro;
        this.clientesDeConsulta = Stream.of(restClient, restClientCartao, restClientCheckoutPro).distinct().toList();
        this.configurado = configurado;
        this.urlNotificacao = montarUrlNotificacao(apiUrl);
        String base = semBarraFinal(apiUrl);
        this.urlRetorno = base == null ? null : base + ROTA_RETORNO;
        String descritor = descritorFatura == null ? "" : descritorFatura.strip();
        this.descritorFatura = descritor.isEmpty() ? null
                : descritor.substring(0, Math.min(descritor.length(), TAMANHO_MAXIMO_DESCRITOR));
    }

    /**
     * {@code source_news=webhooks} faz o Mercado Pago enviar somente notificações no formato Webhooks (assinadas),
     * sem as do formato IPN legado.
     */
    static String montarUrlNotificacao(String apiUrl) {
        String base = semBarraFinal(apiUrl);
        return base == null ? null : base + ROTA_WEBHOOK + "?source_news=webhooks";
    }

    private static String semBarraFinal(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return null;
        }
        String base = apiUrl.strip();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
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

    /**
     * Cobrança à vista com o token do cartão. A verificação do banco (3DS) é pedida quando o emissor exige: o
     * pagamento fica "pending_challenge" e a resposta traz o endereço do desafio, exibido no app.
     */
    @Override
    public SituacaoPagamento pagarComCartao(SolicitacaoPagamentoCartao solicitacao) {
        Map<String, Object> pagador = new LinkedHashMap<>();
        if (solicitacao.clienteId() != null) {
            // Aparelho com cartões salvos: o pagador é o cliente salvo (exigido para cobrar um cartão salvo).
            pagador.put("type", "customer");
            pagador.put("id", solicitacao.clienteId());
        } else {
            pagador.put("email", solicitacao.emailPagador());
        }
        if (solicitacao.documento() != null) {
            pagador.put("identification", Map.of("type", solicitacao.tipoDocumento(), "number", solicitacao.documento()));
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("transaction_amount", solicitacao.valor());
        corpo.put("token", solicitacao.token());
        corpo.put("description", solicitacao.descricao());
        corpo.put("installments", 1);
        corpo.put("payment_method_id", solicitacao.bandeira());
        if (solicitacao.emissorId() != null) {
            corpo.put("issuer_id", solicitacao.emissorId());
        }
        corpo.put("payer", pagador);
        corpo.put("external_reference", solicitacao.referencia());
        corpo.put("three_d_secure_mode", "optional");
        if (urlNotificacao != null) {
            corpo.put("notification_url", urlNotificacao);
        }

        return situacao(chamar("cobrar o cartão", () -> restClientCartao.post()
                .uri(ENDPOINT_PAGAMENTOS)
                .header("X-Idempotency-Key", solicitacao.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class)), null);
    }

    /**
     * Preferência do Checkout Pro só para quem tem conta no Mercado Pago (wallet_purchase), à vista, com cartão
     * de crédito, débito ou o saldo da conta. binary_mode: o pagamento é aprovado ou recusado na hora, sem ficar em análise.
     * <p>
     * Ao terminar, o Mercado Pago devolve o cliente à página de retorno (back_urls), que reabre o app; com o pagamento
     * aprovado o retorno é automático (auto_return). A confirmação continua vindo só do webhook e da consulta ao
     * Mercado Pago: os parâmetros do retorno não são usados.
     */
    @Override
    public PreferenciaCheckout criarPreferencia(SolicitacaoPreferencia solicitacao) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", solicitacao.referencia());
        item.put("title", solicitacao.descricao());
        item.put("description", solicitacao.descricao());
        item.put("category_id", CATEGORIA_ITEM);
        item.put("quantity", 1);
        item.put("currency_id", "BRL");
        item.put("unit_price", solicitacao.valor());

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("items", List.of(item));
        Map<String, Object> pagador = pagadorDaPreferencia(solicitacao);
        if (!pagador.isEmpty()) {
            corpo.put("payer", pagador);
        }
        corpo.put("external_reference", solicitacao.referencia());
        if (descritorFatura != null) {
            corpo.put("statement_descriptor", descritorFatura);
        }
        corpo.put("purpose", "wallet_purchase");
        corpo.put("binary_mode", true);
        corpo.put("payment_methods", Map.of(
                "installments", 1,
                "excluded_payment_types", TIPOS_FORA_DA_CARTEIRA.stream().map(tipo -> Map.of("id", tipo)).toList()));
        corpo.put("expires", true);
        corpo.put("expiration_date_to", FORMATO_DATA.format(solicitacao.expiraEm()));
        if (urlNotificacao != null) {
            corpo.put("notification_url", urlNotificacao);
        }
        if (urlRetorno != null) {
            corpo.put("back_urls", Map.of(
                    "success", urlRetorno + "?resultado=aprovado",
                    "pending", urlRetorno + "?resultado=pendente",
                    "failure", urlRetorno + "?resultado=recusado"));
            corpo.put("auto_return", "approved");
        }

        JsonNode resposta = chamar("criar o pagamento pelo Mercado Pago", () -> restClientCheckoutPro.post()
                .uri(ENDPOINT_PREFERENCIAS)
                .header("X-Idempotency-Key", solicitacao.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class));
        String url = texto(resposta, "init_point");
        if (url == null) {
            throw new GatewayPagamentoException("O Mercado Pago não devolveu o link de pagamento", 0, null);
        }
        return new PreferenciaCheckout(resposta.path("id").asText(), url);
    }

    /**
     * Dados do pagador que o cliente informou: nome (dividido em nome e sobrenome), e-mail do cadastro e celular.
     * Só o que existe é enviado.
     */
    static Map<String, Object> pagadorDaPreferencia(SolicitacaoPreferencia solicitacao) {
        Map<String, Object> pagador = new LinkedHashMap<>();
        String nome = solicitacao.nomePagador() == null ? "" : solicitacao.nomePagador().strip().replaceAll("\\s+", " ");
        if (!nome.isEmpty()) {
            int espaco = nome.indexOf(' ');
            pagador.put("name", espaco < 0 ? nome : nome.substring(0, espaco));
            if (espaco > 0) {
                pagador.put("surname", nome.substring(espaco + 1));
            }
        }
        if (solicitacao.emailPagador() != null && !solicitacao.emailPagador().isBlank()) {
            pagador.put("email", solicitacao.emailPagador().strip());
        }
        String celular = solicitacao.celularPagador() == null ? "" : solicitacao.celularPagador().replaceAll("\\D", "");
        if (celular.length() >= 12 && celular.startsWith("55")) {
            celular = celular.substring(2);
        }
        if (celular.length() == 10 || celular.length() == 11) {
            pagador.put("phone", Map.of("area_code", celular.substring(0, 2), "number", celular.substring(2)));
        }
        return pagador;
    }

    @Override
    public void expirarPreferencia(String preferenciaId) {
        chamar("encerrar o link de pagamento", () -> restClientCheckoutPro.put()
                .uri(ENDPOINT_PREFERENCIAS + "/{id}", preferenciaId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expires", true, "expiration_date_to", FORMATO_DATA.format(OffsetDateTime.now())))
                .retrieve()
                .body(JsonNode.class));
    }

    @Override
    public Optional<SituacaoPagamento> buscarPorReferencia(String referencia) {
        for (RestClient cliente : clientesDeConsulta) {
            Optional<SituacaoPagamento> encontrado = buscarPorReferencia(cliente, referencia);
            if (encontrado.isPresent()) {
                return encontrado;
            }
        }
        return Optional.empty();
    }

    private Optional<SituacaoPagamento> buscarPorReferencia(RestClient cliente, String referencia) {
        JsonNode resposta = chamar("buscar o pagamento", () -> cliente.get()
                .uri(uri -> uri.path(ENDPOINT_PAGAMENTOS + "/search")
                        .queryParam("external_reference", referencia)
                        .queryParam("sort", "date_created")
                        .queryParam("criteria", "desc")
                        .build())
                .retrieve()
                .body(JsonNode.class));
        JsonNode escolhido = null;
        for (JsonNode pagamento : resposta.path("results")) {
            if (!referencia.equals(texto(pagamento, "external_reference"))) {
                continue;
            }
            if ("approved".equals(texto(pagamento, "status"))) {
                escolhido = pagamento;
                break;
            }
            if (escolhido == null) {
                escolhido = pagamento;
            }
        }
        return Optional.ofNullable(escolhido).map(pagamento -> situacao(pagamento, null));
    }

    @Override
    public SituacaoPagamento consultar(String gatewayId) {
        return situacao(chamarEmCadaConta("consultar o pagamento", cliente -> cliente.get()
                .uri(ENDPOINT_PAGAMENTOS + "/{id}", gatewayId)
                .retrieve()
                .body(JsonNode.class)), gatewayId);
    }

    @Override
    public void cancelar(String gatewayId) {
        chamarEmCadaConta("cancelar o pagamento", cliente -> cliente.put()
                .uri(ENDPOINT_PAGAMENTOS + "/{id}", gatewayId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "cancelled"))
                .retrieve()
                .body(JsonNode.class));
    }

    /** Estorno total; a chave de idempotência impede devolver o valor duas vezes. */
    @Override
    public void estornar(String gatewayId, String idempotencyKey) {
        chamarEmCadaConta("estornar o pagamento", cliente -> cliente.post()
                .uri(ENDPOINT_PAGAMENTOS + "/{id}/refunds", gatewayId)
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class));
    }

    /**
     * Cliente onde os cartões salvos ficam, na conta da aplicação do cartão. Se o e-mail já tem cliente (ex.: a
     * resposta da criação se perdeu e o app tentou de novo), devolve o existente.
     */
    @Override
    public String criarCliente(String email) {
        try {
            return chamar("criar o cliente", () -> restClientCartao.post()
                    .uri(ENDPOINT_CLIENTES)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "description", "Cliente do app (cartões salvos no celular)"))
                    .retrieve()
                    .body(JsonNode.class)).path("id").asText();
        } catch (GatewayPagamentoException exception) {
            if (exception.getStatusHttp() != 400) {
                throw exception;
            }
            JsonNode existente = chamar("buscar o cliente", () -> restClientCartao.get()
                    .uri(uri -> uri.path(ENDPOINT_CLIENTES + "/search").queryParam("email", email).build())
                    .retrieve()
                    .body(JsonNode.class)).path("results").path(0);
            if (existente.isMissingNode()) {
                throw exception;
            }
            return existente.path("id").asText();
        }
    }

    @Override
    public CartaoSalvo salvarCartao(String clienteId, String token) {
        return cartaoSalvo(chamar("salvar o cartão", () -> restClientCartao.post()
                .uri(ENDPOINT_CLIENTES + "/{id}/cards", clienteId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", token))
                .retrieve()
                .body(JsonNode.class)));
    }

    @Override
    public List<CartaoSalvo> listarCartoes(String clienteId) {
        JsonNode resposta = chamar("listar os cartões salvos", () -> restClientCartao.get()
                .uri(ENDPOINT_CLIENTES + "/{id}/cards", clienteId)
                .retrieve()
                .body(JsonNode.class));
        List<CartaoSalvo> cartoes = new ArrayList<>();
        resposta.forEach(cartao -> cartoes.add(cartaoSalvo(cartao)));
        return cartoes;
    }

    @Override
    public void excluirCartao(String clienteId, String cartaoId) {
        chamar("excluir o cartão salvo", () -> restClientCartao.delete()
                .uri(ENDPOINT_CLIENTES + "/{cliente}/cards/{cartao}", clienteId, cartaoId)
                .retrieve()
                .body(JsonNode.class));
    }

    private static CartaoSalvo cartaoSalvo(JsonNode cartao) {
        JsonNode metodo = cartao.path("payment_method");
        JsonNode mes = cartao.path("expiration_month");
        JsonNode ano = cartao.path("expiration_year");
        return new CartaoSalvo(
                cartao.path("id").asText(),
                texto(metodo, "id"),
                texto(metodo, "name"),
                texto(metodo, "payment_type_id"),
                texto(cartao, "last_four_digits"),
                mes.isNumber() ? mes.asInt() : null,
                ano.isNumber() ? ano.asInt() : null);
    }

    private static SituacaoPagamento situacao(JsonNode resposta, String gatewayIdPadrao) {
        String statusGateway = texto(resposta, "status");
        String detalhe = texto(resposta, "status_detail");
        JsonNode valor = resposta.path("transaction_amount");
        JsonNode tresDs = resposta.path("three_ds_info");
        return new SituacaoPagamento(
                gatewayIdPadrao == null ? resposta.path("id").asText() : resposta.path("id").asText(gatewayIdPadrao),
                converterStatus(statusGateway, detalhe),
                statusGateway,
                texto(resposta, "external_reference"),
                valor.isNumber() ? valor.decimalValue() : null,
                data(texto(resposta, "date_approved")),
                detalhe,
                texto(resposta, "payment_method_id"),
                texto(resposta.path("card"), "last_four_digits"),
                texto(tresDs, "external_resource_url"),
                texto(tresDs, "creq"),
                texto(resposta, "payment_type_id"));
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
            case "rejected" -> StatusPagamentoEnum.RECUSADO;
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
            throw new GatewayPagamentoException("Mercado Pago não configurado (MERCADOPAGO_QRCODE_ACCESS_TOKEN)", 0, null);
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
            if (exception.getStatusCode().value() == 401) {
                log.error("Access Token do Mercado Pago recusado: confira MERCADOPAGO_QRCODE_ACCESS_TOKEN (copie o Access Token, "
                        + "não a Public Key) e recrie o container para recarregar o .env");
            }
            throw new GatewayPagamentoException("Mercado Pago recusou " + operacao, exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.error("Falha de comunicação com o Mercado Pago ao {}", operacao, exception);
            throw new GatewayPagamentoException("Falha de comunicação com o Mercado Pago ao " + operacao, 0, exception);
        }
    }

    /**
     * Operação sobre um pagamento que já existe: tenta o token de cada aplicação até a conta dona do pagamento
     * responder. Só o "não encontrado" (404) passa para o próximo token; qualquer outra recusa é devolvida na hora.
     */
    private JsonNode chamarEmCadaConta(String operacao, Function<RestClient, JsonNode> chamada) {
        GatewayPagamentoException naoEncontrado = null;
        for (RestClient cliente : clientesDeConsulta) {
            try {
                return chamar(operacao, () -> chamada.apply(cliente));
            } catch (GatewayPagamentoException exception) {
                if (exception.getStatusHttp() != 404) {
                    throw exception;
                }
                naoEncontrado = exception;
            }
        }
        throw naoEncontrado;
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
