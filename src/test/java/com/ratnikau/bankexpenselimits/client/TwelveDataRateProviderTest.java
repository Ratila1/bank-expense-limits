package com.ratnikau.bankexpenselimits.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.ratnikau.bankexpenselimits.config.RateProviderProperties;
import com.ratnikau.bankexpenselimits.domain.RateKind;
import com.ratnikau.bankexpenselimits.exception.ExchangeRateProviderException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwelveDataRateProviderTest {

    private static final LocalDate MONDAY = LocalDate.of(2022, 1, 3);
    private static final LocalDate SUNDAY = LocalDate.of(2022, 1, 2);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private TwelveDataRateProvider provider;

    @BeforeEach
    void setUp() {
        provider = providerWith("test-key", Duration.ofSeconds(2));
    }

    // Проверяет получение курса за запрошенный день
    @Test
    void fetch_returnsClose_whenCandleExistsForRequestedDay() {
        stubSuccess(candles("2022-01-03=480.50"));

        Optional<ProviderRate> rate = provider.fetch("KZT", MONDAY);

        assertThat(rate).hasValueSatisfying(r -> {
            assertThat(r.unitsPerUsd()).isEqualByComparingTo("480.50");
            assertThat(r.kind()).isEqualTo(RateKind.CLOSE);
        });
    }

    // Проверяет получение курса за последний рабочий день
    @Test
    void fetch_returnsPreviousClose_whenRequestedDayIsWeekend() {
        stubSuccess(candles("2021-12-31=479.90", "2021-12-30=479.00"));

        Optional<ProviderRate> rate = provider.fetch("KZT", SUNDAY);

        assertThat(rate).hasValueSatisfying(r -> {
            assertThat(r.unitsPerUsd()).isEqualByComparingTo("479.90");
            assertThat(r.kind()).isEqualTo(RateKind.PREVIOUS_CLOSE);
        });
    }

    // Проверяет игнорирование свечей после запрошенной даты
    @Test
    void fetch_ignoresCandlesAfterRequestedDay() {
        stubSuccess(candles("2022-01-04=481.00", "2022-01-03=480.00"));

        Optional<ProviderRate> rate = provider.fetch("KZT", MONDAY);

        assertThat(rate).hasValueSatisfying(r -> assertThat(r.unitsPerUsd()).isEqualByComparingTo("480.00"));
    }

    // Проверяет пустой результат при отсутствии свечей
    @Test
    void fetch_returnsEmpty_whenProviderHasNoCandles() {
        stubSuccess(candles());

        Optional<ProviderRate> rate = provider.fetch("KZT", MONDAY);

        assertThat(rate).isEmpty();
    }

    // Проверяет параметры запроса к провайдеру
    @Test
    void fetch_sendsSymbolIntervalDateWindowAndApiKey() {
        stubSuccess(candles("2022-01-03=480.50"));

        provider.fetch("KZT", MONDAY);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/time_series"))
                .withQueryParam("symbol", equalTo("USD/KZT"))
                .withQueryParam("interval", equalTo("1day"))
                .withQueryParam("start_date", equalTo("2021-12-24"))
                .withQueryParam("end_date", equalTo("2022-01-04"))
                .withQueryParam("apikey", equalTo("test-key")));
    }

    // Проверяет отсутствие повторных запросов при ошибке в теле ответа
    @Test
    void fetch_doesNotRetry_whenProviderReportsErrorInBody() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).willReturn(
                okJson("{\"code\":400,\"message\":\"symbol is invalid\",\"status\":\"error\"}")));

        assertThatThrownBy(() -> provider.fetch("ZZZ", MONDAY))
                .isInstanceOfSatisfying(ExchangeRateProviderException.class,
                        e -> assertThat(e.isRetryable()).isFalse());
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/time_series")));
    }

    // Проверяет отсутствие повторных запросов при ошибке авторизации
    @Test
    void fetch_doesNotRetry_onUnauthorized() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> provider.fetch("KZT", MONDAY))
                .isInstanceOfSatisfying(ExchangeRateProviderException.class,
                        e -> assertThat(e.isRetryable()).isFalse());
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/time_series")));
    }

    // Проверяет повторные попытки после временных ошибок
    @Test
    void fetch_retriesTemporaryFailures_andSucceedsOnThirdAttempt() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serverError()).willSetStateTo("second"));
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).inScenario("flaky")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("third"));
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).inScenario("flaky")
                .whenScenarioStateIs("third")
                .willReturn(okJson(candles("2022-01-03=480.50"))));

        Optional<ProviderRate> rate = provider.fetch("KZT", MONDAY);

        assertThat(rate).isPresent();
        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/time_series")));
    }

    // Проверяет завершение после максимального количества попыток
    @Test
    void fetch_givesUpAfterMaxAttempts_whenRateLimitIsAlwaysHit() {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> provider.fetch("KZT", MONDAY))
                .isInstanceOfSatisfying(ExchangeRateProviderException.class,
                        e -> assertThat(e.isRetryable()).isTrue());
        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/time_series")));
    }

    // Проверяет обработку таймаута чтения ответа
    @Test
    void fetch_failsWithRetryableError_whenProviderIsSlowerThanReadTimeout() {
        TwelveDataRateProvider impatient = providerWith("test-key", Duration.ofMillis(200));
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).willReturn(
                okJson(candles("2022-01-03=480.50")).withFixedDelay(1500)));

        assertThatThrownBy(() -> impatient.fetch("KZT", MONDAY))
                .isInstanceOfSatisfying(ExchangeRateProviderException.class,
                        e -> assertThat(e.isRetryable()).isTrue());
    }

    // Проверяет отсутствие запроса при пустом API-ключе
    @Test
    void fetch_failsWithoutAnyRequest_whenApiKeyIsMissing() {
        TwelveDataRateProvider withoutKey = providerWith("", Duration.ofSeconds(2));

        assertThatThrownBy(() -> withoutKey.fetch("KZT", MONDAY))
                .isInstanceOfSatisfying(ExchangeRateProviderException.class,
                        e -> assertThat(e.isRetryable()).isFalse());
        wireMock.verify(0, anyRequestedFor(anyUrl()));
    }

    // Проверяет обработку некорректной цены закрытия
    @Test
    void fetch_rejectsMalformedClosePrice() {
        stubSuccess(candles("2022-01-03=abc"));

        assertThatThrownBy(() -> provider.fetch("KZT", MONDAY))
                .isInstanceOfSatisfying(ExchangeRateProviderException.class,
                        e -> assertThat(e.isRetryable()).isFalse());
    }

    private void stubSuccess(String body) {
        wireMock.stubFor(get(urlPathEqualTo("/time_series")).willReturn(okJson(body)));
    }

    private static String candles(String... dateAndClose) {
        String values = Arrays.stream(dateAndClose)
                .map(item -> item.split("="))
                .map(p -> "{\"datetime\":\"%s\",\"open\":\"1\",\"high\":\"1\",\"low\":\"1\",\"close\":\"%s\"}"
                        .formatted(p[0], p[1]))
                .collect(Collectors.joining(","));
        return "{\"meta\":{\"symbol\":\"USD/KZT\"},\"values\":[" + values + "],\"status\":\"ok\"}";
    }

    private TwelveDataRateProvider providerWith(String apiKey, Duration readTimeout) {
        var props = new RateProviderProperties(wireMock.getRuntimeInfo().getHttpBaseUrl(), apiKey,
                Duration.ofSeconds(1), readTimeout, 3, Duration.ofMillis(1));
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        var restClient = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
        return new TwelveDataRateProvider(restClient, props, new SimpleMeterRegistry());
    }
}