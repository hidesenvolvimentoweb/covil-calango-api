package br.com.yugiapp.service;

import br.com.yugiapp.dto.LoginRequestDTO;
import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.enums.TipoProviderAuthEnum;
import br.com.yugiapp.model.Perfil;
import br.com.yugiapp.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern PADRAO_BCRYPT = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$.{53}$");

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenService googleTokenService;

    @Transactional
    public UsuarioResponseDTO login(LoginRequestDTO loginRequestDTO) {
        if (TipoProviderAuthEnum.GOOGLE.getValue().equals(loginRequestDTO.getProvider())) {
            // O e-mail vem do token validado no Google, nunca do corpo da requisição.
            String email = googleTokenService.validarEObterEmail(loginRequestDTO.getIdToken());
            // TODO: avaliar criação do usuário caso seja pelo Aplicativo
            return toResponseDTO(usuarioService.getByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                            "Não existe um usuário cadastrado para esta conta Google")));
        }
        if (TipoProviderAuthEnum.CREDENTIALS.getValue().equals(loginRequestDTO.getProvider())) {
            if (loginRequestDTO.getEmail() == null || loginRequestDTO.getEmail().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o e-mail");
            }
            if (loginRequestDTO.getSenha() == null || loginRequestDTO.getSenha().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha");
            }
            Usuario usuario = usuarioService.getByEmail(loginRequestDTO.getEmail().trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));
            if (!senhaConfere(usuario, loginRequestDTO.getSenha())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha incorreta");
            }
            if (usuario.isSenhaTemporaria()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Antes do primeiro acesso é necessário trocar a senha temporária pelo link enviado para o seu e-mail");
            }
            return toResponseDTO(usuario);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forma de autenticação inválida");
    }

    /**
     * Confere a senha com o BCrypt gravado. Senhas antigas gravadas em texto puro ainda são aceitas uma vez e
     * convertidas para BCrypt; a comparação em texto puro nunca é feita contra um hash, senão o próprio hash
     * funcionaria como senha.
     */
    private boolean senhaConfere(Usuario usuario, String senha) {
        String gravada = usuario.getPassword();
        if (gravada == null) {
            return false;
        }
        if (PADRAO_BCRYPT.matcher(gravada).matches()) {
            return passwordEncoder.matches(senha, gravada);
        }
        if (!MessageDigest.isEqual(gravada.getBytes(StandardCharsets.UTF_8), senha.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        usuario.setPassword(passwordEncoder.encode(senha));
        usuarioService.save(usuario);
        return true;
    }

    private UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        List<PerfilResponseDTO> perfis = usuario.getPerfis() == null ? List.of() : usuario.getPerfis().stream()
                .map(this::toPerfilResponseDTO)
                .toList();

        return UsuarioResponseDTO.builder()
                .id(usuario.getId())
                .nome(usuario.getNome())
                .contato(usuario.getContato())
                .email(usuario.getEmail())
                .perfis(perfis)
                .build();
    }

    private PerfilResponseDTO toPerfilResponseDTO(Perfil perfil) {
        return PerfilResponseDTO.builder()
                .id(perfil.getId())
                .name(perfil.getName())
                .permissoes(perfil.getPermissoes() == null ? List.of() : perfil.getPermissoes().stream()
                        .map(permissao -> permissao.getName())
                        .toList())
                .build();
    }
}
