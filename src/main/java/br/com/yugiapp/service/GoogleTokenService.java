package br.com.yugiapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * Valida o ID token devolvido pelo login do Google no web, consultando o endpoint tokeninfo do Google,
 * e devolve o e-mail verificado da conta. O e-mail informado pelo cliente não é confiável e não é usado.
 */
@Slf4j
@Service
public class GoogleTokenService {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";
    private static final Set<String> EMISSORES = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public GoogleTokenService(@Value("${app.google.client-id}") String clientId, ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.objectMapper = objectMapper;
    }

    public String validarEObterEmail(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token do Google ausente");
        }
        JsonNode info = consultar(idToken);
        boolean emailVerificado = "true".equalsIgnoreCase(info.path("email_verified").asText());
        String email = info.path("email").asText("");
        if (!clientId.equals(info.path("aud").asText())
                || !EMISSORES.contains(info.path("iss").asText())
                || !emailVerificado
                || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token do Google inválido");
        }
        return email;
    }

    private JsonNode consultar(String idToken) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(TOKENINFO_URL + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token do Google inválido ou expirado");
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            log.error("Falha ao validar o token do Google", exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível validar o login com o Google. Tente novamente");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível validar o login com o Google. Tente novamente");
        }
    }
}
