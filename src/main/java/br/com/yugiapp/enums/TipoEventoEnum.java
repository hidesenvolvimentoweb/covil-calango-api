package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoEventoEnum {

    TORNEIO("Torneio"),
    BOARD_GAMES("Board Games"),
    OUTROS("Outros");

    private final String value;

    public static TipoEventoEnum fromValue(String value) {
        for (TipoEventoEnum t : TipoEventoEnum.values()) {
            if (t.getValue().equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Valor inválido para o TipoEventoEnum: " + value);
    }

}
