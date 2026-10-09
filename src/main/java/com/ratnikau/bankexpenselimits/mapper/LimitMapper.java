package com.ratnikau.bankexpenselimits.mapper;

import com.ratnikau.bankexpenselimits.domain.ExpenseLimit;
import com.ratnikau.bankexpenselimits.dto.LimitResponse;
import com.ratnikau.bankexpenselimits.util.UsdConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = CentralMapperConfig.class, imports = UsdConverter.class)
public interface LimitMapper {

    @Mapping(target = "expenseCategory", source = "category")
    @Mapping(target = "limitSum", source = "amountUsd")
    @Mapping(target = "limitDatetime", source = "setAt")
    @Mapping(target = "limitCurrencyShortname", expression = "java(UsdConverter.USD)")
    LimitResponse toResponse(ExpenseLimit limit);

    List<LimitResponse> toResponses(List<ExpenseLimit> limits);
}