package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CategoriaProdutoEnum {

    HAMBURGUER("Hamburguer"),
    BEBIDAS("Bebidas"),
    DOCES("Doces"),
    GELADOS("Gelados"),
    COMBOS("Combos"),
    OUTROS("Outros");

    private final String value;

    public static CategoriaProdutoEnum fromValue(String value) {
        for (CategoriaProdutoEnum c : CategoriaProdutoEnum.values()) {
            if (c.getValue().equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Valor inválido para o CategoriaProdutoEnum: " + value);
    }

}
