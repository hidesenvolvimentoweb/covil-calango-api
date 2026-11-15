package br.com.yugiapp.config.security;

/**
 * Usuário da requisição, extraído do JWT.
 *
 * @param identificador e-mail (web) ou nome do cliente (app)
 * @param numeroComanda comanda do visitante do app; {@code null} para usuários do web
 */
public record UsuarioAutenticado(String identificador, String numeroComanda) {

    public boolean isVisitante() {
        return numeroComanda != null;
    }
}
