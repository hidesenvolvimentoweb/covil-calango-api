package br.com.yugiapp.config.websocket;

import br.com.yugiapp.config.security.AuthCookieService;
import br.com.yugiapp.config.security.AutenticacaoJwtService;
import br.com.yugiapp.config.security.JwtTokenService;
import br.com.yugiapp.config.security.OrigensConfiaveis;
import br.com.yugiapp.config.security.PermissaoPerfilService;
import br.com.yugiapp.config.security.Permissoes;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSecurityInterceptorTest {

    private static final String COMANDA = "202615-10";
    private static final String OUTRA_COMANDA = "202615-11";
    private static final String SESSAO = "sessao-1";
    private static final String ATENDENTE = "ROLE_ATENDENTE";
    private static final String COZINHA = "ROLE_COZINHA";
    private static final Map<String, List<String>> PERMISSOES_POR_PERFIL = Map.of(
            Permissoes.PERFIL_VISITANTE, List.of(Permissoes.APP_CARDAPIO, Permissoes.APP_MINHA_COMANDA),
            ATENDENTE, List.of(Permissoes.WEB_SOLICITACOES, Permissoes.WEB_COMANDAS),
            COZINHA, List.of(Permissoes.WEB_PEDIDOS));

    private final JwtTokenService jwtTokenService =
            new JwtTokenService("segredo-de-teste-com-mais-de-32-caracteres", 4, 24);
    private final MessageChannel canal = mock(MessageChannel.class);
    private WebSocketSecurityInterceptor interceptor;
    private AutenticacaoJwtService autenticacaoJwtService;

    @BeforeEach
    void configurar() {
        PermissaoPerfilService permissaoPerfilService = mock(PermissaoPerfilService.class);
        when(permissaoPerfilService.autoridades(any())).thenAnswer(invocacao -> {
            List<String> perfis = invocacao.getArgument(0);
            Set<String> autoridades = new LinkedHashSet<>(perfis);
            perfis.forEach(perfil -> autoridades.addAll(PERMISSOES_POR_PERFIL.getOrDefault(perfil, List.of())));
            return autoridades;
        });
        autenticacaoJwtService = new AutenticacaoJwtService(jwtTokenService, permissaoPerfilService);
        interceptor = new WebSocketSecurityInterceptor(autenticacaoJwtService);
    }

    private String tokenVisitante(String comanda) {
        return jwtTokenService.generateTokenVisitante("Cliente", comanda);
    }

    private Authentication equipe(String perfil) {
        return autenticacaoJwtService.autenticar(jwtTokenService.generateToken("equipe@covil", List.of(perfil))).orElseThrow();
    }

    private Message<byte[]> frame(StompCommand comando, String destino, Authentication usuario, String tokenFrame) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(comando);
        accessor.setSessionId(SESSAO);
        accessor.setSessionAttributes(new HashMap<>());
        if (destino != null) {
            accessor.setDestination(destino);
        }
        if (usuario != null) {
            accessor.setUser(usuario);
        }
        if (tokenFrame != null) {
            accessor.setNativeHeader("Authorization", "Bearer " + tokenFrame);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private boolean permitido(Message<byte[]> mensagem) {
        return interceptor.preSend(mensagem, canal) != null;
    }

    @Test
    void connectAutenticaPeloCabecalhoAuthorization() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, null, null, tokenVisitante(COMANDA));
        interceptor.preSend(connect, canal);
        assertThat(StompHeaderAccessor.getAccessor(connect, StompHeaderAccessor.class).getUser()).isNotNull();
    }

    @Test
    void connectAutenticaPeloCookieCapturadoNoHandshake() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, null, null, null);
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(connect, StompHeaderAccessor.class);
        accessor.getSessionAttributes().put(WebSocketHandshakeInterceptor.ATRIBUTO_TOKEN_COOKIE,
                jwtTokenService.generateToken("equipe@covil", List.of(ATENDENTE)));
        interceptor.preSend(connect, canal);
        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    void anonimoNaoEscutaTopicosDaEquipe() {
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/solicitacoes", null, null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos", null, null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas/" + COMANDA, null, null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos/comanda/" + COMANDA, null, null))).isFalse();
    }

    @Test
    void equipeEscutaConformeAsPermissoesDoPerfil() {
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/solicitacoes", equipe(ATENDENTE), null))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos", equipe(ATENDENTE), null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos", equipe(COZINHA), null))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/solicitacoes", equipe(COZINHA), null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas/" + COMANDA, equipe(ATENDENTE), null))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pagamentos", equipe(ATENDENTE), null))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pagamentos", equipe(COZINHA), null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas", equipe(ATENDENTE), null))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas", equipe(COZINHA), null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos", equipe(Permissoes.PERFIL_ADMINISTRADOR), null))).isTrue();
    }

    @Test
    void visitanteEscutaSomenteAPropriaComanda() {
        String token = tokenVisitante(COMANDA);
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas/" + COMANDA, null, token))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos/comanda/" + COMANDA, null, token))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas/" + OUTRA_COMANDA, null, token))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos/comanda/" + OUTRA_COMANDA, null, token))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pagamentos/" + COMANDA, null, token))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pagamentos/" + OUTRA_COMANDA, null, token))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pagamentos", null, token))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas", null, token))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/solicitacoes", null, token))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos", null, token))).isFalse();
    }

    @Test
    void tokenInvalidoNoFrameNaoAutoriza() {
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas/" + COMANDA, null, "token-invalido"))).isFalse();
    }

    @Test
    void curingasNaoSaoAceitos() {
        Authentication admin = equipe(Permissoes.PERFIL_ADMINISTRADOR);
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/**", admin, null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comanda/*", null, null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comandas/*", admin, null))).isFalse();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/pedidos/**", admin, null))).isFalse();
    }

    @Test
    void respostaDaSolicitacaoExigeUmUuid() {
        assertThat(permitido(frame(StompCommand.SUBSCRIBE,
                "/topic/comanda/0f8fad5b-d9cb-469f-a165-70867728950e", null, null))).isTrue();
        assertThat(permitido(frame(StompCommand.SUBSCRIBE, "/topic/comanda/sessao-1", null, null))).isFalse();
    }

    @Test
    void topicoDeSessaoEhReescritoParaASessaoDoCliente() {
        Message<?> resultado = interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/session/*", null, null), canal);
        assertThat(resultado).isNotNull();
        assertThat(StompHeaderAccessor.wrap(resultado).getDestination()).isEqualTo("/topic/session/" + SESSAO);
    }

    @Test
    void envioDiretoAosTopicosEhBloqueado() {
        Authentication admin = equipe(Permissoes.PERFIL_ADMINISTRADOR);
        assertThat(permitido(frame(StompCommand.SEND, "/topic/comandas/" + COMANDA, admin, null))).isFalse();
        assertThat(permitido(frame(StompCommand.SEND, "/topic/pedidos", null, null))).isFalse();
        assertThat(permitido(frame(StompCommand.SEND, "/app/solicitacao/solicitar", null, null))).isTrue();
    }

    @Test
    void handshakeSoAceitaOCookieDeOrigemConfiavel() throws Exception {
        WebSocketHandshakeInterceptor handshake = new WebSocketHandshakeInterceptor(
                new AuthCookieService(false, "Lax"), new OrigensConfiaveis("http://web.local"));

        assertThat(atributosDoHandshake(handshake, "http://web.local"))
                .containsEntry(WebSocketHandshakeInterceptor.ATRIBUTO_TOKEN_COOKIE, "token-do-cookie");
        assertThat(atributosDoHandshake(handshake, "http://malicioso.local")).isEmpty();
        assertThat(atributosDoHandshake(handshake, null)).isEmpty();
    }

    private Map<String, Object> atributosDoHandshake(WebSocketHandshakeInterceptor handshake, String origem) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/covil-ws/info");
        request.setCookies(new Cookie(AuthCookieService.NOME_COOKIE, "token-do-cookie"));
        if (origem != null) {
            request.addHeader("Origin", origem);
        }
        Map<String, Object> atributos = new HashMap<>();
        boolean continua = handshake.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), mock(WebSocketHandler.class), atributos);
        assertThat(continua).isTrue();
        return atributos;
    }
}
