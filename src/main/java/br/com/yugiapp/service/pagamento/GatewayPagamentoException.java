package br.com.yugiapp.service.pagamento;

/** Falha na comunicação com o provedor de pagamentos ou recusa da operação por ele. */
public class GatewayPagamentoException extends RuntimeException {

    /** Status HTTP devolvido pelo provedor; 0 quando não houve resposta (timeout, rede). */
    private final int statusHttp;

    public GatewayPagamentoException(String message, int statusHttp, Throwable cause) {
        super(message, cause);
        this.statusHttp = statusHttp;
    }

    public int getStatusHttp() {
        return statusHttp;
    }

    /** O provedor recusou a operação (4xx): repetir a mesma chamada não resolve. */
    public boolean isRecusa() {
        return statusHttp >= 400 && statusHttp < 500;
    }
}
