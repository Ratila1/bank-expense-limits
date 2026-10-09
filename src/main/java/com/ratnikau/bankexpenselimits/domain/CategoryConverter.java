package com.ratnikau.bankexpenselimits.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CategoryConverter implements AttributeConverter<Category, String> {

    @Override
    public String convertToDatabaseColumn(Category category) {
        return category == null ? null : category.getValue();
    }

    @Override
    public Category convertToEntityAttribute(String value) {
        return value == null ? null : Category.fromValue(value);
    }
}