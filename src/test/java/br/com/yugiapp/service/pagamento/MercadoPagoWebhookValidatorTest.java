package br.com.yugiapp.service.pagamento;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoWebhookValidatorTest {

    private static final String SEGREDO = "segredo-do-webhook";

    private static String assinar(String manifesto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aceitaAssinaturaDoManifestoDoMercadoPago() throws Exception {
        String v1 = assinar("id:123456;request-id:req-1;ts:1700000000;");
        MercadoPagoWebhookValidator validator = new MercadoPagoWebhookValidator(SEGREDO);

        assertThat(validator.validar("ts=1700000000,v1=" + v1, "req-1", "123456")).isTrue();
    }

    @Test
    void idAlfanumericoEhAssinadoEmMinusculas() throws Exception {
        String v1 = assinar("id:abc123;request-id:req-1;ts:1700000000;");
        MercadoPagoWebhookValidator validator = new MercadoPagoWebhookValidator(SEGREDO);

        assertThat(validator.validar("ts=1700000000, v1=" + v1, "req-1", "ABC123")).isTrue();
    }

    @Test
    void recusaAssinaturaAusenteOuDeOutroConteudo() throws Exception {
        String v1 = assinar("id:123456;request-id:req-1;ts:1700000000;");
        MercadoPagoWebhookValidator validator = new MercadoPagoWebhookValidator(SEGREDO);

        assertThat(validator.validar("ts=1700000000,v1=" + v1, "req-1", "999999")).isFalse();
        assertThat(validator.validar("ts=1700000001,v1=" + v1, "req-1", "123456")).isFalse();
        assertThat(validator.validar(null, "req-1", "123456")).isFalse();
        assertThat(validator.validar("ts=1700000000,v1=nao-hex", "req-1", "123456")).isFalse();
    }

    @Test
    void aceitaAssinaturaDaAplicacaoDoCheckoutPro() throws Exception {
        String v1 = assinar("id:123456;request-id:req-1;ts:1700000000;");
        MercadoPagoWebhookValidator validator = new MercadoPagoWebhookValidator("segredo-do-transparente", SEGREDO);

        assertThat(validator.isAssinaturaExigida()).isTrue();
        assertThat(validator.validar("ts=1700000000,v1=" + v1, "req-1", "123456")).isTrue();
        assertThat(new MercadoPagoWebhookValidator("segredo-do-transparente", "outro")
                .validar("ts=1700000000,v1=" + v1, "req-1", "123456")).isFalse();
    }

    @Test
    void semSegredoConfiguradoNaoExigeAssinatura() {
        MercadoPagoWebhookValidator validator = new MercadoPagoWebhookValidator("");

        assertThat(validator.isAssinaturaExigida()).isFalse();
        assertThat(validator.validar(null, null, "123456")).isTrue();
    }
}
