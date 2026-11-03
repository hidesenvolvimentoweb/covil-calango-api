package br.com.yugiapp.specs;

import br.com.yugiapp.dto.ProdutoFilterRequestDTO;
import br.com.yugiapp.model.Produto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class ProdutoSpecifications extends DefaultSpecifications<Produto, ProdutoFilterRequestDTO> {

    @Override
    public Specification<Produto> buildSpecifiction(ProdutoFilterRequestDTO filter) {
        String nome = normalize(filter.getNome());
        String categoria = normalize(filter.getCategoria());
        List<Specification<Produto>> specifications = Arrays.asList(
                getBasicSpecs(),
                wrapSpec(nome, value -> (root, query, builder) ->
                        builder.like(builder.lower(root.get("nome")), "%" + value.toLowerCase() + "%")),
                wrapSpec(categoria, value -> (root, query, builder) ->
                        builder.like(builder.lower(root.get("categoria")), "%" + value.toLowerCase() + "%"))
        );
        return specifications.stream().filter(Objects::nonNull).reduce(Specification::and).orElse(null);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
