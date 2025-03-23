package com.wkaiser.riotapimcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RiotApiMcpServerApplication.class)
public class RiotApiMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RiotApiMcpServerApplication.class, args);
	}

}
