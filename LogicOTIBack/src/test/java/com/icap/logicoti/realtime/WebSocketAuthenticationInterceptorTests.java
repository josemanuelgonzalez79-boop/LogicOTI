package com.icap.logicoti.realtime;

import com.icap.logicoti.auth.JwtService;
import com.icap.logicoti.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WebSocketAuthenticationInterceptorTests {

    private WebSocketAuthenticationInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthenticationInterceptor(
                mock(JwtService.class),
                mock(AppUserRepository.class)
        );
        channel = mock(MessageChannel.class);
    }

    @Test
    void permitsAlarmAttentionSubscriptions() {
        Message<byte[]> message = subscription(
                "/topic/alerts/attention"
        );

        assertThat(interceptor.preSend(message, channel))
                .isSameAs(message);
    }

    @Test
    void continuesRejectingUnknownTopics() {
        Message<byte[]> message = subscription(
                "/topic/alerts/not-allowed"
        );

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("La suscripción solicitada no está permitida.");
    }

    private Message<byte[]> subscription(String destination) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(
                new UsernamePasswordAuthenticationToken(
                        "admin",
                        null,
                        List.of()
                )
        );

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}
