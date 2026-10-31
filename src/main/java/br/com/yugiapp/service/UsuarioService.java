package br.com.yugiapp.service;

import br.com.yugiapp.config.security.JwtTokenService;
import br.com.yugiapp.dto.UsuarioConvidadoResponseDTO;
import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.UsuarioRequestDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.dto.UsuarioFilterRequestDTO;
import br.com.yugiapp.model.Perfil;
import br.com.yugiapp.model.Solicitacao;
import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.repository.PerfilRepository;
import br.com.yugiapp.repository.UsuarioRepository;
import br.com.yugiapp.specs.UsuarioSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final JwtTokenService jwtTokenService;
    private final PerfilRepository perfilRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioSpecifications usuarioSpecifications;

    @Transactional(readOnly = true)
    public Page<Usuario> getAll(Pageable pageable) {
        return usuarioRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Usuario> getAll() {
        return usuarioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario getById(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> getByEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    @Transactional
    public void delete(Long id) {
        usuarioRepository.delete(getById(id));
    }

    @Transactional
    public Usuario save(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario create(UsuarioRequestDTO request) {
        if (request.getEmail() != null && usuarioRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        Usuario usuario = Usuario.builder()
                .nome(request.getNome())
                .contato(request.getContato())
                .email(request.getEmail())
                .password(encodePassword(request.getPassword()))
                .perfis(new ArrayList<>())
                .build();
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario update(Long id, UsuarioRequestDTO request) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do usuário é obrigatório");
        }
        Usuario usuario = getById(id);
        if (request.getEmail() != null
                && !request.getEmail().equals(usuario.getEmail())
                && usuarioRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        usuario.setNome(request.getNome());
        usuario.setContato(request.getContato());
        usuario.setEmail(request.getEmail());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPassword(encodePassword(request.getPassword()));
        }
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario associateProfile(Long usuarioId, Long perfilId) {
        Usuario usuario = getById(usuarioId);
        Perfil perfil = perfilRepository.findById(perfilId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado"));
        if (usuario.getPerfis() == null) {
            usuario.setPerfis(new ArrayList<>());
        }
        if (usuario.getPerfis().stream().noneMatch(existing -> existing.getId().equals(perfilId))) {
            usuario.getPerfis().add(perfil);
        }
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario disassociateProfile(Long usuarioId, Long perfilId) {
        Usuario usuario = getById(usuarioId);
        if (perfilRepository.findById(perfilId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado");
        }
        if (usuario.getPerfis() != null) {
            usuario.getPerfis().removeIf(perfil -> perfil.getId().equals(perfilId));
        }
        return usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponseDTO> getAllResponses(UsuarioFilterRequestDTO filter, Pageable pageable) {
        return usuarioRepository.findAll(usuarioSpecifications.getSpecs(filter), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO getResponseById(Long id) {
        return toResponse(getById(id));
    }

    private UsuarioResponseDTO toResponse(Usuario usuario) {
        List<PerfilResponseDTO> perfis = usuario.getPerfis() == null ? List.of() : usuario.getPerfis().stream()
                .map(perfil -> PerfilResponseDTO.builder()
                        .id(perfil.getId())
                        .name(perfil.getName())
                        .permissoes(perfil.getPermissoes() == null ? List.of() : perfil.getPermissoes().stream()
                                .map(permissao -> permissao.getName())
                                .toList())
                        .build())
                .toList();

        return UsuarioResponseDTO.builder()
                .id(usuario.getId())
                .nome(usuario.getNome())
                .contato(usuario.getContato())
                .email(usuario.getEmail())
                .perfis(perfis)
                .build();
    }

    private String encodePassword(String password) {
        return password == null || password.isBlank() ? null : passwordEncoder.encode(password);
    }

    // TODO: Remover para criar autenticação
    public UsuarioConvidadoResponseDTO criarUsuarioConvidado(String nome) {
        return UsuarioConvidadoResponseDTO.builder().nome(nome).token(jwtTokenService.generateToken(nome)).build();
    }

    public Usuario criarNovoUsuario(Solicitacao solicitacao) {
        // TODO: ADICIONAR PERFIL PADRÃO
        return usuarioRepository.save(Usuario.builder().nome(solicitacao.getNome()).email(solicitacao.getEmail()).build());
    }

}
