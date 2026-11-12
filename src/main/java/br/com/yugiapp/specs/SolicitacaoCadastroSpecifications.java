package br.com.yugiapp.specs;

import br.com.yugiapp.dto.SolicitacaoCadastroFilterRequestDTO;
import br.com.yugiapp.model.SolicitacaoCadastro;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class SolicitacaoCadastroSpecifications extends DefaultSpecifications<SolicitacaoCadastro, SolicitacaoCadastroFilterRequestDTO> {

    @Override
    public Specification<SolicitacaoCadastro> buildSpecifiction(SolicitacaoCadastroFilterRequestDTO filter) {
        List<Specification<SolicitacaoCadastro>> specifications = Arrays.asList(
                getBasicSpecs(),
                wrapSpec(normalize(filter.getNome()), value -> containsIgnoreCase(value, "nome")),
                wrapSpec(normalize(filter.getEmail()), value -> containsIgnoreCase(value, "email"))
        );
        return specifications.stream().filter(Objects::nonNull).reduce(Specification::and).orElse(null);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Specification<SolicitacaoCadastro> containsIgnoreCase(String value, String attribute) {
        return (root, query, builder) ->
                builder.like(builder.lower(root.get(attribute)), "%" + value.toLowerCase() + "%");
    }}
