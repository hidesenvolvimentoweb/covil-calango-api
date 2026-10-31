package br.com.yugiapp.service;

import br.com.yugiapp.dto.PerfilRequestDTO;
import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.PerfilFilterRequestDTO;
import br.com.yugiapp.model.Perfil;
import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.repository.PerfilRepository;
import br.com.yugiapp.repository.UsuarioRepository;
import br.com.yugiapp.specs.PerfilSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final PerfilRepository perfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final PerfilSpecifications perfilSpecifications;

    @Transactional(readOnly = true)
    public Page<PerfilResponseDTO> getAll(PerfilFilterRequestDTO filter, Pageable pageable) {
        return perfilRepository.findAll(perfilSpecifications.getSpecs(filter), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PerfilResponseDTO getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
    public PerfilResponseDTO create(PerfilRequestDTO request) {
        String name = validateName(request.getName());
        if (perfilRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Perfil já cadastrado");
        }
        Perfil perfil = perfilRepository.save(Perfil.builder()
                .name(name)
                .permissoes(new ArrayList<>())
                .build());
        return toResponse(perfil);
    }

    @Transactional
    public PerfilResponseDTO update(Long id, PerfilRequestDTO request) {
        Perfil perfil = findById(id);
        String name = validateName(request.getName());
        if (perfilRepository.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Perfil já cadastrado");
        }
        perfil = perfilRepository.save(Perfil.builder()
                .id(perfil.getId())
                .name(name)
                .permissoes(perfil.getPermissoes())
                .build());
        return toResponse(perfil);
    }

    @Transactional
    public void delete(Long id) {
        Perfil perfil = findById(id);
        for (Usuario usuario : usuarioRepository.findAllByPerfis_Id(id)) {
            if (usuario.getPerfis() != null) {
                usuario.getPerfis().removeIf(usuarioPerfil -> usuarioPerfil.getId().equals(id));
            }
        }
        perfilRepository.delete(perfil);
    }

    private Perfil findById(Long id) {
        return perfilRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado"));
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do perfil é obrigatório");
        }
        return name.trim();
    }

    private PerfilResponseDTO toResponse(Perfil perfil) {
        return PerfilResponseDTO.builder()
                .id(perfil.getId())
                .name(perfil.getName())
                .permissoes(perfil.getPermissoes() == null ? List.of() : perfil.getPermissoes().stream()
                        .map(permissao -> permissao.getName())
                        .toList())
                .build();
    }
}
