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
                MercadoPagoGateway.montarUrlNotificacao("https://api.covil.local/"));
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
    void semAccessTokenNaoChamaOMercadoPago() {
        MercadoPagoGateway semToken = new MercadoPagoGateway(RestClient.create(), false, null);

        assertThat(semToken.isConfigurado()).isFalse();
        assertThatThrownBy(() -> semToken.consultar("1")).isInstanceOf(GatewayPagamentoException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "approved,accredited,PAGO",
            "pending,pending_waiting_transfer,PENDENTE",
            "cancelled,expired,EXPIRADO",
            "cancelled,by_collector,CANCELADO",
            "rejected,cc_rejected_other_reason,CANCELADO",
            "refunded,refunded,ESTORNADO"
    })
    void converteStatusDoMercadoPago(String status, String detalhe, StatusPagamentoEnum esperado) {
        assertThat(MercadoPagoGateway.converterStatus(status, detalhe)).isEqualTo(esperado);
    }
}
