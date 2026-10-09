package com.ratnikau.bankexpenselimits.e2e;

import com.ratnikau.bankexpenselimits.AbstractIntegrationTest;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.dto.ExceededTransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static com.ratnikau.bankexpenselimits.support.TestData.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpenseLimitsE2ETest extends AbstractIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private RestClient api;

    @BeforeEach
    void setUpClient() {
        api = RestClient.create("http://localhost:" + port);
    }

    // Проверяет расчёт лимитов и поиск превышенных транзакций
    @Test
    void case1_returnsTransactionsOfThirdAndThirteenthOfJanuaryWithTheirLimits() {
        givenProviderReturns("KZT", "2021-12-31", "480");

        clock.set("2022-01-01T00:00:00Z");
        setLimit("1000.00");
        spendKzt("240000.00", "2022-01-02T10:00:00+06:00");   // 500 USD
        spendKzt("288000.00", "2022-01-03T10:00:00+06:00");   // 600 USD, превышение
        clock.set("2022-01-10T00:00:00Z");
        setLimit("2000.00");
        spendKzt("48000.00", "2022-01-11T10:00:00+06:00");    // 100 USD
        spendKzt("336000.00", "2022-01-12T10:00:00+06:00");   // 700 USD
        spendKzt("48000.00", "2022-01-13T10:00:00+06:00");    // 100 USD, остаток ровно 0
        spendKzt("48000.00", "2022-01-13T11:00:00+06:00");    // 100 USD, превышение

        List<ExceededTransactionResponse> exceeded = api.get()
                .uri("/api/v1/client/transactions/exceeded?account={account}", ACCOUNT)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExceededTransactionResponse>>() {
                });

        assertThat(exceeded).hasSize(2);

        ExceededTransactionResponse third = exceeded.get(0);
        assertThat(third.datetime().toInstant()).isEqualTo(Instant.parse("2022-01-03T04:00:00Z"));
        assertThat(third.accountFrom()).isEqualTo(ACCOUNT);
        assertThat(third.currencyShortname()).isEqualTo("KZT");
        assertThat(third.expenseCategory()).isEqualTo(Category.PRODUCT);
        assertThat(third.sum()).isEqualByComparingTo("288000");
        assertThat(third.limitSum()).isEqualByComparingTo("1000");
        assertThat(third.limitDatetime().toInstant()).isEqualTo(Instant.parse("2022-01-01T00:00:00Z"));
        assertThat(third.limitCurrencyShortname()).isEqualTo("USD");

        ExceededTransactionResponse thirteenth = exceeded.get(1);
        assertThat(thirteenth.datetime().toInstant()).isEqualTo(Instant.parse("2022-01-13T05:00:00Z"));
        assertThat(thirteenth.sum()).isEqualByComparingTo("48000");
        assertThat(thirteenth.limitSum()).isEqualByComparingTo("2000");
        assertThat(thirteenth.limitDatetime().toInstant()).isEqualTo(Instant.parse("2022-01-10T00:00:00Z"));
        assertThat(thirteenth.limitCurrencyShortname()).isEqualTo("USD");
    }

    // Проверяет успешное создание транзакции и расчёт суммы в USD
    @Test
    void postTransaction_returnsCreatedWithCalculatedUsdAndFlag() {
        givenProviderReturns("KZT", "2021-12-31", "480");

        ResponseEntity<String> response = api.post()
                .uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(transactionJson(ACCOUNT, "KZT", "240000.00", "product",
                        "2022-01-03T10:00:00+06:00"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).contains("\"status\":\"PROCESSED\"")
                .contains("\"sum_usd\":500.00")
                .contains("\"limit_exceeded\":false");
    }

    // Проверяет получение лимита по умолчанию после первой транзакции
    @Test
    void getLimits_returnsDefaultLimit_afterFirstTransaction() {
        givenProviderReturns("KZT", "2021-12-31", "480");
        spendKzt("48000.00", "2022-01-03T10:00:00+06:00");

        String body = api.get().uri("/api/v1/client/limits?account={account}", ACCOUNT)
                .retrieve().body(String.class);

        assertThat(body).contains("\"limit_sum\":1000.00").contains("\"limit_currency_shortname\":\"USD\"");
    }

    // Проверяет ошибку при некорректном номере счёта
    @Test
    void postTransaction_returnsProblemDetail_whenAccountIsInvalid() {
        String json = transactionJson("123", "USD", "10.00", "product", "2022-01-03T10:00:00Z");

        assertThatThrownBy(() -> postTransaction(json))
                .isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, e -> {
                    assertThat(e.getResponseBodyAsString()).contains("accountFrom");
                    assertThat(e.getResponseHeaders().getContentType().toString()).contains("problem+json");
                });
    }

    // Проверяет ошибку при неизвестной категории расходов
    @Test
    void postTransaction_returnsBadRequest_whenCategoryIsUnknown() {
        String json = transactionJson(ACCOUNT, "USD", "10.00", "food", "2022-01-03T10:00:00Z");

        assertThatThrownBy(() -> postTransaction(json))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    // Проверяет ошибку при неизвестной валюте
    @Test
    void postTransaction_returnsBadRequest_whenCurrencyIsUnknown() {
        String json = transactionJson(ACCOUNT, "ZZZ", "10.00", "product", "2022-01-03T10:00:00Z");

        assertThatThrownBy(() -> postTransaction(json))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    // Проверяет ошибку при отрицательной сумме транзакции
    @Test
    void postTransaction_returnsBadRequest_whenSumIsNegative() {
        String json = transactionJson(ACCOUNT, "USD", "-5.00", "product", "2022-01-03T10:00:00Z");

        assertThatThrownBy(() -> postTransaction(json))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    // Проверяет ошибку при некорректном номере счёта
    @Test
    void getExceeded_returnsBadRequest_whenAccountIsInvalid() {
        assertThatThrownBy(() -> api.get().uri("/api/v1/client/transactions/exceeded?account=123")
                .retrieve().body(String.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    // Проверяет невозможность изменения лимита через отсутствующий endpoint
    @Test
    void existingLimitCannotBeChanged_becauseThereIsNoUpdateEndpoint() {
        assertThatThrownBy(() -> api.put().uri("/api/v1/client/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve().body(String.class))
                .isInstanceOf(HttpClientErrorException.MethodNotAllowed.class);
    }

    // ---------- Помощники ----------

    private void setLimit(String limitSum) {
        String json = """
                {"account":"%s","expense_category":"product","limit_sum":%s}""".formatted(ACCOUNT, limitSum);
        api.post().uri("/api/v1/client/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve().toBodilessEntity();
    }

    private void spendKzt(String sum, String datetime) {
        postTransaction(transactionJson(ACCOUNT, "KZT", sum, "product", datetime));
    }

    private void postTransaction(String json) {
        api.post().uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve().toBodilessEntity();
    }

    private static String transactionJson(String accountFrom, String currency, String sum,
                                          String category, String datetime) {
        return """
                {"account_from":"%s","account_to":"9999999999","currency_shortname":"%s",
                 "sum":%s,"expense_category":"%s","datetime":"%s"}"""
                .formatted(accountFrom, currency, sum, category, datetime);
    }
}