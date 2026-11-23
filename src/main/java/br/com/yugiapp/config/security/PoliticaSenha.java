package br.com.yugiapp.config.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/** Regras para as senhas definidas pelos usuários (e pela equipe, no cadastro de usuários). */
public final class PoliticaSenha {

    public static final int TAMANHO_MINIMO = 8;
    /** Limite do BCrypt: o que passa de 72 bytes é ignorado no hash. */
    public static final int TAMANHO_MAXIMO = 72;

    private PoliticaSenha() {
    }

    public static void validar(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve possuir ao menos " + TAMANHO_MINIMO + " caracteres");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve possuir no máximo " + TAMANHO_MAXIMO + " caracteres");
        }
    }
}
