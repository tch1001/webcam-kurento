package com.example.demo;

import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@SpringBootApplication
@EnableWebSocket
public class DemoApplication implements WebSocketConfigurer {

	public static ConfigurableApplicationContext context;

	public static void main(String[] args) throws Exception{
		context = SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public Handler handler(){
		return new Handler();
	}

	@Bean
	public AdminHandler adminHandler(){
		return new AdminHandler();
	}

	@Bean
	public KurentoClient kurentoClient(){
		return KurentoClient.create();
	}

	@Bean
	public ServletServerContainerFactoryBean createServletServerContainerFactoryBean(){
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
		container.setMaxTextMessageBufferSize(32768);
		return container;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
		registry.addHandler(handler(), "/upstream");
		registry.addHandler(adminHandler(), "/downstream");
	}

}
