package br.com.yugiapp.config.security;

import br.com.yugiapp.service.ArquivoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.com.yugiapp.config.security.Permissoes.*;

@Configuration
@EnableWebSecurity
public class JwtSecurityConfig {

    private final OrigensConfiaveis origensConfiaveis;
    private final ObjectMapper objectMapper;

    public JwtSecurityConfig(OrigensConfiaveis origensConfiaveis, ObjectMapper objectMapper) {
        this.origensConfiaveis = origensConfiaveis;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity,
                                                   AutenticacaoJwtService autenticacaoJwtService,
                                                   AuthCookieService authCookieService) throws Exception {
        return httpSecurity.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(new UserAuthenticationFilter(autenticacaoJwtService, authCookieService),
                        UsernamePasswordAuthenticationFilter.class)
                // Cada rota exige uma permissão do cadastro de Perfis/Permissões (ver Permissoes.exigir).
                // Rotas do visitante que recebem o número da comanda validam também o dono (AcessoService).
                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // O WebSocket tem autorização própria, por mensagem (WebSocketSecurityInterceptor).
                        .requestMatchers("/error", "/covil-ws/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, ArquivoService.URL_PREFIX + "**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/solicitacao-cadastro").permitAll()

                        // Parâmetros: leitura pública por nome (o app usa); o restante é da manutenção.
                        .requestMatchers("/api/parametros/admin", "/api/parametros/admin/**").hasAnyAuthority(exigir(WEB_MANTER_PRODUTOS))
                        .requestMatchers(HttpMethod.GET, "/api/parametros/*").permitAll()
                        .requestMatchers("/api/parametros", "/api/parametros/**").hasAnyAuthority(exigir(WEB_MANTER_PRODUTOS))

                        .requestMatchers("/api/produto/admin", "/api/produto/admin/**").hasAnyAuthority(exigir(WEB_MANTER_PRODUTOS))
                        .requestMatchers(HttpMethod.GET, "/api/produto", "/api/produto/*").hasAnyAuthority(exigir(APP_CARDAPIO, WEB_MANTER_PRODUTOS))
                        .requestMatchers("/api/produto", "/api/produto/**").hasAnyAuthority(exigir(WEB_MANTER_PRODUTOS))

                        .requestMatchers(HttpMethod.POST, "/api/pedido").hasAnyAuthority(exigir(APP_CARDAPIO))
                        .requestMatchers(HttpMethod.GET, "/api/pedido").hasAnyAuthority(exigir(
                                APP_MEUS_PEDIDOS, APP_MINHA_COMANDA, WEB_PEDIDOS, WEB_DASHBOARD, WEB_COMANDAS))
                        .requestMatchers("/api/pedido/**").hasAnyAuthority(exigir(WEB_PEDIDOS))

                        .requestMatchers(HttpMethod.GET, "/api/comanda").hasAnyAuthority(exigir(WEB_COMANDAS, WEB_DASHBOARD))
                        .requestMatchers(HttpMethod.POST, "/api/comanda/gerar/**").hasAnyAuthority(exigir(WEB_SOLICITACOES))
                        // Consulta: o visitante só a própria comanda (validado no controller).
                        .requestMatchers(HttpMethod.GET, "/api/comanda/*").hasAnyAuthority(exigir(
                                PERFIL_VISITANTE, WEB_COMANDAS, WEB_DASHBOARD))
                        // Fechamento (inclusive PUT /fechar) só pela equipe: o cliente fecha a comanda pagando
                        // (a confirmação do pagamento fecha a comanda, ver PagamentoService).
                        .requestMatchers("/api/comanda/**").hasAnyAuthority(exigir(WEB_COMANDAS))

                        // Pagamento: o webhook do provedor é público (a notificação só dispara uma consulta ao
                        // provedor); o visitante só paga a própria comanda (validado no controller).
                        .requestMatchers(HttpMethod.POST, "/api/pagamento/webhook/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagamento/comanda/*").hasAnyAuthority(exigir(
                                APP_MINHA_COMANDA, WEB_COMANDAS, WEB_DASHBOARD))
                        .requestMatchers("/api/pagamento/comanda/*/pix").hasAnyAuthority(exigir(APP_MINHA_COMANDA, WEB_COMANDAS))

                        .requestMatchers("/api/solicitacao", "/api/solicitacao/**").hasAnyAuthority(exigir(WEB_SOLICITACOES))

                        .requestMatchers("/api/relatorio", "/api/relatorio/**").hasAnyAuthority(exigir(WEB_DASHBOARD))

                        .requestMatchers("/api/usuario", "/api/usuario/**").hasAnyAuthority(exigir(WEB_MANTER_USUARIOS))
                        .requestMatchers("/api/perfil", "/api/perfil/**").hasAnyAuthority(exigir(WEB_MANTER_USUARIOS))
                        .requestMatchers("/api/permissao", "/api/permissao/**").hasAnyAuthority(exigir(WEB_MANTER_USUARIOS))
                        .requestMatchers("/api/solicitacao-cadastro", "/api/solicitacao-cadastro/**").hasAnyAuthority(exigir(WEB_MANTER_USUARIOS))

                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, ex) ->
                                escreverErro(request, response, HttpStatus.UNAUTHORIZED, "Sessão expirada ou não autenticada. Entre novamente"))
                        .accessDeniedHandler((request, response, ex) ->
                                escreverErro(request, response, HttpStatus.FORBIDDEN, "Você não tem permissão para realizar esta operação")))
                .build();
    }

    /**
     * Origens confiáveis (CORS_ALLOWED_ORIGINS, por padrão o WEB_URL) podem enviar o cookie de sessão.
     * As demais origens (ex.: o app) continuam com acesso, mas sem credenciais: autenticam pelo cabeçalho
     * Authorization, e o navegador não envia o cookie para elas.
     * O endpoint de WebSocket/SockJS mantém a configuração de CORS própria.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            if (request.getRequestURI().startsWith("/covil-ws")) {
                return null;
            }
            CorsConfiguration config = new CorsConfiguration();
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
            String origem = request.getHeader("Origin");
            if (origensConfiaveis.contem(origem)) {
                config.setAllowedOrigins(List.of(origem));
                config.setAllowCredentials(true);
            } else {
                config.addAllowedOriginPattern("*");
                config.setAllowCredentials(false);
            }
            return config;
        };
    }

    private void escreverErro(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                              String mensagem) throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now().toString());
        corpo.put("status", status.value());
        corpo.put("error", status.getReasonPhrase());
        corpo.put("message", mensagem);
        corpo.put("path", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), corpo);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
