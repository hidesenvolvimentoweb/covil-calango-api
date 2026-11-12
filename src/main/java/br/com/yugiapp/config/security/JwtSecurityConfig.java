package br.com.yugiapp.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class JwtSecurityConfig {

    public static final String PERFIL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";

    private final List<String> origensConfiaveis;
    private final ObjectMapper objectMapper;

    public JwtSecurityConfig(@Value("${app.cors.allowed-origins}") String origensConfiaveis, ObjectMapper objectMapper) {
        this.origensConfiaveis = Arrays.stream(origensConfiaveis.split(","))
                .map(String::trim)
                .map(origem -> origem.endsWith("/") ? origem.substring(0, origem.length() - 1) : origem)
                .filter(origem -> !origem.isEmpty())
                .toList();
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity,
                                                   JwtTokenService jwtTokenService,
                                                   AuthCookieService authCookieService) throws Exception {
        return httpSecurity.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(new UserAuthenticationFilter(jwtTokenService, authCookieService),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Parâmetros: leitura pública por nome (o app usa); o restante é restrito ao administrador.
                        .requestMatchers("/api/parametros/admin", "/api/parametros/admin/**").hasAuthority(PERFIL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/api/parametros/*").permitAll()
                        .requestMatchers("/api/parametros", "/api/parametros/**").hasAuthority(PERFIL_ADMINISTRADOR)
                        .anyRequest().permitAll())
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
            if (origem != null && origensConfiaveis.contains(origem)) {
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
