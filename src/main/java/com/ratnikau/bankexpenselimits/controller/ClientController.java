package com.ratnikau.bankexpenselimits.controller;


import com.ratnikau.bankexpenselimits.dto.ExceededTransactionResponse;
import com.ratnikau.bankexpenselimits.dto.LimitRequest;
import com.ratnikau.bankexpenselimits.dto.LimitResponse;
import com.ratnikau.bankexpenselimits.mapper.LimitMapper;
import com.ratnikau.bankexpenselimits.mapper.TransactionMapper;
import com.ratnikau.bankexpenselimits.service.LimitService;
import com.ratnikau.bankexpenselimits.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;


import java.util.List;


@Tag(name = "Клиентский API", description = "Лимиты и транзакции, превысившие лимит")
@ApiResponse(responseCode = "400", description = "Некорректные данные запроса",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервиса",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@RestController
@RequestMapping("/api/v1/client")
@RequiredArgsConstructor
public class ClientController {

    private static final String ACCOUNT_EXAMPLE = "0000000123";

    private final LimitService limitService;
    private final TransactionService transactionService;
    private final LimitMapper limitMapper;
    private final TransactionMapper transactionMapper;

    // Установить новый лимит: дата проставляется автоматически, существующие лимиты не меняются
    @Operation(summary = "Установить новый лимит",
            description = """
                    Дата установления проставляется сервисом автоматически (текущий момент).
                    Задать её вручную, в прошлом или будущем, нельзя. Существующие лимиты не изменяются:
                    новый лимит лишь добавляется и действует для операций, совершённых после его установки.""")
    @ApiResponse(responseCode = "201", description = "Лимит установлен")
    @PostMapping("/limits")
    @ResponseStatus(HttpStatus.CREATED)
    public LimitResponse setLimit(@Valid @RequestBody LimitRequest request) {
        var limit = limitService.setLimit(request.account(), request.expenseCategory(), request.limitSum());
        return limitMapper.toResponse(limit);
    }

    // Получить историю лимитов счёта от новых к старым, включая дефолтный лимит 1000 USD
    @Operation(summary = "Получить все лимиты счёта",
            description = "История лимитов от новых к старым. Лимит по умолчанию (1000 USD) отображается с датой 1970-01-01.")
    @GetMapping("/limits")
    public List<LimitResponse> limits(
            @Parameter(description = "Номер счёта клиента, 10 цифр", example = ACCOUNT_EXAMPLE)
            @RequestParam @Pattern(regexp = "\\d{10}") String account) {
        return limitMapper.toResponses(limitService.findAll(account));
    }

    // Получить транзакции, превысившие лимит, с данными лимита (сумма, дата, валюта USD)
    @Operation(summary = "Транзакции, превысившие лимит",
            description = "Для каждой транзакции возвращается лимит, который был превышен: сумма, дата установления и валюта (USD).")
    @GetMapping("/transactions/exceeded")
    public List<ExceededTransactionResponse> exceeded(
            @Parameter(description = "Номер счёта клиента, 10 цифр", example = ACCOUNT_EXAMPLE)
            @RequestParam @Pattern(regexp = "\\d{10}") String account) {
        return transactionMapper.toExceededResponses(transactionService.findExceeded(account));
    }
}