package br.com.yugiapp.enums;

/**
 * Define como o valor de um parâmetro é editado na tela de parâmetros.
 * <ul>
 *     <li>{@link #TEXTO}: texto curto, editado em um campo simples.</li>
 *     <li>{@link #TEXTO_LONGO}: texto extenso (ex.: base de conhecimento da IA), editado em uma área de texto.</li>
 *     <li>{@link #IMAGEM}: a descrição guarda o caminho público de uma imagem enviada ao servidor.</li>
 * </ul>
 */
public enum TipoParametroEnum {
    TEXTO,
    TEXTO_LONGO,
    IMAGEM
}
