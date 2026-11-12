package br.com.yugiapp.config.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Gera e valida os JWT da API. O segredo vem de {@code app.jwt.secret} (variável JWT_SECRET).
 * Sem segredo configurado, um segredo aleatório é gerado na inicialização: funciona em desenvolvimento,
 * mas invalida todas as sessões a cada reinício, por isso em produção a variável deve ser definida.
 */
@Slf4j
@Service
public class JwtTokenService {

    public static final String CLAIM_PERFIS = "perfis";
    private static final String ISSUER = "covil-calango-api";
    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration validade;

    public JwtTokenService(@Value("${app.jwt.secret:}") String secret,
                           @Value("${app.jwt.expiration-hours:4}") long expirationHours) {
        String segredo = secret == null ? "" : secret.trim();
        if (segredo.isEmpty()) {
            byte[] aleatorio = new byte[48];
            new SecureRandom().nextBytes(aleatorio);
            segredo = Base64.getEncoder().encodeToString(aleatorio);
            log.warn("JWT_SECRET não configurado: usando um segredo aleatório. As sessões serão perdidas a cada reinício.");
        } else if (segredo.length() < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException("JWT_SECRET deve ter pelo menos " + TAMANHO_MINIMO_SEGREDO + " caracteres");
        }
        this.algorithm = Algorithm.HMAC256(segredo);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
        this.validade = Duration.ofHours(expirationHours);
    }

    public Duration getValidade() {
        return validade;
    }

    /** Token de convidado (app), sem perfis. */
    public String generateToken(String subject) {
        return generateToken(subject, List.of());
    }

    /** Token de usuário autenticado; os perfis viram as authorities da requisição. */
    public String generateToken(String subject, List<String> perfis) {
        try {
            Instant agora = Instant.now();
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withIssuedAt(agora)
                    .withExpiresAt(agora.plus(validade))
                    .withSubject(subject)
                    .withClaim(CLAIM_PERFIS, perfis)
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new JWTCreationException("Erro ao gerar token.", exception);
        }
    }

    /** Devolve o token decodificado quando a assinatura, o emissor e a validade conferem. */
    public Optional<DecodedJWT> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(verifier.verify(token));
        } catch (JWTVerificationException exception) {
            return Optional.empty();
        }
    }
}
