package br.com.yugiapp.service;

import br.com.yugiapp.enums.TipoParametroEnum;
import br.com.yugiapp.model.Parametro;
import br.com.yugiapp.model.Produto;
import br.com.yugiapp.repository.ParametroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Abertura e fechamento da cozinha (parâmetro COZINHA_ABERTA). Com ela fechada, os produtos preparados na cozinha
 * ({@link Produto#isUsaCozinha()}) não podem ser pedidos; os demais continuam à venda.
 */
@Service
@RequiredArgsConstructor
public class CozinhaService {

    static final String PARAMETRO_COZINHA_ABERTA = "COZINHA_ABERTA";

    private final ParametroRepository parametroRepository;

    /** Sem o parâmetro (ou com um valor diferente de "false"), a cozinha é considerada aberta. */
    @Transactional(readOnly = true)
    public boolean isAberta() {
        return parametroRepository.findByNome(PARAMETRO_COZINHA_ABERTA)
                .map(Parametro::getDescricao)
                .map(valor -> !"false".equalsIgnoreCase(valor.trim()))
                .orElse(true);
    }

    @Transactional
    public boolean definirAberta(boolean aberta) {
        Parametro parametro = parametroRepository.findByNome(PARAMETRO_COZINHA_ABERTA)
                .orElseGet(() -> Parametro.builder().nome(PARAMETRO_COZINHA_ABERTA).tipo(TipoParametroEnum.TEXTO).build());
        parametro.setDescricao(Boolean.toString(aberta));
        parametroRepository.save(parametro);
        return aberta;
    }

    /** Impede pedir um produto da cozinha com ela fechada. */
    public void exigirDisponivel(Produto produto) {
        if (produto.isUsaCozinha() && !isAberta()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A cozinha está fechada: " + produto.getNome() + " não pode ser pedido agora");
        }
    }
}
