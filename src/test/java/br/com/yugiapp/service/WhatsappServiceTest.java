package br.com.yugiapp.service;

import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.model.Produto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class WhatsappServiceTest {

    private static final String CELULAR = "5583999999999";

    private Pedido pedido(StatusPedidoEnum status) {
        return Pedido.builder()
                .status(status)
                .quantidade(2L)
                .produto(Produto.builder().nome("X-Calango").build())
                .comanda(Comanda.builder().nomeCliente("Ana").celular(CELULAR).notificarWhatsapp(true).build())
                .build();
    }

    private WhatsappService service() {
        WhatsappService service = spy(new WhatsappService("http://bot.local", "token", new ObjectMapper()));
        doNothing().when(service).enviarMensagem(anyString(), anyString());
        return service;
    }

    @ParameterizedTest
    @EnumSource(value = StatusPedidoEnum.class, names = "PRONTO")
    void notificaQuandoOPedidoEhConcluido(StatusPedidoEnum status) {
        WhatsappService service = service();
        service.notificarAlteracaoStatusPedido(pedido(status));
        verify(service).enviarMensagem(eq(CELULAR), contains("está pronto para retirada"));
    }

    @ParameterizedTest
    @EnumSource(value = StatusPedidoEnum.class, names = "PRONTO", mode = EnumSource.Mode.EXCLUDE)
    void naoNotificaAsDemaisMovimentacoes(StatusPedidoEnum status) {
        WhatsappService service = service();
        service.notificarAlteracaoStatusPedido(pedido(status));
        verify(service, never()).enviarMensagem(anyString(), anyString());
    }
}
