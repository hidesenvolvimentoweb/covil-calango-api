package br.com.yugiapp.config.security;

import br.com.yugiapp.config.exception.GlobalExceptionHandler;
import br.com.yugiapp.controller.AuthController;
import br.com.yugiapp.controller.ParametroController;
import br.com.yugiapp.dto.ParametroResponseDTO;
import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.service.AuthService;
import br.com.yugiapp.service.ParametroService;
import br.com.yugiapp.service.RecuperacaoSenhaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {ParametroController.class, AuthController.class, GlobalExceptionHandler.class,
        JwtSecurityConfig.class, JwtTokenService.class, AuthCookieService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres",
        "app.cors.allowed-origins=http://web.local"
})
class JwtSecurityConfigTest {

    private static final String ADMIN = JwtSecurityConfig.PERFIL_ADMINISTRADOR;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private ParametroService parametroService;
    @MockBean
    private AuthService authService;
    @MockBean
    private RecuperacaoSenhaService recuperacaoSenhaService;

    private Cookie cookie(List<String> perfis) {
        return new Cookie(AuthCookieService.NOME_COOKIE, jwtTokenService.generateToken("admin@covil", perfis));
    }

    private MockMultipartFile imagem() {
        return new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
    }

    @Test
    void uploadSemAutenticacaoRetorna401() throws Exception {
        mockMvc.perform(multipart("/api/parametros/1/imagem").file(imagem()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void uploadComUsuarioSemPerfilAdminRetorna403() throws Exception {
        mockMvc.perform(multipart("/api/parametros/1/imagem").file(imagem())
                        .cookie(cookie(List.of("ROLE_VISITANTE")))
                        .header(UserAuthenticationFilter.CABECALHO_CSRF, "XMLHttpRequest"))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadComCookieAdminECabecalhoCsrfEhPermitido() throws Exception {
        when(parametroService.atualizarImagem(anyLong(), any())).thenReturn(ParametroResponseDTO.builder().id(1L).build());
        mockMvc.perform(multipart("/api/parametros/1/imagem").file(imagem())
                        .cookie(cookie(List.of(ADMIN)))
                        .header(UserAuthenticationFilter.CABECALHO_CSRF, "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    void uploadComCookieAdminSemCabecalhoCsrfRetorna401() throws Exception {
        mockMvc.perform(multipart("/api/parametros/1/imagem").file(imagem()).cookie(cookie(List.of(ADMIN))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAdulteradoRetorna401() throws Exception {
        String token = jwtTokenService.generateToken("admin@covil", List.of(ADMIN));
        Cookie adulterado = new Cookie(AuthCookieService.NOME_COOKIE, token.substring(0, token.length() - 2) + "xx");
        mockMvc.perform(get("/api/parametros/admin").cookie(adulterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listagemAdminExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/parametros/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/parametros/admin/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void leituraPublicaPorNomeContinuaLiberada() throws Exception {
        when(parametroService.getResponseByNome("PARAMETRO_REGRA_PERMANENCIA"))
                .thenReturn(ParametroResponseDTO.builder().nome("PARAMETRO_REGRA_PERMANENCIA").build());
        mockMvc.perform(get("/api/parametros/PARAMETRO_REGRA_PERMANENCIA")).andExpect(status().isOk());
    }

    @Test
    void loginGravaCookieHttpOnlySemTokenNoCorpo() throws Exception {
        when(authService.login(any())).thenReturn(UsuarioResponseDTO.builder()
                .email("admin@covil")
                .perfis(List.of(PerfilResponseDTO.builder().name(ADMIN).build()))
                .build());
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString(AuthCookieService.NOME_COOKIE + "="),
                        containsString("HttpOnly"),
                        containsString("Path=/api"))))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void corsPermiteCredenciaisSomenteParaOrigemConfiavel() throws Exception {
        mockMvc.perform(options("/api/parametros/admin")
                        .header("Origin", "http://web.local")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://web.local"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/parametros/admin")
                        .header("Origin", "http://malicioso.local")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}
