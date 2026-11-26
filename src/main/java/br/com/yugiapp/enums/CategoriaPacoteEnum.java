package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * O que pode ser deixado na loja: só itens relacionados a ela (nada de celular, roupa, calçado ou outros objetos
 * pessoais). A equipe recusa (cancela) o pacote que não se encaixar.
 */
@Getter
@RequiredArgsConstructor
public enum CategoriaPacoteEnum {

    CARTA("Carta"),
    DECK("Deck"),
    PLAYMAT("Playmat"),
    ACESSORIO("Acessório (sleeves, deck box, dados...)"),
    PRODUTO_LACRADO("Produto lacrado (booster, caixa...)"),
    BOARD_GAME("Board game");

    private final String value;
}
