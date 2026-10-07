package com.ratnikau.bankexpenselimits;

import org.springframework.boot.SpringApplication;

public class TestBankexpenselimitsApplication {

	public static void main(String[] args) {
		SpringApplication.from(BankexpenselimitsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
