package com.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiAgentApplication {

	public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ApiAgentApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
	}

}
