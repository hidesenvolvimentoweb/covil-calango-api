package br.com.yugiapp.service;

import br.com.yugiapp.dto.ProdutoFilterRequestDTO;
import br.com.yugiapp.dto.ProdutoRequestDTO;
import br.com.yugiapp.dto.ProdutoResponseDTO;
import br.com.yugiapp.model.Produto;
import br.com.yugiapp.repository.ProdutoRepository;
import br.com.yugiapp.specs.ProdutoSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoSpecifications produtoSpecifications;

    @Transactional(readOnly = true)
    public Page<ProdutoResponseDTO> getAll(ProdutoFilterRequestDTO filter, Pageable pageable) {
        return produtoRepository.findAll(produtoSpecifications.getSpecs(filter), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<Produto> getAll(Pageable pageable) {
        return produtoRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Produto> getAll() {
        return produtoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Produto getById(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
    }

    @Transactional(readOnly = true)
    public ProdutoResponseDTO getResponseById(Long id) {
        return toResponse(getById(id));
    }

    @Transactional
    public void delete(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        try {
            produtoRepository.delete(produto);
            produtoRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Não é possível excluir um produto que já possui pedidos.", exception);
        }
    }

    @Transactional
    public Produto save(Produto produto) {
        return produtoRepository.save(produto);
    }

    @Transactional
    public ProdutoResponseDTO create(ProdutoRequestDTO request) {
        Produto produto = fromRequest(request);
        produto.setId(null);
        return toResponse(produtoRepository.save(produto));
    }

    @Transactional
    public ProdutoResponseDTO update(Long id, ProdutoRequestDTO request) {
        Produto produto = getById(id);
        Produto updated = fromRequest(request);
        produto.setNome(updated.getNome());
        produto.setCategoria(updated.getCategoria());
        produto.setImagem(updated.getImagem());
        produto.setValor(updated.getValor());
        produto.setIngredientes(updated.getIngredientes());
        produto.setPrazo(updated.getPrazo());
        return toResponse(produtoRepository.save(produto));
    }

    private Produto fromRequest(ProdutoRequestDTO request) {
        if (request == null || request.getNome() == null || request.getNome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do produto é obrigatório");
        }
        if (request.getValor() == null || request.getValor() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor do produto deve ser maior ou igual a zero");
        }
        if (request.getPrazo() != null && request.getPrazo() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Prazo do produto deve ser maior ou igual a zero");
        }
        return Produto.builder()
                .nome(request.getNome().trim())
                .categoria(trimToNull(request.getCategoria()))
                .imagem(trimToNull(request.getImagem()))
                .valor(request.getValor())
                .ingredientes(trimToNull(request.getIngredientes()))
                .prazo(request.getPrazo())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProdutoResponseDTO toResponse(Produto produto) {
        return ProdutoResponseDTO.builder()
                .id(produto.getId())
                .nome(produto.getNome())
                .categoria(produto.getCategoria())
                .imagem(produto.getImagem())
                .valor(produto.getValor())
                .ingredientes(produto.getIngredientes())
                .prazo(produto.getPrazo())
                .build();
    }
}
