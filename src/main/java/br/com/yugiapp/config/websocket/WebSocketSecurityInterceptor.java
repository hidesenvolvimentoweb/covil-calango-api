package br.com.yugiapp.config.websocket;

import br.com.yugiapp.controller.PacoteController;
import br.com.yugiapp.service.ComandaNotificacaoService;
import br.com.yugiapp.config.security.AutenticacaoJwtService;
import br.com.yugiapp.config.security.UserAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static br.com.yugiapp.config.security.Permissoes.*;

/**
 * Autenticação e autorização das mensagens STOMP recebidas dos clientes.
 * <ul>
 *     <li><b>CONNECT</b>: autentica pelo cabeçalho {@code Authorization: Bearer} (app) ou pelo cookie de sessão
 *     capturado no handshake (web, ver {@link WebSocketHandshakeInterceptor}). Sem token, a conexão segue anônima.</li>
 *     <li><b>SUBSCRIBE</b>: cada tópico tem sua regra de acesso; o frame pode trazer o próprio
 *     {@code Authorization} (o app só recebe o token depois de conectado). Curingas não são aceitos, pois o broker
 *     os interpretaria como padrão e entregaria mensagens de outros tópicos.</li>
 *     <li><b>SEND</b>: só para os {@code @MessageMapping} ({@code /app/**}). Envio direto aos tópicos é bloqueado,
 *     para que ninguém publique mensagens falsas (ex.: "comanda fechada") para outros clientes.</li>
 * </ul>
 * Mensagens não autorizadas são descartadas sem derrubar a conexão.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    /** Tópico pelo qual o cliente descobre o próprio id de sessão; é reescrito para o tópico exclusivo da sessão. */
    public static final String TOPICO_SESSAO = "/topic/session/*";
    public static final String PREFIXO_SESSAO = "/topic/session/";

    private static final String CABECALHO_AUTHORIZATION = "Authorization";
    private static final String PREFIXO_APP = "/app/";
    private static final String TOPICO_PEDIDOS = "/topic/pedidos";
    private static final String TOPICO_SOLICITACOES = "/topic/solicitacoes";
    private static final String TOPICO_PAGAMENTOS = "/topic/pagamentos";
    /** Resposta da solicitação de comanda; o id é um UUID aleatório gerado pelo app, conhecido só por ele. */
    private static final Pattern TOPICO_RESPOSTA_SOLICITACAO = Pattern.compile(
            "/topic/comanda/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern TOPICO_COMANDA = Pattern.compile("/topic/comandas/([0-9A-Za-z-]{1,40})");
    private static final Pattern TOPICO_PEDIDOS_COMANDA = Pattern.compile("/topic/pedidos/comanda/([0-9A-Za-z-]{1,40})");
    private static final Pattern TOPICO_PAGAMENTOS_COMANDA = Pattern.compile("/topic/pagamentos/([0-9A-Za-z-]{1,40})");

    private final AutenticacaoJwtService autenticacaoJwtService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        return switch (accessor.getCommand()) {
            case CONNECT, STOMP -> conectar(message, accessor);
            case SUBSCRIBE -> inscrever(message, accessor);
            case SEND -> enviar(message, accessor);
            default -> message;
        };
    }

    private Message<?> conectar(Message<?> message, StompHeaderAccessor accessor) {
        String token = Optional.ofNullable(UserAuthenticationFilter.lerBearer(accessor.getFirstNativeHeader(CABECALHO_AUTHORIZATION)))
                .orElseGet(() -> tokenDoCookie(accessor));
        autenticacaoJwtService.autenticar(token).ifPresent(accessor::setUser);
        return message;
    }

    private Message<?> inscrever(Message<?> message, StompHeaderAccessor accessor) {
        String destino = accessor.getDestination();
        if (destino == null) {
            return negar(accessor, "sem destino");
        }
        if (TOPICO_SESSAO.equals(destino)) {
            return reescreverDestino(message, PREFIXO_SESSAO + accessor.getSessionId());
        }
        if (TOPICO_RESPOSTA_SOLICITACAO.matcher(destino).matches()) {
            return message;
        }

        Authentication autenticacao = autenticacao(accessor);
        boolean permitido;
        Matcher comanda;
        if (TOPICO_SOLICITACOES.equals(destino)) {
            permitido = AutenticacaoJwtService.possuiAlguma(autenticacao, exigir(WEB_SOLICITACOES));
        } else if (TOPICO_PAGAMENTOS.equals(destino) || ComandaNotificacaoService.TOPICO_COMANDAS_EQUIPE.equals(destino)) {
            permitido = AutenticacaoJwtService.possuiAlguma(autenticacao, exigir(WEB_COMANDAS, WEB_DASHBOARD));
        } else if (PacoteController.TOPICO_PACOTES.equals(destino)) {
            permitido = AutenticacaoJwtService.possuiAlguma(autenticacao, exigir(WEB_PACOTES));
        } else if (TOPICO_PEDIDOS.equals(destino)) {
            permitido = AutenticacaoJwtService.possuiAlguma(autenticacao, exigir(WEB_PEDIDOS, WEB_DASHBOARD));
        } else if ((comanda = TOPICO_COMANDA.matcher(destino)).matches()) {
            permitido = donoOuEquipe(autenticacao, comanda.group(1), WEB_COMANDAS, WEB_DASHBOARD);
        } else if ((comanda = TOPICO_PEDIDOS_COMANDA.matcher(destino)).matches()) {
            permitido = donoOuEquipe(autenticacao, comanda.group(1), WEB_PEDIDOS, WEB_COMANDAS, WEB_DASHBOARD);
        } else if ((comanda = TOPICO_PAGAMENTOS_COMANDA.matcher(destino)).matches()) {
            permitido = donoOuEquipe(autenticacao, comanda.group(1), WEB_COMANDAS, WEB_DASHBOARD);
        } else {
            permitido = false;
        }
        return permitido ? message : negar(accessor, destino);
    }

    private Message<?> enviar(Message<?> message, StompHeaderAccessor accessor) {
        String destino = accessor.getDestination();
        return destino != null && destino.startsWith(PREFIXO_APP) ? message : negar(accessor, destino);
    }

    private boolean donoOuEquipe(Authentication autenticacao, String numeroComanda, String... permissoesEquipe) {
        return AutenticacaoJwtService.possuiAlguma(autenticacao, exigir(permissoesEquipe))
                || AutenticacaoJwtService.comandaDoVisitante(autenticacao).map(numeroComanda::equals).orElse(false);
    }

    /** Autenticação do frame (cabeçalho Authorization próprio) ou, na falta dele, a do CONNECT. */
    private Authentication autenticacao(StompHeaderAccessor accessor) {
        String tokenFrame = UserAuthenticationFilter.lerBearer(accessor.getFirstNativeHeader(CABECALHO_AUTHORIZATION));
        if (tokenFrame != null) {
            return autenticacaoJwtService.autenticar(tokenFrame).orElse(null);
        }
        return accessor.getUser() instanceof Authentication autenticacao ? autenticacao : null;
    }

    private String tokenDoCookie(StompHeaderAccessor accessor) {
        Map<String, Object> atributos = accessor.getSessionAttributes();
        return atributos == null ? null : (String) atributos.get(WebSocketHandshakeInterceptor.ATRIBUTO_TOKEN_COOKIE);
    }

    private Message<?> reescreverDestino(Message<?> message, String destino) {
        StompHeaderAccessor copia = StompHeaderAccessor.wrap(message);
        copia.setDestination(destino);
        return MessageBuilder.createMessage(message.getPayload(), copia.getMessageHeaders());
    }

    private Message<?> negar(StompHeaderAccessor accessor, String destino) {
        log.debug("WebSocket: {} para '{}' negado (sessão {})", accessor.getCommand(), destino, accessor.getSessionId());
        return null;
    }
}
