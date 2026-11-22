package br.com.yugiapp.dto;

import br.com.yugiapp.enums.MetodoPagamentoEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.model.Pagamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class PagamentoResponseDTO implements Serializable {

    private Long id;

    private String comanda;

    private MetodoPagamentoEnum metodo;

    private StatusPagamentoEnum status;

    private BigDecimal valor;

    /** Código "copia e cola"; enviado somente enquanto o pagamento está pendente. */
    private String pixCopiaECola;

    /** QR Code (PNG em Base64, sem o prefixo data:image/png;base64,); somente enquanto pendente. */
    private String pixQrCodeBase64;

    private LocalDateTime dataHoraCriacao;

    private LocalDateTime dataHoraExpiracao;

    private LocalDateTime dataHoraPagamento;

    public static PagamentoResponseDTO of(Pagamento pagamento) {
        PagamentoResponseDTO dto = resumo(pagamento);
        if (pagamento.getStatus() == StatusPagamentoEnum.PENDENTE) {
            dto.setPixCopiaECola(pagamento.getPixCopiaECola());
            dto.setPixQrCodeBase64(pagamento.getPixQrCodeBase64());
        }
        return dto;
    }

    /** Sem o QR Code e o "copia e cola": para listagens e para a equipe, que não precisam pagar. */
    public static PagamentoResponseDTO resumo(Pagamento pagamento) {
        return PagamentoResponseDTO.builder()
                .id(pagamento.getId())
                .comanda(pagamento.getComanda().getNumero())
                .metodo(pagamento.getMetodo())
                .status(pagamento.getStatus())
                .valor(pagamento.getValor())
                .dataHoraCriacao(pagamento.getDataHoraCriacao())
                .dataHoraExpiracao(pagamento.getDataHoraExpiracao())
                .dataHoraPagamento(pagamento.getDataHoraPagamento())
                .build();
    }
}
