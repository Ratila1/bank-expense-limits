package com.ratnikau.bankexpenselimits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class BankexpenselimitsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankexpenselimitsApplication.class, args);
    }
}