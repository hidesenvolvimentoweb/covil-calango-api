package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** O que o app precisa para deixar um pacote: a taxa (parâmetro PACOTE_VALOR) e as categorias aceitas. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacoteConfiguracaoResponseDTO {

    private double valorTaxa;
    private List<Categoria> categorias;

    public record Categoria(String valor, String rotulo) {
    }
}
