package com.ratnikau.bankexpenselimits.mapper;

import com.ratnikau.bankexpenselimits.domain.Transaction;
import com.ratnikau.bankexpenselimits.dto.ExceededTransactionResponse;
import com.ratnikau.bankexpenselimits.dto.TransactionRequest;
import com.ratnikau.bankexpenselimits.dto.TransactionResponse;
import com.ratnikau.bankexpenselimits.util.UsdConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = CentralMapperConfig.class, imports = UsdConverter.class)
public interface TransactionMapper {

    @Mapping(target = "currency", source = "currencyShortname")
    @Mapping(target = "amount", source = "sum")
    @Mapping(target = "occurredAt", source = "datetime")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sumUsd", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "limitExceeded", ignore = true)
    @Mapping(target = "limit", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Transaction toEntity(TransactionRequest request);

    @Mapping(target = "currencyShortname", source = "currency")
    @Mapping(target = "sum", source = "amount")
    @Mapping(target = "datetime", source = "occurredAt")
    TransactionResponse toResponse(Transaction transaction);

    @Mapping(target = "currencyShortname", source = "currency")
    @Mapping(target = "sum", source = "amount")
    @Mapping(target = "datetime", source = "occurredAt")
    @Mapping(target = "limitSum", source = "limit.amountUsd")
    @Mapping(target = "limitDatetime", source = "limit.setAt")
    @Mapping(target = "limitCurrencyShortname", expression = "java(UsdConverter.USD)")
    ExceededTransactionResponse toExceededResponse(Transaction transaction);

    List<ExceededTransactionResponse> toExceededResponses(List<Transaction> transactions);
}