package com.hrm.hrmsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EntityScan(basePackages = {"com.hrm.hrmsystem.model", "com.hrm.hrmsystem.entity"})
@ConfigurationPropertiesScan
public class HrmsystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(HrmsystemApplication.class, args);
	}

}
