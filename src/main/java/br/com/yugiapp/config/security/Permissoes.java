package br.com.yugiapp.config.security;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Nomes das permissões cadastradas em {@code tb_permissao}. Cada rota da API exige uma delas; o acesso
 * de um usuário vem das permissões associadas aos seus perfis (tela Perfis/Permissões do web).
 */
public final class Permissoes {

    public static final String APP_CARDAPIO = "app-cardapio";
    public static final String APP_MEUS_PEDIDOS = "app-meus-pedidos";
    public static final String APP_MINHA_COMANDA = "app-minha-comanda";
    public static final String APP_MEU_PERFIL = "app-meu-perfil";
    public static final String APP_EVENTOS = "app-eventos";

    public static final String WEB_DASHBOARD = "web-dashboard";
    public static final String WEB_SOLICITACOES = "web-solicitacoes";
    public static final String WEB_PEDIDOS = "web-pedidos";
    public static final String WEB_COMANDAS = "web-comandas";
    public static final String WEB_MANTER_PRODUTOS = "web-manter-produtos";
    public static final String WEB_MANTER_USUARIOS = "web-manter-usuarios";
    public static final String WEB_EVENTOS = "web-eventos";

    /** Perfis com acesso total, mesmo que suas permissões sejam alteradas (evita perder o acesso administrativo). */
    public static final String PERFIL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";
    public static final String PERFIL_VISITANTE = "ROLE_VISITANTE";

    private Permissoes() {
    }

    /** Autoridades aceitas por uma rota: as permissões informadas e o perfil administrador. */
    public static String[] exigir(String... permissoes) {
        return Stream.concat(Arrays.stream(permissoes), Stream.of(PERFIL_ADMINISTRADOR)).toArray(String[]::new);
    }
}
