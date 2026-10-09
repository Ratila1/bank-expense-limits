package com.ratnikau.bankexpenselimits.client;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


import java.util.List;


// Модель ответа от Twelve Data: статус, код, сообщение и список значений (datetime, close)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwelveDataTimeSeries(String status, Integer code, String message, List<Value> values) {

    // Отдельное значение временной серии: дата-время и цена закрытия
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(String datetime, String close) {
    }
}