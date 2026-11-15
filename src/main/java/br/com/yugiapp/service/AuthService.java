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

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

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
     * Confere a senha informada com o hash BCrypt gravado. Nunca há comparação em texto puro: senhas legadas
     * são convertidas na subida da aplicação (ver CriptografiaSenhasLegadasRunner).
     */
    private boolean senhaConfere(Usuario usuario, String senha) {
        String gravada = usuario.getPassword();
        return gravada != null && passwordEncoder.matches(senha, gravada);
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
