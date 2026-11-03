package br.com.yugiapp.service;

import br.com.yugiapp.dto.ParametroFilterRequestDTO;
import br.com.yugiapp.dto.ParametroRequestDTO;
import br.com.yugiapp.dto.ParametroResponseDTO;
import br.com.yugiapp.model.Parametro;
import br.com.yugiapp.repository.ParametroRepository;
import br.com.yugiapp.specs.ParametroSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ParametroService {

    private final ParametroRepository parametroRepository;
    private final ParametroSpecifications parametroSpecifications;

    @Transactional(readOnly = true)
    public Optional<Parametro> findByNome(String nome) {
        return parametroRepository.findByNome(nome);
    }

    @Transactional(readOnly = true)
    public Page<ParametroResponseDTO> getAll(ParametroFilterRequestDTO filter, Pageable pageable) {
        return parametroRepository.findAll(parametroSpecifications.getSpecs(filter), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ParametroResponseDTO getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
    public ParametroResponseDTO create(ParametroRequestDTO request) {
        String nome = validateNome(request);
        if (parametroRepository.existsByNomeIgnoreCase(nome)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parâmetro já cadastrado");
        }
        Parametro parametro = Parametro.builder()
                .nome(nome)
                .descricao(normalizeDescricao(request.getDescricao()))
                .build();
        return toResponse(parametroRepository.save(parametro));
    }

    @Transactional
    public ParametroResponseDTO update(Long id, ParametroRequestDTO request) {
        Parametro parametro = findById(id);
        String nome = validateNome(request);
        if (parametroRepository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parâmetro já cadastrado");
        }
        parametro.setNome(nome);
        parametro.setDescricao(normalizeDescricao(request.getDescricao()));
        return toResponse(parametroRepository.save(parametro));
    }

    @Transactional
    public void delete(Long id) {
        parametroRepository.delete(findById(id));
    }

    private Parametro findById(Long id) {
        return parametroRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parâmetro não encontrado"));
    }

    private String validateNome(ParametroRequestDTO request) {
        if (request == null || request.getNome() == null || request.getNome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do parâmetro é obrigatório");
        }
        return request.getNome().trim();
    }

    private String normalizeDescricao(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.trim();
    }

    private ParametroResponseDTO toResponse(Parametro parametro) {
        return ParametroResponseDTO.builder()
                .id(parametro.getId())
                .nome(parametro.getNome())
                .descricao(parametro.getDescricao())
                .build();
    }
}
