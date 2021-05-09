package com.pk.ws.wsserver;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.ServerResponse.accepted;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Sinks;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class WsServerApplication {

	private final EventSubject subject;

	public static void main(String[] args) {
		SpringApplication.run(WsServerApplication.class, args);		
	}

	@Bean
	public WebSocketHandler eventsHandler() {
		return session -> {
			Flux<WebSocketMessage> publisher = Flux.create((FluxSink<WebSocketMessage> sink) -> subject.connectSession(sink, session))
			.doOnNext(m -> log.info("Push message {} to client", m.getPayloadAsText()))
			.doFinally(done -> subject.disconnectSession(session.getId()));
			return session.send(publisher);
		};
	}

	@Bean 
	public HandlerMapping handlerMapping() {
		return new SimpleUrlHandlerMapping(
			Map.of("/ws/events", eventsHandler()),
			10
		);
	}

	@Bean
	public RouterFunction<ServerResponse> routeConfig() {
	  return route(POST("/events"), req -> req.bodyToMono(Event.class).doOnNext(subject::next).then(accepted().build()));
	}
}

@Component
@Slf4j
class EventSubject {
	private Sinks.Many<Event> eventSink = Sinks.many().multicast().onBackpressureBuffer();
	private Map<String, Disposable> disposables = new HashMap<>(); 


	public void next(Event event) {
		log.info("Emitting to eventSink {}", event);
		this.eventSink.tryEmitNext(event);
	}

	public void connectSession(FluxSink<WebSocketMessage> clientSink, WebSocketSession session) {
		disposables.put(
			session.getId(), 
			eventSink.asFlux()
				.map(EventSubject::convertObjToJson)
				.map(session::textMessage)	
				.subscribe(clientSink::next)
		);
	}

	public void disconnectSession(String sessionId) {
		disposables.get(sessionId).dispose();
		disposables.remove(sessionId);
	}

	private static String convertObjToJson(Object obj) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return ""; // TODO
		}
	}

}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Event implements Serializable {
	// private Date eventDate;
	private String message;
}