package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoProviderAuthEnum {

    GOOGLE("GOOGLE"),
    CREDENTIALS("CREDENTIAL");

    private final String value;

    public static TipoProviderAuthEnum fromValue(String value) {
        for (TipoProviderAuthEnum s : TipoProviderAuthEnum.values()) {
            if (s.getValue().equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Valor inválido para o TipoProviderAuthEnum: " + value);
    }
}
