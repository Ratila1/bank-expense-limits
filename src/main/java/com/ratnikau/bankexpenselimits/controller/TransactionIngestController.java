package com.ratnikau.bankexpenselimits.controller;


import com.ratnikau.bankexpenselimits.dto.TransactionRequest;
import com.ratnikau.bankexpenselimits.dto.TransactionResponse;
import com.ratnikau.bankexpenselimits.mapper.TransactionMapper;
import com.ratnikau.bankexpenselimits.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Приём транзакций", description = "API для интеграции с банковскими сервисами")
@ApiResponse(responseCode = "400", description = "Некорректные данные запроса",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервиса",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionIngestController {

    private final TransactionService service;
    private final TransactionMapper mapper;

    // Принять расходную операцию: конвертация в USD, проверка лимита, статус PENDING_RATE если курса нет
    @Operation(summary = "Принять расходную операцию",
            description = """
                    Переводит сумму в USD по курсу закрытия на день операции (в выходной используется предыдущее закрытие)
                    и выставляет флаг limit_exceeded по лимиту, действовавшему на момент операции.
                    Если курс получить не удалось, операция сохраняется со статусом PENDING_RATE
                    и досчитывается автоматически.""")
    @ApiResponse(responseCode = "201", description = "Операция принята")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse accept(@Valid @RequestBody TransactionRequest request) {
        return mapper.toResponse(service.accept(request));
    }
}