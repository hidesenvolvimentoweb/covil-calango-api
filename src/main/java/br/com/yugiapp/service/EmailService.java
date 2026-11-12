package br.com.yugiapp.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:}")
    private String remetente;

    @Value("${app.web.url}")
    private String webUrl;

    /**
     * E-mail enviado quando o administrador finaliza o cadastro: leva a senha temporária
     * e o link para o usuário definir a senha de acesso.
     */
    public void enviarCadastroAprovado(String nome, String email, String senhaTemporaria, String token, long validadeHoras) {
        String link = montarLink(token, true);
        String senha = HtmlUtils.htmlEscape(senhaTemporaria);
        enviar(email, "Covil do Calango - Seu cadastro foi aprovado", link, montarCorpo(
                nome,
                "O seu cadastro foi aprovado! Para acessar o sistema, troque a senha temporária abaixo por uma senha de sua escolha:"
                        + "</p><p style=\"text-align: center; font-size: 20px; font-weight: bold; letter-spacing: 2px;\">" + senha,
                "Criar minha senha",
                link,
                "Este link é válido por " + validadeHoras + " horas. A senha temporária será solicitada na tela de troca de senha "
                        + "e não permite entrar no sistema antes da troca.",
                "Este e-mail será utilizado para o seu login e para a recuperação de senha."),
                "Senha temporária: " + senhaTemporaria);
    }

    public void enviarRecuperacaoSenha(String nome, String email, String token, long validadeMinutos, boolean primeiroAcesso) {
        String link = montarLink(token, primeiroAcesso);
        enviar(email, "Covil do Calango - Recuperação de senha", link, montarCorpo(
                nome,
                "Recebemos um pedido para redefinir a sua senha. Para criar uma nova senha, clique no botão abaixo:",
                "Redefinir senha",
                link,
                "Este link é válido por " + validadeMinutos + " minutos e só pode ser utilizado uma vez."
                        + (primeiroAcesso ? " Tenha em mãos a senha temporária enviada no e-mail de aprovação do seu cadastro." : ""),
                "Se você não solicitou a recuperação de senha, apenas ignore este e-mail: a sua senha continua a mesma."),
                null);
    }

    private String montarLink(String token, boolean primeiroAcesso) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(webUrl)
                .path("/redefinir-senha")
                .queryParam("token", token);
        if (primeiroAcesso) {
            builder.queryParam("primeiroAcesso", true);
        }
        return builder.toUriString();
    }

    private void enviar(String email, String assunto, String link, String corpo, String detalheLogSemSmtp) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || mailHost == null || mailHost.isBlank()) {
            // Sem SMTP configurado (ambiente de desenvolvimento): os dados do e-mail ficam disponíveis apenas no log.
            log.warn("SMTP não configurado (MAIL_HOST). E-mail \"{}\" para {} não enviado. Link: {}{}",
                    assunto, email, link, detalheLogSemSmtp == null ? "" : " | " + detalheLogSemSmtp);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            if (remetente != null && !remetente.isBlank()) {
                helper.setFrom(remetente);
            }
            helper.setTo(email);
            helper.setSubject(assunto);
            helper.setText(corpo, true);
            mailSender.send(message);
        } catch (Exception exception) {
            log.error("Falha ao enviar o e-mail \"{}\" para {}", assunto, email, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível enviar o e-mail. Tente novamente mais tarde.");
        }
    }

    private String montarCorpo(String nome, String mensagem, String textoBotao, String link, String observacao, String rodape) {
        return """
                <div style="font-family: Arial, Helvetica, sans-serif; max-width: 480px; margin: 0 auto; color: #2a3547;">
                  <h2 style="color: #13deb9;">Covil do Calango</h2>
                  <p>Olá, %s!</p>
                  <p>%s</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background: #13deb9; color: #ffffff; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold;">%s</a>
                  </p>
                  <p>%s</p>
                  <p style="font-size: 12px; color: #7c8fac;">Se o botão não funcionar, copie e cole este endereço no navegador:<br/>%s</p>
                  <p style="font-size: 12px; color: #7c8fac;">%s</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(nome == null ? "" : nome), mensagem, link, textoBotao, observacao, link, rodape);
    }
}
