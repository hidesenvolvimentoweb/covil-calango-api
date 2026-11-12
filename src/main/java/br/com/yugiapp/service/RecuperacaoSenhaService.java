package br.com.yugiapp.service;

import br.com.yugiapp.model.RecuperacaoSenha;
import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.repository.RecuperacaoSenhaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private static final Duration VALIDADE_RECUPERACAO = Duration.ofMinutes(60);
    private static final Duration VALIDADE_PRIMEIRO_ACESSO = Duration.ofHours(48);
    private static final int TAMANHO_MINIMO_SENHA = 6;
    private static final int TAMANHO_SENHA_TEMPORARIA = 10;
    // Sem caracteres ambíguos (0/O, 1/l/I) para facilitar a digitação.
    private static final String CARACTERES_SENHA_TEMPORARIA = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RecuperacaoSenhaRepository recuperacaoSenhaRepository;
    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /**
     * Primeiro acesso de um usuário recém-aprovado: gera uma senha temporária aleatória, grava-a no usuário
     * e envia por e-mail a senha junto do link de troca. Enquanto a troca não é feita, o login fica bloqueado.
     */
    @Transactional
    public Usuario enviarPrimeiroAcesso(Usuario usuario) {
        StringBuilder senhaTemporaria = new StringBuilder(TAMANHO_SENHA_TEMPORARIA);
        for (int i = 0; i < TAMANHO_SENHA_TEMPORARIA; i++) {
            senhaTemporaria.append(CARACTERES_SENHA_TEMPORARIA.charAt(
                    SECURE_RANDOM.nextInt(CARACTERES_SENHA_TEMPORARIA.length())));
        }
        usuario.setPassword(passwordEncoder.encode(senhaTemporaria));
        usuario.setSenhaTemporaria(true);
        Usuario salvo = usuarioService.save(usuario);

        String token = gerarToken(salvo.getId(), VALIDADE_PRIMEIRO_ACESSO);
        emailService.enviarCadastroAprovado(salvo.getNome(), salvo.getEmail(), senhaTemporaria.toString(),
                token, VALIDADE_PRIMEIRO_ACESSO.toHours());
        return salvo;
    }

    /**
     * Envia o link de recuperação caso exista um usuário com o e-mail informado.
     * Não informa ao chamador se o e-mail está ou não cadastrado.
     */
    @Transactional
    public void solicitar(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o e-mail");
        }
        String emailInformado = email.trim();
        Optional<Usuario> usuario = usuarioService.getByEmail(emailInformado)
                .or(() -> usuarioService.getByEmail(emailInformado.toLowerCase()));
        if (usuario.isEmpty()) {
            return;
        }

        String token = gerarToken(usuario.get().getId(), VALIDADE_RECUPERACAO);
        emailService.enviarRecuperacaoSenha(usuario.get().getNome(), usuario.get().getEmail(), token,
                VALIDADE_RECUPERACAO.toMinutes(), usuario.get().isSenhaTemporaria());
    }

    @Transactional
    public void redefinir(String token, String senhaTemporaria, String novaSenha) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link de recuperação inválido");
        }
        if (novaSenha == null || novaSenha.length() < TAMANHO_MINIMO_SENHA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve possuir ao menos " + TAMANHO_MINIMO_SENHA + " caracteres");
        }
        RecuperacaoSenha recuperacao = recuperacaoSenhaRepository.findByTokenHash(hash(token.trim()))
                .filter(r -> r.getDataHoraExpiracao().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Link inválido ou expirado. Solicite um novo link em \"Esqueceu a senha?\"."));

        Usuario usuario = usuarioService.getById(recuperacao.getUsuarioId());
        if (usuario.isSenhaTemporaria()) {
            if (senhaTemporaria == null || usuario.getPassword() == null
                    || !passwordEncoder.matches(senhaTemporaria.trim(), usuario.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Senha temporária incorreta. Confira a senha enviada para o seu e-mail.");
            }
            if (passwordEncoder.matches(novaSenha, usuario.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A nova senha deve ser diferente da senha temporária");
            }
        }
        usuario.setPassword(passwordEncoder.encode(novaSenha));
        usuario.setSenhaTemporaria(false);
        usuarioService.save(usuario);
        recuperacaoSenhaRepository.deleteAllByUsuarioId(usuario.getId());
    }

    /** Gera um novo token para o usuário, invalidando os links enviados anteriormente. */
    private String gerarToken(Long usuarioId, Duration validade) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        recuperacaoSenhaRepository.deleteAllByUsuarioId(usuarioId);
        recuperacaoSenhaRepository.flush();
        recuperacaoSenhaRepository.save(RecuperacaoSenha.builder()
                .usuarioId(usuarioId)
                .tokenHash(hash(token))
                .dataHoraExpiracao(LocalDateTime.now().plus(validade))
                .build());
        return token;
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
