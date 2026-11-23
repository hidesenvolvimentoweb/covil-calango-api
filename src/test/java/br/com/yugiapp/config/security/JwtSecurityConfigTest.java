package br.com.yugiapp.config.security;

import br.com.yugiapp.config.exception.GlobalExceptionHandler;
import br.com.yugiapp.controller.AuthController;
import br.com.yugiapp.controller.ComandaController;
import br.com.yugiapp.controller.PagamentoController;
import br.com.yugiapp.controller.PedidoController;
import br.com.yugiapp.dto.PagamentoResponseDTO;
import br.com.yugiapp.service.PagamentoService;
import br.com.yugiapp.service.pagamento.MercadoPagoWebhookValidator;
import br.com.yugiapp.dto.ComandaResponseDTO;
import br.com.yugiapp.dto.PedidoFilterRequestDTO;
import br.com.yugiapp.controller.ParametroController;
import br.com.yugiapp.dto.ParametroResponseDTO;
import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.service.AuthService;
import br.com.yugiapp.service.ComandaService;
import br.com.yugiapp.service.PedidoService;
import br.com.yugiapp.service.WhatsappService;
import br.com.yugiapp.service.ParametroService;
import br.com.yugiapp.service.RecuperacaoSenhaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {ParametroController.class, AuthController.class, ComandaController.class,
        PedidoController.class, PagamentoController.class, GlobalExceptionHandler.class, JwtSecurityConfig.class, JwtTokenService.class,
        AuthCookieService.class, AcessoService.class, AutenticacaoJwtService.class, OrigensConfiaveis.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres",
        "app.cors.allowed-origins=http://web.local"
})
class JwtSecurityConfigTest {

    private static final String ADMIN = Permissoes.PERFIL_ADMINISTRADOR;
    private static final String ATENDENTE = "ROLE_ATENDENTE";
    private static final String COMANDA = "202615-10";

    /** Permissões de cada perfil, como estariam cadastradas na tela de Perfis. */
    private static final Map<String, List<String>> PERMISSOES_POR_PERFIL = Map.of(
            Permissoes.PERFIL_VISITANTE, List.of(Permissoes.APP_CARDAPIO, Permissoes.APP_MEUS_PEDIDOS,
                    Permissoes.APP_MINHA_COMANDA, Permissoes.APP_MEU_PERFIL),
            ATENDENTE, List.of(Permissoes.WEB_COMANDAS, Permissoes.WEB_MANTER_PRODUTOS));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private br.com.yugiapp.service.ComandaNotificacaoService comandaNotificacaoService;
    @MockBean
    private ParametroService parametroService;
    @MockBean
    private AuthService authService;
    @MockBean
    private RecuperacaoSenhaService recuperacaoSenhaService;
    @MockBean
    private PermissaoPerfilService permissaoPerfilService;
    @MockBean
    private ComandaService comandaService;
    @MockBean
    private PedidoService pedidoService;
    @MockBean
    private WhatsappService whatsappService;
    @MockBean
    private SimpMessagingTemplate messagingTemplate;
    @MockBean
    private PagamentoService pagamentoService;
    @MockBean
    private MercadoPagoWebhookValidator mercadoPagoWebhookValidator;

    @BeforeEach
    void configurarPermissoes() {
        when(permissaoPerfilService.autoridades(any())).thenAnswer(invocacao -> {
            List<String> perfis = invocacao.getArgument(0);
            Set<String> autoridades = new LinkedHashSet<>(perfis);
            perfis.forEach(perfil -> autoridades.addAll(PERMISSOES_POR_PERFIL.getOrDefault(perfil, List.of())));
            return autoridades;
        });
    }

    private String bearerVisitante(String numeroComanda) {
        return "Bearer " + jwtTokenService.generateTokenVisitante("Cliente", numeroComanda);
    }

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
                        // Path "/" para o cookie chegar também ao handshake do WebSocket (/covil-ws).
                        containsString("Path=/;"))))
                // Remove o cookie das versões anteriores (Path=/api), que teria precedência nas rotas /api.
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString(AuthCookieService.NOME_COOKIE + "=;"),
                        containsString("Path=/api"),
                        containsString("Max-Age=0")))))
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

    @Test
    void perfilSemSerAdminAcessaPelaPermissao() throws Exception {
        when(parametroService.atualizarImagem(anyLong(), any())).thenReturn(ParametroResponseDTO.builder().id(1L).build());
        mockMvc.perform(multipart("/api/parametros/1/imagem").file(imagem())
                        .cookie(cookie(List.of(ATENDENTE)))
                        .header(UserAuthenticationFilter.CABECALHO_CSRF, "XMLHttpRequest"))
                .andExpect(status().isOk());
    }

    @Test
    void rotasDaEquipeExigemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/usuario")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/usuario").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/comanda")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pedido")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/produto")).andExpect(status().isUnauthorized());
    }

    @Test
    void visitanteNaoAcessaRotasDaEquipe() throws Exception {
        String visitante = bearerVisitante(COMANDA);
        mockMvc.perform(get("/api/usuario").header("Authorization", visitante)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/comanda").header("Authorization", visitante)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parametros/admin").header("Authorization", visitante)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/pedido/aceitar/1").header("Authorization", visitante)).andExpect(status().isForbidden());
    }

    @Test
    void visitanteAcessaApenasAPropriaComanda() throws Exception {
        when(comandaService.getResponseDTOByNumero(COMANDA)).thenReturn(ComandaResponseDTO.builder().numero(COMANDA).build());
        mockMvc.perform(get("/api/comanda/" + COMANDA).header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/comanda/202615-99").header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/comanda/fechar/202615-99").header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void visitanteNaoFechaAComandaSemPagar() throws Exception {
        mockMvc.perform(put("/api/comanda/fechar/" + COMANDA).header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isForbidden());
        verify(comandaService, never()).fechar(any(), any());

        mockMvc.perform(put("/api/comanda/fechar/" + COMANDA).cookie(cookie(List.of(ATENDENTE)))
                        .header(UserAuthenticationFilter.CABECALHO_CSRF, "XMLHttpRequest")
                        .contentType("application/json").content("{\"formaPagamento\":\"DINHEIRO\"}"))
                .andExpect(status().isOk());
        verify(comandaService).fechar(COMANDA, br.com.yugiapp.enums.FormaPagamentoEnum.DINHEIRO);
    }

    @Test
    void equipeInformaAFormaDePagamentoAoFecharAComanda() throws Exception {
        mockMvc.perform(put("/api/comanda/fechar/" + COMANDA).cookie(cookie(List.of(ATENDENTE)))
                        .header(UserAuthenticationFilter.CABECALHO_CSRF, "XMLHttpRequest"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/comanda/fechar/" + COMANDA).cookie(cookie(List.of(ATENDENTE)))
                        .header(UserAuthenticationFilter.CABECALHO_CSRF, "XMLHttpRequest")
                        .contentType("application/json").content("{\"formaPagamento\":\"CHEQUE\"}"))
                .andExpect(status().isBadRequest());
        verify(comandaService, never()).fechar(any(), any());
    }

    @Test
    void visitanteListaSomenteOsPedidosDaPropriaComanda() throws Exception {
        mockMvc.perform(get("/api/pedido").header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isOk());
        ArgumentCaptor<PedidoFilterRequestDTO> filtro = ArgumentCaptor.forClass(PedidoFilterRequestDTO.class);
        verify(pedidoService).getAllByFilters(filtro.capture());
        assertThat(filtro.getValue().getComanda()).isEqualTo(COMANDA);

        mockMvc.perform(get("/api/pedido").param("comanda", "202615-99").header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void visitanteNaoFazPedidoEmOutraComanda() throws Exception {
        mockMvc.perform(post("/api/pedido").header("Authorization", bearerVisitante(COMANDA))
                        .contentType("application/json").content("{\"comanda\":\"202615-99\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void equipeComPermissaoAcessaQualquerComanda() throws Exception {
        when(comandaService.getResponseDTOByNumero("202615-99")).thenReturn(ComandaResponseDTO.builder().build());
        mockMvc.perform(get("/api/comanda/202615-99").cookie(cookie(List.of(ATENDENTE))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/usuario").cookie(cookie(List.of(ATENDENTE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void visitantePagaApenasAPropriaComanda() throws Exception {
        when(pagamentoService.gerarPix(COMANDA)).thenReturn(PagamentoResponseDTO.builder().comanda(COMANDA).build());
        mockMvc.perform(post("/api/pagamento/comanda/" + COMANDA + "/pix").header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pagamento/comanda/202615-99/pix").header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pagamento/comanda/202615-99").header("Authorization", bearerVisitante(COMANDA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pagamento/comanda/" + COMANDA + "/pix")).andExpect(status().isUnauthorized());
    }

    @Test
    void webhookDoMercadoPagoEhPublicoEConfereAssinatura() throws Exception {
        when(mercadoPagoWebhookValidator.validar("ts=1,v1=ab", "req-1", "123")).thenReturn(true);
        mockMvc.perform(post("/api/pagamento/webhook/mercadopago").param("data.id", "123").param("type", "payment")
                        .header("x-signature", "ts=1,v1=ab").header("x-request-id", "req-1"))
                .andExpect(status().isOk());
        verify(pagamentoService).processarNotificacao("123");

        mockMvc.perform(post("/api/pagamento/webhook/mercadopago").param("data.id", "456").param("type", "payment")
                        .header("x-signature", "ts=1,v1=00").header("x-request-id", "req-2"))
                .andExpect(status().isUnauthorized());
        verify(pagamentoService, never()).processarNotificacao("456");
    }
}
