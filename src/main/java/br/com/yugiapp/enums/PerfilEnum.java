package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PerfilEnum {
    ROLE_VISITANTE(0, "Visitante"),
    ROLE_ADMINISTRATOR(1, "Administrador");

    private final Integer codigo;
    private final String value;

    public static PerfilEnum fromCodigo(Integer codigo) {
        for (PerfilEnum perfil : PerfilEnum.values()) {
            if (perfil.getCodigo().equals(codigo)) {
                return perfil;
            }
        }
        throw new IllegalArgumentException("Código inválido para o PerfilEnum: " + codigo);
    }

    public static PerfilEnum fromValue(String value) {
        for (PerfilEnum perfil : PerfilEnum.values()) {
            if (perfil.getValue().equals(value)) {
                return perfil;
            }
        }
        throw new IllegalArgumentException("Valor inválido para o PerfilEnum: " + value);
    }
}
