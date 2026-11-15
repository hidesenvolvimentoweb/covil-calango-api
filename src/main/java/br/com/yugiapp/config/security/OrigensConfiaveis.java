package br.com.yugiapp.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Origens confiáveis ({@code CORS_ALLOWED_ORIGINS}, por padrão o WEB_URL): as únicas que podem usar o cookie de
 * sessão, tanto nas requisições HTTP (CORS com credenciais) quanto no handshake do WebSocket.
 */
@Component
public class OrigensConfiaveis {

    private final List<String> origens;

    public OrigensConfiaveis(@Value("${app.cors.allowed-origins}") String origens) {
        this.origens = Arrays.stream(origens.split(","))
                .map(String::trim)
                .map(origem -> origem.endsWith("/") ? origem.substring(0, origem.length() - 1) : origem)
                .filter(origem -> !origem.isEmpty())
                .toList();
    }

    public boolean contem(String origem) {
        return origem != null && origens.contains(origem);
    }
}
