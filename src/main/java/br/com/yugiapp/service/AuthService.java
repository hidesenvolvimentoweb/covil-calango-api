package br.com.yugiapp.service;

import br.com.yugiapp.dto.LoginRequestDTO;
import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.enums.TipoProviderAuthEnum;
import br.com.yugiapp.model.Perfil;
import br.com.yugiapp.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UsuarioResponseDTO login(LoginRequestDTO loginRequestDTO) throws Exception {
        if (TipoProviderAuthEnum.GOOGLE.getValue().equals(loginRequestDTO.getProvider())) {
            // TODO: validar token recebido
            // TODO: avaliar criação do usuário caso seja pelo Aplicativo
            return toResponseDTO(usuarioService.getByEmail(loginRequestDTO.getEmail()).orElseThrow());
        }
        if (TipoProviderAuthEnum.CREDENTIALS.getValue().equals(loginRequestDTO.getProvider())) {
            Optional<Usuario> usuario = usuarioService.getByEmail(loginRequestDTO.getEmail());
            if (usuario.isPresent()
                    && loginRequestDTO.getSenha() != null
                    && usuario.get().getPassword() != null
                    && (passwordEncoder.matches(loginRequestDTO.getSenha(), usuario.get().getPassword())
                    || usuario.get().getPassword().equals(loginRequestDTO.getSenha()))) {
                return toResponseDTO(usuario.get());
            }
            throw new Exception();
        }
        throw new Exception();
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
