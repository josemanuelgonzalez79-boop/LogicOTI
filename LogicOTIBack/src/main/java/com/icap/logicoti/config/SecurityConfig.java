package com.icap.logicoti.config;

import com.icap.logicoti.auth.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception -> exception

                        // No se mandó token o el token no es válido.
                        .authenticationEntryPoint(
                                (request, response, authenticationException) ->
                                        response.sendError(
                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                "Se requiere un token válido."
                                        )
                        )

                        // El usuario inició sesión, pero su rol no tiene permiso.
                        .accessDeniedHandler(
                                (request, response, accessDeniedException) ->
                                        response.sendError(
                                                HttpServletResponse.SC_FORBIDDEN,
                                                "El usuario no tiene permiso para realizar esta operación."
                                        )
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Permite que Spring entregue correctamente los errores.
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR,
                                DispatcherType.FORWARD
                        ).permitAll()

                        // Solicitudes previas del navegador.
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        // Inicio de sesión.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()

                        // Conexión inicial de WebSocket.
                        // El JWT se valida después en el CONNECT de STOMP.
                        .requestMatchers(
                                "/ws",
                                "/ws/**"
                        ).permitAll()

                        // Estado básico para comprobar que el servidor está vivo.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // Acceso temporal y firmado a la cámara de una alerta.
                        // No concede una sesión ni acceso al catálogo completo.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/camera-alerts/view"
                        ).permitAll()

                        // Administración de usuarios.
                        .requestMatchers(
                                "/api/users",
                                "/api/users/**"
                        ).hasRole("ADMIN")

                        // Endpoints de diagnóstico.
                        .requestMatchers(
                                "/api/realtime/status",
                                "/api/plc/test"
                        ).hasRole("ADMIN")

                        // Control de luces y minisplits.
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/devices/*/command"
                        ).hasAnyRole(
                                "ADMIN",
                                "OPERATOR"
                        )

                        // Armado y desarmado manual de la alarma.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/security/arm",
                                "/api/security/disarm",
                                "/api/sensor-diagnostics",
                                "/api/sensor-diagnostics/*/cancel"
                        ).hasAnyRole(
                                "ADMIN",
                                "OPERATOR"
                        )

                        // Omitir un sensor debilita la protección y queda sólo para ADMIN.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/security/bypasses"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/security/bypasses/*"
                        ).hasRole("ADMIN")

                        // La configuración de horarios queda solo para ADMIN.
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/security/schedules"
                        ).hasRole("ADMIN")

                        // Cada usuario puede activar o desactivar avisos en sus dispositivos.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/notifications/push/subscriptions",
                                "/api/notifications/push/test"
                        ).hasAnyRole(
                                "ADMIN",
                                "OPERATOR",
                                "MONITORING"
                        )

                        // Todo el personal operativo puede reconocer y documentar alarmas.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/alarms/*/acknowledgement",
                                "/api/alarms/*/comments"
                        ).hasAnyRole(
                                "ADMIN",
                                "OPERATOR",
                                "MONITORING"
                        )

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/notifications/push/subscriptions"
                        ).hasAnyRole(
                                "ADMIN",
                                "OPERATOR",
                                "MONITORING"
                        )

                        // La bitácora de entregas puede contener detalle operativo.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/notifications/push/deliveries"
                        ).hasRole("ADMIN")

                        // Consultas del edificio, oficinas, PLC, etc.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/**"
                        ).hasAnyRole(
                                "ADMIN",
                                "OPERATOR",
                                "MONITORING"
                        )

                        // Cualquier operación no contemplada queda solo para ADMIN.
                        .anyRequest().hasRole("ADMIN")
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
