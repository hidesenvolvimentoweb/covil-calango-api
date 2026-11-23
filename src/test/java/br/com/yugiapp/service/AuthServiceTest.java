package br.com.yugiapp.service;

import br.com.yugiapp.config.security.LimiteTentativasService;
import br.com.yugiapp.dto.LoginRequestDTO;
import br.com.yugiapp.enums.TipoProviderAuthEnum;
import br.com.yugiapp.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String EMAIL = "ana@covil";
    private static final String SENHA = "senha-correta";

    /** Custo baixo só para o teste ficar rápido. */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private AuthService service;

    @BeforeEach
    void configurar() {
        service = new AuthService(usuarioService, passwordEncoder, mock(GoogleTokenService.class),
                new LimiteTentativasService());
        when(usuarioService.getByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioService.getByEmail(EMAIL)).thenReturn(Optional.of(Usuario.builder().id(1L).nome("Ana").email(EMAIL)
                .password(passwordEncoder.encode(SENHA)).perfis(List.of()).build()));
    }

    private static LoginRequestDTO login(String email, String senha) {
        return LoginRequestDTO.builder().provider(TipoProviderAuthEnum.CREDENTIALS.getValue()).email(email).senha(senha)
                .build();
    }

    private static void assertErro(Runnable acao, HttpStatus status, String mensagem) {
        assertThatThrownBy(acao::run).isInstanceOfSatisfying(ResponseStatusException.class, erro -> {
            assertThat(erro.getStatusCode()).isEqualTo(status);
            assertThat(erro.getReason()).startsWith(mensagem);
        });
    }

    @Test
    void emailInexistenteESenhaErradaTemAMesmaResposta() {
        assertErro(() -> service.login(login("ninguem@covil", SENHA)), HttpStatus.UNAUTHORIZED,
                AuthService.CREDENCIAIS_INVALIDAS);
        assertErro(() -> service.login(login(EMAIL, "senha-errada")), HttpStatus.UNAUTHORIZED,
                AuthService.CREDENCIAIS_INVALIDAS);
    }

    @Test
    void bloqueiaAContaDepoisDeSenhasErradasSeguidas() {
        for (int i = 0; i < AuthService.MAXIMO_FALHAS_LOGIN; i++) {
            assertErro(() -> service.login(login(EMAIL, "senha-errada")), HttpStatus.UNAUTHORIZED,
                    AuthService.CREDENCIAIS_INVALIDAS);
        }
        // Bloqueada, nem a senha certa entra até a janela passar; o e-mail digitado com outra caixa também conta.
        assertErro(() -> service.login(login(EMAIL, SENHA)), HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas");
        assertErro(() -> service.login(login("ANA@covil", SENHA)), HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas");
    }

    @Test
    void emailSemCadastroTambemEhBloqueado() {
        // Senão o bloqueio (ou a falta dele) revelaria quais e-mails têm cadastro.
        for (int i = 0; i < AuthService.MAXIMO_FALHAS_LOGIN; i++) {
            assertErro(() -> service.login(login("ninguem@covil", SENHA)), HttpStatus.UNAUTHORIZED,
                    AuthService.CREDENCIAIS_INVALIDAS);
        }
        assertErro(() -> service.login(login("ninguem@covil", SENHA)), HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas");
    }

    @Test
    void loginCertoZeraAsFalhas() {
        for (int i = 0; i < AuthService.MAXIMO_FALHAS_LOGIN - 1; i++) {
            assertErro(() -> service.login(login(EMAIL, "senha-errada")), HttpStatus.UNAUTHORIZED,
                    AuthService.CREDENCIAIS_INVALIDAS);
        }
        assertThat(service.login(login(EMAIL, SENHA)).getEmail()).isEqualTo(EMAIL);
        for (int i = 0; i < AuthService.MAXIMO_FALHAS_LOGIN - 1; i++) {
            assertErro(() -> service.login(login(EMAIL, "senha-errada")), HttpStatus.UNAUTHORIZED,
                    AuthService.CREDENCIAIS_INVALIDAS);
        }
        assertThat(service.login(login(EMAIL, SENHA)).getEmail()).isEqualTo(EMAIL);
    }
}
