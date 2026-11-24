package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** Período exibido no calendário: traz os eventos que acontecem, ao menos em parte, entre {@code inicio} e {@code fim}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoFilterRequestDTO {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime inicio;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fim;
}
