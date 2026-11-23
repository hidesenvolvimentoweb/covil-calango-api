package br.com.yugiapp.dto;

import br.com.yugiapp.enums.FormaPagamentoEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fechamento da comanda pela equipe: a forma como o cliente pagou no balcão. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FecharComandaRequestDTO {

    private FormaPagamentoEnum formaPagamento;
}
