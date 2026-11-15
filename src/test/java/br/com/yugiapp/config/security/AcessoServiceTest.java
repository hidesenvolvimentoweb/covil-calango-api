package br.com.yugiapp.config.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcessoServiceTest {

    private final AcessoService acessoService = new AcessoService();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String numeroComanda, String... autoridades) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado("usuario", numeroComanda), null,
                Arrays.stream(autoridades).map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void visitanteAcessaApenasAPropriaComanda() {
        autenticar("202615-10", Permissoes.PERFIL_VISITANTE, Permissoes.APP_MINHA_COMANDA);

        assertThatCode(() -> acessoService.validarComanda("202615-10", Permissoes.WEB_COMANDAS)).doesNotThrowAnyException();
        assertThatThrownBy(() -> acessoService.validarComanda("202615-11", Permissoes.WEB_COMANDAS))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> acessoService.validarComanda(null, Permissoes.WEB_COMANDAS))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void equipeComPermissaoAcessaQualquerComanda() {
        autenticar(null, "ROLE_ATENDENTE", Permissoes.WEB_COMANDAS);

        assertThatCode(() -> acessoService.validarComanda("202615-11", Permissoes.WEB_COMANDAS)).doesNotThrowAnyException();
        assertThatCode(() -> acessoService.validarComanda(null, Permissoes.WEB_COMANDAS)).doesNotThrowAnyException();
    }

    @Test
    void equipeSemAPermissaoDaRotaNaoAcessa() {
        autenticar(null, "ROLE_ATENDENTE", Permissoes.WEB_PEDIDOS);

        assertThatThrownBy(() -> acessoService.validarComanda("202615-11", Permissoes.WEB_COMANDAS))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void administradorAcessaSempre() {
        autenticar(null, Permissoes.PERFIL_ADMINISTRADOR);

        assertThatCode(() -> acessoService.validarComanda("202615-11")).doesNotThrowAnyException();
    }

    @Test
    void comandaDoVisitanteSoExisteParaTokenDoApp() {
        autenticar("202615-10", Permissoes.PERFIL_VISITANTE);
        assertThat(acessoService.comandaDoVisitante()).contains("202615-10");

        autenticar(null, Permissoes.PERFIL_ADMINISTRADOR);
        assertThat(acessoService.comandaDoVisitante()).isEmpty();
    }

    @Test
    void semAutenticacaoNaoAcessa() {
        assertThatThrownBy(() -> acessoService.validarComanda("202615-10", Permissoes.WEB_COMANDAS))
                .isInstanceOf(ResponseStatusException.class);
    }
}
