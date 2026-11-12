package br.com.yugiapp.service;

import br.com.yugiapp.dto.ParametroFilterRequestDTO;
import br.com.yugiapp.dto.ParametroRequestDTO;
import br.com.yugiapp.dto.ParametroResponseDTO;
import br.com.yugiapp.enums.TipoParametroEnum;
import br.com.yugiapp.model.Parametro;
import br.com.yugiapp.repository.ParametroRepository;
import br.com.yugiapp.specs.ParametroSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ParametroService {

    private static final String PREFIXO_PRIVADO = "WPP_BOT_";

    private final ParametroRepository parametroRepository;
    private final ParametroSpecifications parametroSpecifications;
    private final ArquivoService arquivoService;

    @Transactional(readOnly = true)
    public Optional<Parametro> findByNome(String nome) {
        return parametroRepository.findByNome(nome);
    }

    /**
     * Leitura pública por nome (usada pelo app). Os parâmetros do wpp-bot guardam segredos (chave da IA,
     * senha do painel de pedidos) e nunca são expostos por aqui; o administrador os consulta pelo /admin.
     */
    @Transactional(readOnly = true)
    public ParametroResponseDTO getResponseByNome(String nome) {
        if (nome == null || nome.regionMatches(true, 0, PREFIXO_PRIVADO, 0, PREFIXO_PRIVADO.length())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parâmetro não encontrado");
        }
        return parametroRepository.findByNome(nome)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parâmetro não encontrado"));
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
        TipoParametroEnum tipo = resolveTipo(request);
        Parametro parametro = Parametro.builder()
                .nome(nome)
                .tipo(tipo)
                // O valor de um parâmetro de imagem só é definido pelo upload.
                .descricao(tipo == TipoParametroEnum.IMAGEM ? null : normalizeDescricao(request.getDescricao()))
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
        TipoParametroEnum tipoAnterior = parametro.getTipo();
        TipoParametroEnum tipo = resolveTipo(request);
        String imagemDescartada = null;
        parametro.setNome(nome);
        parametro.setTipo(tipo);
        if (tipo == TipoParametroEnum.IMAGEM) {
            // A imagem atual é mantida; ela só muda pelo endpoint de upload.
            if (tipoAnterior != TipoParametroEnum.IMAGEM) {
                parametro.setDescricao(null);
            }
        } else {
            if (tipoAnterior == TipoParametroEnum.IMAGEM) {
                imagemDescartada = parametro.getDescricao();
            }
            parametro.setDescricao(normalizeDescricao(request.getDescricao()));
        }
        ParametroResponseDTO response = toResponse(parametroRepository.saveAndFlush(parametro));
        arquivoService.excluir(imagemDescartada);
        return response;
    }

    @Transactional
    public void delete(Long id) {
        Parametro parametro = findById(id);
        String imagem = parametro.getTipo() == TipoParametroEnum.IMAGEM ? parametro.getDescricao() : null;
        parametroRepository.delete(parametro);
        parametroRepository.flush();
        arquivoService.excluir(imagem);
    }

    /**
     * Grava a imagem enviada no servidor, associa ao parâmetro e remove a imagem anterior.
     */
    @Transactional
    public ParametroResponseDTO atualizarImagem(Long id, MultipartFile arquivo) {
        Parametro parametro = findParametroImagem(id);
        String imagemAnterior = parametro.getDescricao();
        String novaImagem = arquivoService.salvarImagem(arquivo, ArquivoService.PASTA_PARAMETROS);
        try {
            parametro.setDescricao(novaImagem);
            parametroRepository.saveAndFlush(parametro);
        } catch (RuntimeException exception) {
            arquivoService.excluir(novaImagem);
            throw exception;
        }
        arquivoService.excluir(imagemAnterior);
        return toResponse(parametro);
    }

    @Transactional
    public ParametroResponseDTO removerImagem(Long id) {
        Parametro parametro = findParametroImagem(id);
        String imagemAnterior = parametro.getDescricao();
        parametro.setDescricao(null);
        parametroRepository.saveAndFlush(parametro);
        arquivoService.excluir(imagemAnterior);
        return toResponse(parametro);
    }

    private Parametro findById(Long id) {
        return parametroRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parâmetro não encontrado"));
    }

    private Parametro findParametroImagem(Long id) {
        Parametro parametro = findById(id);
        if (parametro.getTipo() != TipoParametroEnum.IMAGEM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O parâmetro não é do tipo imagem");
        }
        return parametro;
    }

    private String validateNome(ParametroRequestDTO request) {
        if (request == null || request.getNome() == null || request.getNome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do parâmetro é obrigatório");
        }
        return request.getNome().trim();
    }

    private TipoParametroEnum resolveTipo(ParametroRequestDTO request) {
        return request.getTipo() == null ? TipoParametroEnum.TEXTO : request.getTipo();
    }

    private String normalizeDescricao(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.trim();
    }

    private ParametroResponseDTO toResponse(Parametro parametro) {
        return ParametroResponseDTO.builder()
                .id(parametro.getId())
                .nome(parametro.getNome())
                .descricao(parametro.getDescricao())
                .tipo(parametro.getTipo())
                .build();
    }
}
