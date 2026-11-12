package br.com.yugiapp.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

/**
 * Armazena no disco do servidor os arquivos enviados pelos usuários. O diretório raiz ({@code app.upload.dir})
 * deve ser um volume persistente (no Docker Compose, o volume {@code uploads-data}). No banco fica gravado apenas
 * o caminho público relativo do arquivo, servido pela própria API em {@value #URL_PREFIX}.
 */
@Slf4j
@Service
public class ArquivoService {

    /** Prefixo público sob o qual os arquivos são servidos (ver WebConfig). */
    public static final String URL_PREFIX = "/api/arquivos/";
    public static final String PASTA_PRODUTOS = "produtos";
    public static final String PASTA_PARAMETROS = "parametros";

    private static final long TAMANHO_MAXIMO_IMAGEM = 5L * 1024 * 1024;

    private final Path raiz;

    public ArquivoService(@Value("${app.upload.dir}") String uploadDir) {
        this.raiz = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void criarDiretorios() throws IOException {
        Files.createDirectories(raiz.resolve(PASTA_PRODUTOS));
        Files.createDirectories(raiz.resolve(PASTA_PARAMETROS));
        log.info("Diretório de uploads: {}", raiz);
    }

    public Path getRaiz() {
        return raiz;
    }

    /**
     * Valida e grava uma imagem (JPEG, PNG ou WebP) na pasta informada.
     *
     * @return caminho público relativo do arquivo, por exemplo {@code /api/arquivos/produtos/<uuid>.jpg}
     */
    public String salvarImagem(MultipartFile arquivo, String pasta) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma imagem");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_IMAGEM) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "A imagem deve ter no máximo 5 MB");
        }
        // O nome e o tipo informados pelo cliente não são confiáveis: a extensão é decidida pelo conteúdo do arquivo.
        String extensao = detectarExtensao(arquivo);
        String nomeArquivo = UUID.randomUUID() + "." + extensao;
        Path destino = raiz.resolve(pasta).resolve(nomeArquivo);
        try (InputStream inputStream = arquivo.getInputStream()) {
            Files.copy(inputStream, destino);
        } catch (IOException exception) {
            log.error("Falha ao gravar o arquivo {}", destino, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível salvar a imagem");
        }
        return URL_PREFIX + pasta + "/" + nomeArquivo;
    }

    /**
     * Remove do disco um arquivo previamente salvo por este serviço. Caminhos que não pertencem ao
     * diretório de uploads (URLs externas, valores vazios) são ignorados.
     */
    public void excluir(String caminhoPublico) {
        if (!isArquivoArmazenado(caminhoPublico)) {
            return;
        }
        Path arquivo = raiz.resolve(caminhoPublico.substring(URL_PREFIX.length())).normalize();
        if (!arquivo.startsWith(raiz)) {
            return;
        }
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException exception) {
            log.warn("Não foi possível excluir o arquivo {}", arquivo, exception);
        }
    }

    public boolean isArquivoArmazenado(String caminhoPublico) {
        return caminhoPublico != null && caminhoPublico.startsWith(URL_PREFIX);
    }

    private String detectarExtensao(MultipartFile arquivo) {
        byte[] cabecalho = new byte[12];
        int lidos;
        try (InputStream inputStream = arquivo.getInputStream()) {
            lidos = inputStream.readNBytes(cabecalho, 0, cabecalho.length);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler a imagem enviada");
        }
        if (lidos >= 3 && (cabecalho[0] & 0xFF) == 0xFF && (cabecalho[1] & 0xFF) == 0xD8 && (cabecalho[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (lidos >= 8 && Arrays.equals(Arrays.copyOf(cabecalho, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "png";
        }
        if (lidos >= 12
                && "RIFF".equals(new String(cabecalho, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(cabecalho, 8, 4, StandardCharsets.US_ASCII))) {
            return "webp";
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Formato de imagem inválido. Envie um arquivo JPG, PNG ou WebP");
    }
}
