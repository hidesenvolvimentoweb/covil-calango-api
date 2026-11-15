package br.com.yugiapp.config.security;

import br.com.yugiapp.model.Perfil;
import br.com.yugiapp.model.Permissao;
import br.com.yugiapp.repository.PerfilRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolve as autoridades de uma requisição: os perfis do token e as permissões associadas a eles.
 * As permissões são lidas do banco a cada requisição, então alterações feitas na tela de Perfis valem
 * na hora, sem precisar de um novo login.
 */
@Service
@RequiredArgsConstructor
public class PermissaoPerfilService {

    private final PerfilRepository perfilRepository;

    @Transactional(readOnly = true)
    public Set<String> autoridades(List<String> perfis) {
        Set<String> autoridades = new LinkedHashSet<>();
        if (perfis == null || perfis.isEmpty()) {
            return autoridades;
        }
        for (Perfil perfil : perfilRepository.findAllByNameIn(perfis)) {
            autoridades.add(perfil.getName());
            if (perfil.getPermissoes() != null) {
                perfil.getPermissoes().stream()
                        .map(Permissao::getName)
                        .filter(Objects::nonNull)
                        .forEach(autoridades::add);
            }
        }
        return autoridades;
    }
}
