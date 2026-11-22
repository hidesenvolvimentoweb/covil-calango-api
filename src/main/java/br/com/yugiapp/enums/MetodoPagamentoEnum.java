package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MetodoPagamentoEnum {

    PIX("PIX"),
    CARTAO_CREDITO("Cartão de Crédito");

    private final String value;

}
