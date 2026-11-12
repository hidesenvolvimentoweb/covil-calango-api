package br.com.yugiapp.config.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * Converte as exceções lançadas pelos controllers em uma resposta de erro padronizada
 * ({@link ErrorResponseDTO}), com o status HTTP correto e uma mensagem pronta para ser exibida ao usuário.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Erros de negócio: a mensagem informada em quem lançou a exceção é devolvida ao cliente. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? mensagemPadrao(exception.getStatusCode())
                : exception.getReason();
        return build(exception.getStatusCode(), message, request);
    }

    /** {@code Optional.orElseThrow()} sem mensagem: registro inexistente. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(NoSuchElementException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Registro não encontrado", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Violação de integridade em {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "Não foi possível concluir a operação: o registro já existe ou está vinculado a outros dados", request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponseDTO> handleBadRequest(Exception exception, HttpServletRequest request) {
        log.warn("Requisição inválida em {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Os dados enviados são inválidos", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSize(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo enviado excede o tamanho máximo permitido", request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMultipart(MultipartException exception, HttpServletRequest request) {
        log.warn("Upload inválido em {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception exception, HttpServletRequest request) {
        // Exceções do próprio Spring MVC (rota inexistente, método não suportado etc.) já carregam o status correto.
        if (exception instanceof ErrorResponse errorResponse) {
            return build(errorResponse.getStatusCode(), mensagemPadrao(errorResponse.getStatusCode()), request);
        }
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, mensagemPadrao(HttpStatus.INTERNAL_SERVER_ERROR), request);
    }

    private ResponseEntity<ErrorResponseDTO> build(HttpStatusCode statusCode, String message, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return ResponseEntity.status(statusCode).body(ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(statusCode.value())
                .error(status == null ? null : status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build());
    }

    private String mensagemPadrao(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> "Os dados enviados são inválidos";
            case 401 -> "Não autenticado";
            case 403 -> "Você não tem permissão para realizar esta operação";
            case 404 -> "Recurso não encontrado";
            case 405 -> "Operação não suportada";
            case 409 -> "A operação entra em conflito com o estado atual do registro";
            case 413 -> "O arquivo enviado excede o tamanho máximo permitido";
            case 415 -> "Formato de conteúdo não suportado";
            case 503 -> "Serviço temporariamente indisponível. Tente novamente mais tarde";
            default -> statusCode.is4xxClientError()
                    ? "Não foi possível processar a requisição"
                    : "Erro interno do servidor. Tente novamente mais tarde";
        };
    }
}
