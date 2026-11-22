package br.com.yugiapp.config.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void naoEscreveCorpoDeErroNosTransportesDoSockJs() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/covil-ws/562/ahk4mxbi/xhr_streaming");

        assertThat(handler.handleGeneric(new IllegalStateException("conexão encerrada"), request)).isNull();
    }

    @Test
    void demaisRotasContinuamRecebendoErro500() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/comanda");

        ResponseEntity<ErrorResponseDTO> resposta = handler.handleGeneric(new IllegalStateException("falha"), request);

        assertThat(resposta.getStatusCode().value()).isEqualTo(500);
    }
}
