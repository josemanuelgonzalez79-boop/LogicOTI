package com.icap.logicoti.realtime;

import com.icap.logicoti.auth.JwtService;
import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthenticationInterceptor
        implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SMOKE_ALERT_TOPIC =
            "/topic/alerts/smoke";
    private static final String ALARM_ATTENTION_TOPIC =
            "/topic/alerts/attention";
    private static final String SECURITY_STATUS_TOPIC =
            "/topic/security/status";
    private static final String SECURITY_WARNING_TOPIC =
            "/topic/security/warnings";
    private static final String SECURITY_MOTION_ALERT_TOPIC =
            "/topic/security/motion-alerts";
    private static final String AUTOMATIC_LIGHTING_TOPIC =
            "/topic/security/automatic-lighting";
    private static final String AREA_INACTIVITY_TOPIC =
            "/topic/security/inactivity";

    private static final Pattern AREA_TOPIC_PATTERN = Pattern.compile(
            "^/topic/areas/[A-Z0-9_]+/state$"
    );

    private static final Pattern SENSOR_DIAGNOSTIC_TOPIC_PATTERN =
            Pattern.compile(
                    "^/topic/diagnostics/sensors/[0-9]+$"
            );

    private final JwtService jwtService;
    private final AppUserRepository userRepository;

    public WebSocketAuthenticationInterceptor(
            JwtService jwtService,
            AppUserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw new AccessDeniedException(
                    "El cliente no puede enviar mensajes por WebSocket."
            );
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorizationHeader =
                accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null
                || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException(
                    "Falta el token de acceso del WebSocket."
            );
        }

        String token = authorizationHeader
                .substring(BEARER_PREFIX.length())
                .trim();

        if (token.isBlank() || !jwtService.isTokenValid(token)) {
            throw new BadCredentialsException(
                    "El token del WebSocket no es válido o ya venció."
            );
        }

        String username = jwtService.extractUsername(token);

        AppUser user = userRepository
                .findByUsernameIgnoreCase(username)
                .filter(AppUser::isActive)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "El usuario no existe o está inactivo."
                        )
                );

        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole()
                );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        null,
                        List.of(authority)
                );

        accessor.setUser(authentication);
    }

    private void authorizeSubscription(
            StompHeaderAccessor accessor
    ) {
        if (accessor.getUser() == null) {
            throw new BadCredentialsException(
                    "La conexión WebSocket no está autenticada."
            );
        }

        String destination = accessor.getDestination();


                boolean allowedAreaTopic =
                        destination != null
                        && AREA_TOPIC_PATTERN
                                .matcher(destination)
                                .matches();

                boolean allowedSmokeAlertTopic =
                        SMOKE_ALERT_TOPIC.equals(destination);

                boolean allowedAlarmAttentionTopic =
                        ALARM_ATTENTION_TOPIC.equals(destination);

                boolean allowedSecurityStatusTopic =
                        SECURITY_STATUS_TOPIC.equals(destination);

                boolean allowedSecurityWarningTopic =
                        SECURITY_WARNING_TOPIC.equals(destination);

                boolean allowedSecurityMotionAlertTopic =
                        SECURITY_MOTION_ALERT_TOPIC.equals(destination);

                boolean allowedAutomaticLightingTopic =
                        AUTOMATIC_LIGHTING_TOPIC.equals(destination);

                boolean allowedAreaInactivityTopic =
                        AREA_INACTIVITY_TOPIC.equals(destination);

                boolean allowedSensorDiagnosticTopic =
                        destination != null
                        && SENSOR_DIAGNOSTIC_TOPIC_PATTERN
                                .matcher(destination)
                                .matches();

                if (!allowedAreaTopic
                        && !allowedSmokeAlertTopic
                        && !allowedAlarmAttentionTopic
                        && !allowedSecurityStatusTopic
                        && !allowedSecurityWarningTopic
                        && !allowedSecurityMotionAlertTopic
                        && !allowedAutomaticLightingTopic
                        && !allowedAreaInactivityTopic
                        && !allowedSensorDiagnosticTopic) {
                throw new AccessDeniedException(
                        "La suscripción solicitada no está permitida."
                );
            }
    }
}
