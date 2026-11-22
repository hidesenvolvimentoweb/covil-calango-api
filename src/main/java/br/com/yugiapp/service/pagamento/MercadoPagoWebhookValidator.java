package br.com.yugiapp.service.pagamento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Valida a assinatura (cabeçalho {@code x-signature}) das notificações do Mercado Pago.
 * <p>
 * A assinatura é um HMAC-SHA256, com a chave secreta da aplicação, do texto
 * {@code id:[data.id];request-id:[x-request-id];ts:[ts];}. Mesmo com a assinatura válida, a API nunca confia no
 * conteúdo da notificação: o pagamento é sempre consultado no Mercado Pago antes de qualquer alteração.
 *
 * @see <a href="https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks">Webhooks</a>
 */
@Component
public class MercadoPagoWebhookValidator {

    private static final String ALGORITMO = "HmacSHA256";

    private final String segredo;

    public MercadoPagoWebhookValidator(@Value("${app.mercadopago.webhook-secret:}") String segredo) {
        this.segredo = segredo;
    }

    /** Sem a chave secreta configurada (ex.: ambiente local) a assinatura não é exigida. */
    public boolean isAssinaturaExigida() {
        return !segredo.isBlank();
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

        byte[] esperado = hmac(manifesto.toString());
        byte[] recebido;
        try {
            recebido = HexFormat.of().parseHex(v1.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return MessageDigest.isEqual(esperado, recebido);
    }

    private byte[] hmac(String texto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 indisponível", exception);
        }
    }
}
