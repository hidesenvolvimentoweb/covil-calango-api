package br.com.yugiapp.service.pagamento;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Valida a assinatura (cabeçalho {@code x-signature}) das notificações do Mercado Pago.
 * <p>
 * A assinatura é um HMAC-SHA256, com a chave secreta da aplicação, do texto
 * {@code id:[data.id];request-id:[x-request-id];ts:[ts];}. Cada aplicação do Mercado Pago (Checkout Transparente e
 * Checkout Pro) tem a sua chave; a notificação é aceita quando a assinatura confere com qualquer uma delas. Mesmo com a assinatura válida, a API nunca confia no
 * conteúdo da notificação: o pagamento é sempre consultado no Mercado Pago antes de qualquer alteração.
 *
 * @see <a href="https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks">Webhooks</a>
 */
@Component
public class MercadoPagoWebhookValidator {

    private static final String ALGORITMO = "HmacSHA256";

    private final List<String> segredos;

    @Autowired
    public MercadoPagoWebhookValidator(@Value("${app.mercadopago.webhook-secret:}") String segredo,
                                       @Value("${app.mercadopago.checkout-pro.webhook-secret:}") String segredoCheckoutPro) {
        this.segredos = Stream.of(segredo, segredoCheckoutPro)
                .map(valor -> valor == null ? "" : valor.strip())
                .filter(valor -> !valor.isEmpty())
                .distinct()
                .toList();
    }

    MercadoPagoWebhookValidator(String segredo) {
        this(segredo, null);
    }

    /** Sem nenhuma chave secreta configurada (ex.: ambiente local) a assinatura não é exigida. */
    public boolean isAssinaturaExigida() {
        return !segredos.isEmpty();
    }

    public boolean validar(String assinatura, String requestId, String dataId) {
        if (!isAssinaturaExigida()) {
            return true;
        }
        if (assinatura == null || assinatura.isBlank()) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : assinatura.split(",")) {
            String[] chaveValor = parte.trim().split("=", 2);
            if (chaveValor.length != 2) {
                continue;
            }
            switch (chaveValor[0].trim()) {
                case "ts" -> ts = chaveValor[1].trim();
                case "v1" -> v1 = chaveValor[1].trim();
                default -> {
                }
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }

        StringBuilder manifesto = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            // IDs alfanuméricos são assinados em minúsculas.
            manifesto.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            manifesto.append("request-id:").append(requestId).append(';');
        }
        manifesto.append("ts:").append(ts).append(';');

        byte[] recebido;
        try {
            recebido = HexFormat.of().parseHex(v1.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String texto = manifesto.toString();
        return segredos.stream().anyMatch(segredo -> MessageDigest.isEqual(hmac(segredo, texto), recebido));
    }

    private static byte[] hmac(String segredo, String texto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 indisponível", exception);
        }
    }
}
