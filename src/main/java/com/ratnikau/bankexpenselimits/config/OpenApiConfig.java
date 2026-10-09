package com.ratnikau.bankexpenselimits.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Bank Expense Limits API")
                .version("v1")
                .description("""
                        Приём расходных операций и контроль месячных лимитов в USD.
                        Лимиты ведутся раздельно по счёту и категории расходов (product / service).
                        Все ошибки возвращаются в формате ProblemDetail (RFC 9457)."""));
    }
}