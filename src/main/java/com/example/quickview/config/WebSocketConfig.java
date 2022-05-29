package com.example.quickview.config;

import com.example.quickview.exception.RoomIsFullException;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final String CALL_ID_ATTRIBUTE = "callId";
    private static final Map<String, Integer> ROOM_CAPACITIES = new ConcurrentHashMap<>();

    @SneakyThrows
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new CallHandler(), "/chat/*")
                .addInterceptors(clientInterceptor())
                .setAllowedOrigins("*");
    }

    @Bean
    public HandshakeInterceptor clientInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler webSocketHandler, Map<String, Object> attributes) throws RoomIsFullException {
                String path = request.getURI().getPath();
                String callId = path.substring(path.lastIndexOf('/') + 1);
                synchronized (ROOM_CAPACITIES) {
                    if (ROOM_CAPACITIES.containsKey(callId)) {
                        if (ROOM_CAPACITIES.get(callId) == 2) {
                            throw new RoomIsFullException("Current room is full.");
                        }
                        int capacity = ROOM_CAPACITIES.get(callId);
                        ROOM_CAPACITIES.put(callId, capacity + 1);
                    } else {
                        ROOM_CAPACITIES.put(callId, 1);
                    }
                }
                attributes.put(CALL_ID_ATTRIBUTE, callId);
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) { }
        };
    }

    @Component
    private static class CallHandler extends TextWebSocketHandler {

        private final List<WebSocketSession> SESSIONS = new CopyOnWriteArrayList<>();

        @Override
        public void handleTextMessage(WebSocketSession session, TextMessage message)
                throws IOException {
            getDestination(session).sendMessage(message);
        }

        private WebSocketSession getDestination(WebSocketSession session) {
            String commonCallId = (String) session.getAttributes().get(CALL_ID_ATTRIBUTE);
            return SESSIONS.stream().filter(x -> x.getAttributes().get(CALL_ID_ATTRIBUTE).equals(commonCallId) && !x.getId().equals(session.getId())).findFirst().orElseThrow();
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            SESSIONS.add(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            String callId = (String) session.getAttributes().get(CALL_ID_ATTRIBUTE);
            synchronized (ROOM_CAPACITIES) {
                ROOM_CAPACITIES.put(callId, ROOM_CAPACITIES.get(callId) - 1);
            }
            SESSIONS.remove(session);
        }
    }

}
