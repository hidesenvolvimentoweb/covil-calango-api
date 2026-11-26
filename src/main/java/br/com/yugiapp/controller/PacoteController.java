package br.com.yugiapp.controller;

import br.com.yugiapp.config.security.AcessoService;
import br.com.yugiapp.config.security.UsuarioAutenticado;
import br.com.yugiapp.dto.PacoteConfiguracaoResponseDTO;
import br.com.yugiapp.dto.PacoteResponseDTO;
import br.com.yugiapp.enums.CategoriaPacoteEnum;
import br.com.yugiapp.enums.StatusPacoteEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Pacote;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.service.ComandaNotificacaoService;
import br.com.yugiapp.service.PacoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Pacotes deixados na loja. O cliente do app (app-pacotes) deixa, consulta e cancela os pacotes da própria comanda;
 * a equipe (web-pacotes) lista, cancela e dá baixa na retirada pelo código (ver JwtSecurityConfig).
 */
@RestController
@RequestMapping("/api/pacote")
@RequiredArgsConstructor
public class PacoteController {

    /** A tela de pacotes do web se atualiza com os pacotes deixados, retirados e cancelados. */
    public static final String TOPICO_PACOTES = "/topic/pacotes";

    private final PacoteService pacoteService;
    private final AcessoService acessoService;
    private final ComandaNotificacaoService comandaNotificacaoService;
    private final SimpMessagingTemplate messagingTemplate;

    /** Taxa por pacote e categorias aceitas. */
    @GetMapping("/configuracao")
    public PacoteConfiguracaoResponseDTO getConfiguracao() {
        return pacoteService.getConfiguracao();
    }

    /** Pacotes deixados com a comanda deste aparelho (app). */
    @GetMapping("/meus")
    public List<PacoteResponseDTO> getMeus() {
        return pacoteService.getDaComanda(comandaObrigatoria());
    }

    /** O cliente deixa um pacote: a foto é obrigatória e a taxa entra na comanda. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PacoteResponseDTO depositar(@RequestParam("categoria") CategoriaPacoteEnum categoria,
                                       @RequestParam("descricao") String descricao,
                                       @RequestParam("destinatario") String destinatario,
                                       @RequestParam("foto") MultipartFile foto) {
        Pacote pacote = pacoteService.depositar(comandaObrigatoria(), categoria, descricao, destinatario, foto);
        notificar(pacote, true);
        return pacoteService.toResponse(pacote, false);
    }

    /** O cliente cancela um pacote ainda não retirado: a taxa sai da comanda. */
    @DeleteMapping("/meus/{id}")
    public void cancelarMeu(@PathVariable Long id) {
        notificar(pacoteService.cancelarPeloCliente(id, comandaObrigatoria()), true);
    }

    /** Pacotes para a equipe ({@code ?status=AGUARDANDO_RETIRADA}; sem filtro, todos), dos mais recentes. */
    @GetMapping
    public List<PacoteResponseDTO> getAll(@RequestParam(name = "status", required = false) List<StatusPacoteEnum> status) {
        return pacoteService.getAll(status);
    }

    @GetMapping("/codigo/{codigo}")
    public PacoteResponseDTO getByCodigo(@PathVariable String codigo) {
        return pacoteService.getByCodigo(codigo);
    }

    /** Baixa da retirada: o código informado por quem recebe e a foto do item entregue. */
    @PostMapping(path = "{id}/retirada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PacoteResponseDTO retirar(@PathVariable Long id, @RequestParam("codigo") String codigo,
                                     @RequestParam("foto") MultipartFile foto) {
        Pacote pacote = pacoteService.retirar(id, codigo, foto, atendente());
        notificar(pacote, false);
        return pacoteService.toResponse(pacote, true);
    }

    /** A equipe cancela ou recusa um pacote ainda não retirado. */
    @DeleteMapping("{id}")
    public void cancelar(@PathVariable Long id) {
        notificar(pacoteService.cancelarPelaEquipe(id), true);
    }

    private String comandaObrigatoria() {
        return acessoService.comandaDoVisitante()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Gere uma comanda para deixar um pacote"));
    }

    private static String atendente() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario.identificador();
        }
        return autenticacao == null ? null : autenticacao.getName();
    }

    /**
     * Avisa a tela de pacotes e a de comandas da equipe e, quando a taxa sai da comanda, o app do cliente (tópico dos
     * pedidos da comanda): o pedido chega como cancelado e deixa de entrar no total.
     */
    private void notificar(Pacote pacote, boolean comandaAlterada) {
        PacoteResponseDTO resposta = pacoteService.toResponse(pacote, true);
        // Como na listagem: o código só é conhecido por quem deixou e por quem vai receber.
        if (pacote.getStatus() == StatusPacoteEnum.AGUARDANDO_RETIRADA) {
            resposta.setCodigo(null);
        }
        messagingTemplate.convertAndSend(TOPICO_PACOTES, resposta);
        if (!comandaAlterada) {
            return;
        }
        String numero = pacote.getComanda().getNumero();
        Pedido taxaRemovida = pacote.getTaxaRemovida();
        if (taxaRemovida != null) {
            taxaRemovida.setStatus(StatusPedidoEnum.CANCELADO);
            messagingTemplate.convertAndSend("/topic/pedidos/comanda/" + numero, taxaRemovida);
        }
        comandaNotificacaoService.notificarAlteracao(numero);
    }
}
