package br.com.yugiapp.service.pagamento;

import br.com.yugiapp.enums.StatusPagamentoEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoPagoGatewayTest {

    private static final String BASE_URL = "https://api.mercadopago.com";

    private MockRestServiceServer servidor;
    private MercadoPagoGateway gateway;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        gateway = new MercadoPagoGateway(builder.build(), true,
                "https://api.covil.local/");
    }

    @Test
    void criaPixComValorExpiracaoEChaveDeIdempotencia() {
        servidor.expect(requestTo(BASE_URL + "/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "chave-1"))
                .andExpect(jsonPath("$.transaction_amount").value(25.5))
                .andExpect(jsonPath("$.payment_method_id").value("pix"))
                .andExpect(jsonPath("$.external_reference").value("PAG-7"))
                .andExpect(jsonPath("$.date_of_expiration").value("2026-11-22T20:30:00.000-03:00"))
                .andExpect(jsonPath("$.notification_url")
                        .value("https://api.covil.local/api/pagamento/webhook/mercadopago?source_news=webhooks"))
                .andExpect(jsonPath("$.payer.email").value("cliente@covil.local"))
                .andRespond(withSuccess("""
                        {"id": 123456789, "status": "pending", "status_detail": "pending_waiting_transfer",
                         "point_of_interaction": {"transaction_data": {"qr_code": "00020126...", "qr_code_base64": "iVBOR"}}}
                        """, MediaType.APPLICATION_JSON));

        CobrancaPix cobranca = gateway.criarCobrancaPix(new SolicitacaoCobrancaPix("PAG-7", new BigDecimal("25.50"),
                "Comanda 1", "Cliente", "cliente@covil.local", OffsetDateTime.parse("2026-11-22T20:30:00-03:00"), "chave-1"));

        assertThat(cobranca.gatewayId()).isEqualTo("123456789");
        assertThat(cobranca.status()).isEqualTo(StatusPagamentoEnum.PENDENTE);
        assertThat(cobranca.copiaECola()).isEqualTo("00020126...");
        assertThat(cobranca.qrCodeBase64()).isEqualTo("iVBOR");
        servidor.verify();
    }

    @Test
    void consultaPagamentoAprovado() {
        servidor.expect(requestTo(BASE_URL + "/v1/payments/123456789"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": 123456789, "status": "approved", "status_detail": "accredited",
                         "external_reference": "PAG-7", "transaction_amount": 25.5,
                         "date_approved": "2026-11-22T20:10:00.000-04:00"}
                        """, MediaType.APPLICATION_JSON));

        SituacaoPagamento situacao = gateway.consultar("123456789");

        assertThat(situacao.status()).isEqualTo(StatusPagamentoEnum.PAGO);
        assertThat(situacao.referencia()).isEqualTo("PAG-7");
        assertThat(situacao.valor()).isEqualByComparingTo("25.50");
        assertThat(situacao.aprovadoEm()).isEqualTo(OffsetDateTime.parse("2026-11-22T20:10:00-04:00"));
    }

    @Test
    void recusaDoMercadoPagoViraGatewayPagamentoException() {
        servidor.expect(requestTo(BASE_URL + "/v1/payments/1"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"invalid\"}"));

        assertThatThrownBy(() -> gateway.cancelar("1"))
                .isInstanceOfSatisfying(GatewayPagamentoException.class, erro -> assertThat(erro.isRecusa()).isTrue());
    }

    @Test
    void cobraOCartaoAVistaComTokenVerificacaoDoBancoEIdempotencia() {
        servidor.expect(requestTo(BASE_URL + "/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "chave-2"))
                .andExpect(jsonPath("$.transaction_amount").value(25.5))
                .andExpect(jsonPath("$.token").value("tok_123"))
                .andExpect(jsonPath("$.installments").value(1))
                .andExpect(jsonPath("$.payment_method_id").value("visa"))
                .andExpect(jsonPath("$.issuer_id").value("25"))
                .andExpect(jsonPath("$.three_d_secure_mode").value("optional"))
                .andExpect(jsonPath("$.external_reference").value("PAG-7"))
                .andExpect(jsonPath("$.payer.email").value("titular@email.com"))
                .andExpect(jsonPath("$.payer.identification.type").value("CPF"))
                .andExpect(jsonPath("$.payer.identification.number").value("12345678909"))
                .andRespond(withSuccess("""
                        {"id": 555, "status": "pending", "status_detail": "pending_challenge", "transaction_amount": 25.5,
                         "external_reference": "PAG-7", "payment_method_id": "visa", "payment_type_id": "credit_card",
                         "card": {"last_four_digits": "4242"},
                         "three_ds_info": {"external_resource_url": "https://banco/3ds", "creq": "creq-1"}}
                        """, MediaType.APPLICATION_JSON));

        SituacaoPagamento situacao = gateway.pagarComCartao(new SolicitacaoPagamentoCartao("PAG-7",
                new BigDecimal("25.50"), "Comanda 1", "tok_123", "visa", "25", "titular@email.com", "CPF",
                "12345678909", "chave-2"));

        servidor.verify();
        assertThat(situacao.gatewayId()).isEqualTo("555");
        assertThat(situacao.status()).isEqualTo(StatusPagamentoEnum.PENDENTE);
        assertThat(situacao.statusDetalhe()).isEqualTo("pending_challenge");
        assertThat(situacao.cartaoFinal()).isEqualTo("4242");
        assertThat(situacao.tipoCartao()).isEqualTo("credit_card");
        assertThat(situacao.desafioUrl()).isEqualTo("https://banco/3ds");
        assertThat(situacao.desafioCreq()).isEqualTo("creq-1");
    }

    /** Gateway com um token por aplicação: PIX (QR Code), cartão (Transparente) e Checkout Pro. */
    private MercadoPagoGateway gatewayComTokensSeparados() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        return new MercadoPagoGateway(
                builder.clone().defaultHeader("Authorization", "Bearer token-pix").build(),
                builder.clone().defaultHeader("Authorization", "Bearer token-cartao").build(),
                builder.clone().defaultHeader("Authorization", "Bearer token-pro").build(),
                true, "https://api.covil.local/", "COVILDOCALANGO");
    }

    @Test
    void cobraOCartaoComOTokenDaAplicacaoDoCartaoEOPixComODoQrCode() {
        MercadoPagoGateway separado = gatewayComTokensSeparados();
        servidor.expect(requestTo(BASE_URL + "/v1/payments"))
                .andExpect(header("Authorization", "Bearer token-cartao"))
                .andRespond(withSuccess("{\"id\": 555, \"status\": \"approved\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE_URL + "/v1/payments"))
                .andExpect(header("Authorization", "Bearer token-pix"))
                .andRespond(withSuccess("""
                        {"id": 556, "status": "pending",
                         "point_of_interaction": {"transaction_data": {"qr_code": "00020126..."}}}
                        """, MediaType.APPLICATION_JSON));

        separado.pagarComCartao(new SolicitacaoPagamentoCartao("PAG-7", new BigDecimal("25.50"), "Comanda 1",
                "tok_123", "visa", null, "titular@email.com", null, null, "chave-2"));
        separado.criarCobrancaPix(new SolicitacaoCobrancaPix("PAG-8", new BigDecimal("10"), "Comanda 2", null,
                "cliente@covil.local", OffsetDateTime.parse("2026-11-22T20:30:00-03:00"), "chave-3"));

        servidor.verify();
    }

    @Test
    void cartaoSalvoCobraComoClienteDaAplicacaoDoCartao() {
        MercadoPagoGateway separado = gatewayComTokensSeparados();
        servidor.expect(requestTo(BASE_URL + "/v1/payments"))
                .andExpect(header("Authorization", "Bearer token-cartao"))
                .andExpect(jsonPath("$.payer.type").value("customer"))
                .andExpect(jsonPath("$.payer.id").value("cliente-1"))
                .andExpect(jsonPath("$.payer.email").doesNotExist())
                .andExpect(jsonPath("$.payer.identification").doesNotExist())
                .andRespond(withSuccess("{\"id\": 557, \"status\": \"approved\"}", MediaType.APPLICATION_JSON));

        separado.pagarComCartao(new SolicitacaoPagamentoCartao("PAG-9", new BigDecimal("10"), "Comanda 3", "tok_salvo",
                "visa", null, "cliente@covil.local", null, null, "chave-4", "cliente-1"));

        servidor.verify();
    }

    @Test
    void criaOClienteESalvaOCartaoNaAplicacaoDoCartao() {
        MercadoPagoGateway separado = gatewayComTokensSeparados();
        servidor.expect(requestTo(BASE_URL + "/v1/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-cartao"))
                .andExpect(jsonPath("$.email").value("cliente-42@covildocalango.tech"))
                .andRespond(withSuccess("{\"id\": \"cliente-1\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE_URL + "/v1/customers/cliente-1/cards"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-cartao"))
                .andExpect(jsonPath("$.token").value("tok_1"))
                .andRespond(withSuccess("""
                        {"id": "9897919519", "last_four_digits": "3311", "expiration_month": 11, "expiration_year": 2030,
                         "payment_method": {"id": "master", "name": "Mastercard", "payment_type_id": "credit_card"}}
                        """, MediaType.APPLICATION_JSON));

        String cliente = separado.criarCliente("cliente-42@covildocalango.tech");
        CartaoSalvo cartao = separado.salvarCartao(cliente, "tok_1");

        servidor.verify();
        assertThat(cartao).isEqualTo(new CartaoSalvo("9897919519", "master", "Mastercard", "credit_card", "3311", 11, 2030));
    }

    @Test
    void clienteJaExistenteComOMesmoEmailEhReaproveitado() {
        MercadoPagoGateway separado = gatewayComTokensSeparados();
        servidor.expect(requestTo(BASE_URL + "/v1/customers"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        servidor.expect(requestTo(BASE_URL + "/v1/customers/search?email=cliente-42@covildocalango.tech"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"results\": [{\"id\": \"cliente-1\"}]}", MediaType.APPLICATION_JSON));

        assertThat(separado.criarCliente("cliente-42@covildocalango.tech")).isEqualTo("cliente-1");
        servidor.verify();
    }

    @Test
    void listaERemoveOsCartoesSalvos() {
        MercadoPagoGateway separado = gatewayComTokensSeparados();
        servidor.expect(requestTo(BASE_URL + "/v1/customers/cliente-1/cards"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id": "card-1", "last_four_digits": "5682",
                          "payment_method": {"id": "visa", "name": "Visa", "payment_type_id": "credit_card"}}]
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE_URL + "/v1/customers/cliente-1/cards/card-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer token-cartao"))
                .andRespond(withSuccess("{\"id\": \"card-1\"}", MediaType.APPLICATION_JSON));

        assertThat(separado.listarCartoes("cliente-1")).extracting(CartaoSalvo::finalCartao).containsExactly("5682");
        separado.excluirCartao("cliente-1", "card-1");

        servidor.verify();
    }

    @Test
    void consultaProcuraOPagamentoNaContaDeCadaAplicacao() {
        MercadoPagoGateway separado = gatewayComTokensSeparados();
        servidor.expect(requestTo(BASE_URL + "/v1/payments/555"))
                .andExpect(header("Authorization", "Bearer token-pix"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo(BASE_URL + "/v1/payments/555"))
                .andExpect(header("Authorization", "Bearer token-cartao"))
                .andRespond(withSuccess("{\"id\": 555, \"status\": \"approved\"}", MediaType.APPLICATION_JSON));

        assertThat(separado.consultar("555").status()).isEqualTo(StatusPagamentoEnum.PAGO);
        servidor.verify();
    }

    @Test
    void consultaNaoTentaOutraContaQuandoOMercadoPagoRecusaPorOutroMotivo() {
        MercadoPagoGateway separado = gatewayComTokensSeparados();
        servidor.expect(requestTo(BASE_URL + "/v1/payments/555"))
                .andExpect(header("Authorization", "Bearer token-pix"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> separado.consultar("555")).isInstanceOf(GatewayPagamentoException.class);
        servidor.verify();
    }

    @Test
    void criaOLinkDoMercadoPagoComCartaoOuSaldoDaContaAVista() {
        servidor.expect(requestTo(BASE_URL + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "chave-3"))
                .andExpect(jsonPath("$.external_reference").value("PAG-8"))
                .andExpect(jsonPath("$.items[0].unit_price").value(25.5))
                .andExpect(jsonPath("$.purpose").value("wallet_purchase"))
                .andExpect(jsonPath("$.binary_mode").value(true))
                .andExpect(jsonPath("$.payment_methods.installments").value(1))
                .andExpect(jsonPath("$.payment_methods.excluded_payment_types[?(@.id == 'debit_card')]").doesNotExist())
                .andExpect(jsonPath("$.payment_methods.excluded_payment_types[?(@.id == 'prepaid_card')]").exists())
                .andExpect(jsonPath("$.payment_methods.excluded_payment_types[?(@.id == 'account_money')]").doesNotExist())
                .andExpect(jsonPath("$.expiration_date_to").value("2026-11-22T20:30:00.000-03:00"))
                .andExpect(jsonPath("$.items[0].description").value("Comanda 1"))
                .andExpect(jsonPath("$.items[0].category_id").value("entertainment"))
                .andExpect(jsonPath("$.statement_descriptor").value("COVILDOCALANGO"))
                .andExpect(jsonPath("$.payer.name").value("Maria"))
                .andExpect(jsonPath("$.payer.surname").value("da Silva"))
                .andExpect(jsonPath("$.payer.email").value("maria@exemplo.com"))
                .andExpect(jsonPath("$.payer.phone.area_code").value("61"))
                .andExpect(jsonPath("$.payer.phone.number").value("999998888"))
                .andExpect(jsonPath("$.back_urls.success")
                        .value("https://api.covil.local/api/pagamento/retorno/mercadopago?resultado=aprovado"))
                .andExpect(jsonPath("$.back_urls.failure")
                        .value("https://api.covil.local/api/pagamento/retorno/mercadopago?resultado=recusado"))
                .andExpect(jsonPath("$.auto_return").value("approved"))
                .andRespond(withSuccess("""
                        {"id": "pref-1", "init_point": "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1"}
                        """, MediaType.APPLICATION_JSON));

        PreferenciaCheckout preferencia = gateway.criarPreferencia(new SolicitacaoPreferencia("PAG-8",
                new BigDecimal("25.50"), "Comanda 1", OffsetDateTime.parse("2026-11-22T20:30:00-03:00"), "chave-3",
                "  Maria   da Silva ", "maria@exemplo.com", "5561999998888"));

        servidor.verify();
        assertThat(preferencia.id()).isEqualTo("pref-1");
        assertThat(preferencia.url()).contains("pref_id=pref-1");
    }

    @Test
    void semDadosDoClienteAPreferenciaNaoLevaPagadorESemApiUrlNaoLevaRetorno() {
        MercadoPagoGateway semApiUrl = new MercadoPagoGateway(RestClient.create(), true, null);
        assertThat(MercadoPagoGateway.pagadorDaPreferencia(new SolicitacaoPreferencia("PAG-1", BigDecimal.ONE, "Comanda 1",
                OffsetDateTime.now(), "chave", " ", null, "123"))).isEmpty();
        assertThat(MercadoPagoGateway.pagadorDaPreferencia(new SolicitacaoPreferencia("PAG-1", BigDecimal.ONE, "Comanda 1",
                OffsetDateTime.now(), "chave", "Maria", null, "6133334444")))
                .containsEntry("name", "Maria").doesNotContainKey("surname")
                .containsEntry("phone", java.util.Map.of("area_code", "61", "number", "33334444"));
        assertThat(semApiUrl).isNotNull();
    }

    @Test
    void buscaPelaReferenciaPrefereOPagamentoAprovado() {
        servidor.expect(requestTo(BASE_URL + "/v1/payments/search?external_reference=PAG-8&sort=date_created&criteria=desc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results": [
                          {"id": 3, "status": "rejected", "external_reference": "PAG-8", "transaction_amount": 25.5},
                          {"id": 2, "status": "approved", "external_reference": "PAG-8", "transaction_amount": 25.5},
                          {"id": 1, "status": "approved", "external_reference": "PAG-80", "transaction_amount": 25.5}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        SituacaoPagamento situacao = gateway.buscarPorReferencia("PAG-8").orElseThrow();

        assertThat(situacao.gatewayId()).isEqualTo("2");
        assertThat(situacao.status()).isEqualTo(StatusPagamentoEnum.PAGO);
    }

    @Test
    void estornaOValorTotalComChaveDeIdempotencia() {
        servidor.expect(requestTo(BASE_URL + "/v1/payments/555/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "estorno-chave"))
                .andRespond(withSuccess("{\"id\": 1, \"status\": \"approved\"}", MediaType.APPLICATION_JSON));

        gateway.estornar("555", "estorno-chave");

        servidor.verify();
    }

    @Test
    void semAccessTokenNaoChamaOMercadoPago() {
        MercadoPagoGateway semToken = new MercadoPagoGateway(RestClient.create(), false, null);

        assertThat(semToken.isConfigurado()).isFalse();
        assertThatThrownBy(() -> semToken.consultar("1")).isInstanceOf(GatewayPagamentoException.class);
    }

    @Test
    void normalizaTokenComEspacosQuebraDeLinhaOuAspas() {
        assertThat(MercadoPagoGateway.normalizarToken("APP_USR-123\r")).isEqualTo("APP_USR-123");
        assertThat(MercadoPagoGateway.normalizarToken("  \"APP_USR-123\" ")).isEqualTo("APP_USR-123");
        assertThat(MercadoPagoGateway.normalizarToken("'APP_USR-123'")).isEqualTo("APP_USR-123");
        assertThat(MercadoPagoGateway.normalizarToken(" \r\n")).isEmpty();
        assertThat(MercadoPagoGateway.normalizarToken(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "approved,accredited,PAGO",
            "pending,pending_waiting_transfer,PENDENTE",
            "cancelled,expired,EXPIRADO",
            "cancelled,by_collector,CANCELADO",
            "rejected,cc_rejected_other_reason,RECUSADO",
            "refunded,refunded,ESTORNADO"
    })
    void converteStatusDoMercadoPago(String status, String detalhe, StatusPagamentoEnum esperado) {
        assertThat(MercadoPagoGateway.converterStatus(status, detalhe)).isEqualTo(esperado);
    }
}
