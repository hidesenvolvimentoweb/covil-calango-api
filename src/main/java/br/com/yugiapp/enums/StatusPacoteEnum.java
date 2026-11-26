package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Situação de um pacote deixado na loja. */
@Getter
@RequiredArgsConstructor
public enum StatusPacoteEnum {

    /** Deixado na loja, esperando quem vai receber. */
    AGUARDANDO_RETIRADA("Aguardando retirada"),
    /** Entregue a quem recebe, com a foto da retirada. */
    RETIRADO("Retirado"),
    /** Cancelado pelo cliente ou recusado pela equipe (ex.: item que não é da loja). */
    CANCELADO("Cancelado");

    private final String value;
}
