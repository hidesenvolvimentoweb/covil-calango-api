package br.com.yugiapp.service;

import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.PermissaoRequestDTO;
import br.com.yugiapp.dto.PermissaoResponseDTO;
import br.com.yugiapp.dto.PermissaoFilterRequestDTO;
import br.com.yugiapp.model.Perfil;
import br.com.yugiapp.model.Permissao;
import br.com.yugiapp.repository.PerfilRepository;
import br.com.yugiapp.repository.PermissaoRepository;
import br.com.yugiapp.specs.PermissaoSpecifications;
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
public class PermissaoService {

    private final PermissaoRepository permissaoRepository;
    private final PerfilRepository perfilRepository;
    private final PermissaoSpecifications permissaoSpecifications;

    @Transactional(readOnly = true)
    public Page<PermissaoResponseDTO> getAll(PermissaoFilterRequestDTO filter, Pageable pageable) {
        return permissaoRepository.findAll(permissaoSpecifications.getSpecs(filter), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PermissaoResponseDTO getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
    public PermissaoResponseDTO create(PermissaoRequestDTO request) {
        String name = validateName(request.getName());
        if (permissaoRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Permissão já cadastrada");
        }
        return toResponse(permissaoRepository.save(Permissao.builder().name(name).build()));
    }

    @Transactional
    public PermissaoResponseDTO update(Long id, PermissaoRequestDTO request) {
        Permissao permissao = findById(id);
        String name = validateName(request.getName());
        if (permissaoRepository.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Permissão já cadastrada");
        }
        permissao = permissaoRepository.save(Permissao.builder()
                .id(permissao.getId())
                .name(name)
                .perfis(permissao.getPerfis())
                .build());
        return toResponse(permissao);
    }

    @Transactional
    public void delete(Long id) {
        Permissao permissao = findById(id);
        List<Perfil> perfis = perfilRepository.findAllByPermissoes_Id(id);
        for (Perfil perfil : perfis) {
            List<Permissao> permissoes = perfil.getPermissoes() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(perfil.getPermissoes());
            permissoes.removeIf(perfilPermissao -> perfilPermissao.getId().equals(id));
            perfilRepository.save(Perfil.builder()
                    .id(perfil.getId())
                    .name(perfil.getName())
                    .permissoes(permissoes)
                    .build());
        }
        permissaoRepository.delete(permissao);
    }

    @Transactional
    public PerfilResponseDTO associateWithProfile(Long permissaoId, Long perfilId) {
        Permissao permissao = findById(permissaoId);
        Perfil perfil = findProfileById(perfilId);
        List<Permissao> permissoes = perfil.getPermissoes() == null
                ? new ArrayList<>()
                : new ArrayList<>(perfil.getPermissoes());
        if (permissoes.stream().noneMatch(existing -> existing.getId().equals(permissaoId))) {
            permissoes.add(permissao);
        }
        return toProfileResponse(perfilRepository.save(Perfil.builder()
                .id(perfil.getId())
                .name(perfil.getName())
                .permissoes(permissoes)
                .build()));
    }

    @Transactional
    public PerfilResponseDTO disassociateFromProfile(Long permissaoId, Long perfilId) {
        findById(permissaoId);
        Perfil perfil = findProfileById(perfilId);
        List<Permissao> permissoes = perfil.getPermissoes() == null
                ? new ArrayList<>()
                : new ArrayList<>(perfil.getPermissoes());
        permissoes.removeIf(existing -> existing.getId().equals(permissaoId));
        return toProfileResponse(perfilRepository.save(Perfil.builder()
                .id(perfil.getId())
                .name(perfil.getName())
                .permissoes(permissoes)
                .build()));
    }

    private Permissao findById(Long id) {
        return permissaoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permissão não encontrada"));
    }

    private Perfil findProfileById(Long id) {
        return perfilRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado"));
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome da permissão é obrigatório");
        }
        return name.trim();
    }

    private PermissaoResponseDTO toResponse(Permissao permissao) {
        return PermissaoResponseDTO.builder()
                .id(permissao.getId())
                .name(permissao.getName())
                .build();
    }

    private PerfilResponseDTO toProfileResponse(Perfil perfil) {
        return PerfilResponseDTO.builder()
                .id(perfil.getId())
                .name(perfil.getName())
                .permissoes(perfil.getPermissoes() == null ? List.of() : perfil.getPermissoes().stream()
                        .map(Permissao::getName)
                        .toList())
                .build();
    }
}
