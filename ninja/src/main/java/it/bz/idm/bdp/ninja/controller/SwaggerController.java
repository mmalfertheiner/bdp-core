package it.bz.idm.bdp.ninja.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import springfox.documentation.annotations.ApiIgnore;
import springfox.documentation.swagger.web.UiConfiguration;
import springfox.documentation.swagger.web.UiConfigurationBuilder;

@ApiIgnore
@Controller
public class SwaggerController {

	@RequestMapping("/")
	public String swaggerUi() {
		return "redirect:/swagger-ui.html";
	}

	@Bean
	UiConfiguration uiConfig() {
		return UiConfigurationBuilder
				.builder()
				.validatorUrl(null)
				.build();
	}
}
