package com.ratnikau.bankexpenselimits.client;


import java.time.LocalDate;
import java.util.Optional;


public interface ExchangeRateProvider {

    // Получить курс «единиц валюты за 1 USD» на дату. Пусто, если данных нет
    Optional<ProviderRate> fetch(String currency, LocalDate date);
}