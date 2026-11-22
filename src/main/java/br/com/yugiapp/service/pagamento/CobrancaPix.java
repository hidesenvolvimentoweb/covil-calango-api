package br.com.yugiapp.service.pagamento;

import br.com.yugiapp.enums.StatusPagamentoEnum;

/**
 * Cobrança PIX criada no provedor.
 *
 * @param copiaECola    código "copia e cola" (BR Code)
 * @param qrCodeBase64  imagem PNG do QR Code em Base64
 */
public record CobrancaPix(
        String gatewayId,
        StatusPagamentoEnum status,
        String statusGateway,
        String copiaECola,
        String qrCodeBase64) {
}
