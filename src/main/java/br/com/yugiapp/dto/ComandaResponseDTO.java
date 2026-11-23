package br.com.yugiapp.dto;

import br.com.yugiapp.enums.FormaPagamentoEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class ComandaResponseDTO implements Serializable {

    private String numero;

    private UsuarioConvidadoResponseDTO usuario;

    private String nomeCliente;

    private LocalDateTime dataHoraEntrada;

    private LocalDateTime dataHoraSaida;

    private Double valorTotal;

    private String status;

    /** Forma de pagamento da comanda fechada; nula enquanto aberta. */
    private FormaPagamentoEnum formaPagamento;

    private List<PedidoResponseDTO> pedidos;

    /** Última tentativa de pagamento pelo app (resumo, sem QR Code); nula se o cliente ainda não tentou pagar. */
    private PagamentoResponseDTO ultimoPagamento;

}
