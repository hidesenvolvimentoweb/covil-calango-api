package br.com.yugiapp.config.security;

import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Na subida da aplicação, converte para BCrypt qualquer senha que ainda esteja gravada em texto puro
 * (cadastros anteriores à criptografia). Assim nenhuma senha fica legível no banco e o login só precisa
 * comparar hashes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriptografiaSenhasLegadasRunner implements ApplicationRunner {

    private static final Pattern PADRAO_BCRYPT = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$.{53}$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Usuario> pendentes = usuarioRepository.findAllByPasswordIsNotNull().stream()
                .filter(usuario -> !PADRAO_BCRYPT.matcher(usuario.getPassword()).matches())
                .toList();
        if (pendentes.isEmpty()) {
            return;
        }
        pendentes.forEach(usuario -> usuario.setPassword(passwordEncoder.encode(usuario.getPassword())));
        usuarioRepository.saveAll(pendentes);
        log.info("{} senha(s) em texto puro convertida(s) para BCrypt", pendentes.size());
    }
}
