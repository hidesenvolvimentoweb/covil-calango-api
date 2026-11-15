package br.com.yugiapp.config.websocket;

import br.com.yugiapp.config.security.AuthCookieService;
import br.com.yugiapp.config.security.OrigensConfiaveis;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Guarda na sessão do WebSocket o JWT do cookie de sessão do web, para autenticar o CONNECT do STOMP.
 * <p>
 * O cookie só é aceito quando o handshake vem de uma origem confiável: como o navegador envia o cookie sozinho,
 * aceitá-lo de qualquer origem permitiria que um site de terceiros abrisse uma conexão em nome do usuário logado
 * (Cross-Site WebSocket Hijacking). As demais origens (ex.: o app) se autenticam pelo cabeçalho Authorization.
 */
@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATRIBUTO_TOKEN_COOKIE = "covil.tokenCookie";

    private final AuthCookieService authCookieService;
    private final OrigensConfiaveis origensConfiaveis;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest
                && origensConfiaveis.contem(request.getHeaders().getOrigin())) {
            String token = authCookieService.ler(servletRequest.getServletRequest());
            if (token != null) {
                attributes.put(ATRIBUTO_TOKEN_COOKIE, token);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {
    }
}
