package com.ratnikau.bankexpenselimits;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ratnikau.bankexpenselimits.domain.ExchangeRate;
import com.ratnikau.bankexpenselimits.domain.RateKind;
import com.ratnikau.bankexpenselimits.repository.ExchangeRateRepository;
import com.ratnikau.bankexpenselimits.repository.ExpenseLimitRepository;
import com.ratnikau.bankexpenselimits.repository.TransactionRepository;
import com.ratnikau.bankexpenselimits.support.MutableClock;
import com.ratnikau.bankexpenselimits.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Общая основа интеграционных тестов: настоящий Postgres (Testcontainers),
 * управляемые часы и WireMock вместо Twelve Data. Без заглушки WireMock отвечает 404,
 * то есть «провайдер не дал курс».
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfig.class})
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void rateProviderProperties(DynamicPropertyRegistry registry) {
        registry.add("rates.provider.base-url", WIRE_MOCK::baseUrl);
    }

    @Autowired
    protected TransactionRepository transactionRepository;
    @Autowired
    protected ExpenseLimitRepository limitRepository;
    @Autowired
    protected ExchangeRateRepository rateRepository;
    @Autowired
    protected MutableClock clock;

    @BeforeEach
    protected void resetState() {
        // Порядок важен: транзакции ссылаются на лимиты
        transactionRepository.deleteAllInBatch();
        limitRepository.deleteAllInBatch();
        rateRepository.deleteAllInBatch();
        WIRE_MOCK.resetAll();
        clock.set("2022-01-01T00:00:00Z");
    }

    protected void givenRateInDb(String currency, String date, String rate) {
        var entity = new ExchangeRate();
        entity.setCurrency(currency);
        entity.setRateDate(LocalDate.parse(date));
        entity.setRate(new BigDecimal(rate));
        entity.setRateKind(RateKind.CLOSE);
        entity.setFetchedAt(Instant.parse("2022-01-01T00:00:00Z"));
        rateRepository.save(entity);
    }

    /** Провайдер отвечает одной свечой: {@code candleDate} с ценой закрытия {@code close}. */
    protected void givenProviderReturns(String currency, String candleDate, String close) {
        String body = """
                {"meta":{"symbol":"USD/%s","interval":"1day"},
                 "values":[{"datetime":"%s","open":"%s","high":"%s","low":"%s","close":"%s"}],
                 "status":"ok"}""".formatted(currency, candleDate, close, close, close, close);
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/time_series"))
                .withQueryParam("symbol", equalTo("USD/" + currency))
                .willReturn(okJson(body)));
    }

    protected void givenProviderIsDown() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/time_series")).willReturn(serverError()));
    }

    protected void verifyProviderCalls(int expected) {
        WIRE_MOCK.verify(expected, getRequestedFor(urlPathEqualTo("/time_series")));
    }
}