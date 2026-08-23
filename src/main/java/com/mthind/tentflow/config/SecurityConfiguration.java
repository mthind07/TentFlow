package com.mthind.tentflow.config;

import com.mthind.tentflow.audit.AuditService;
import com.mthind.tentflow.security.DatabaseUserDetailsService;
import com.mthind.tentflow.security.JwtAudienceValidator;
import com.mthind.tentflow.security.RestAccessDeniedHandler;
import com.mthind.tentflow.security.RestAuthenticationEntryPoint;
import com.mthind.tentflow.security.Utf8BoundedPasswordEncoder;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.annotation.Order;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Map;
import java.time.Clock;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(TentFlowSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                //match Actuator endpoints by endpoint identity instead of assuming that the /actuator base path never changes
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .authorizeHttpRequests(authorize -> authorize
                        //Port 8081 is internal-only in the supported Docker and Render deployments. Do not publish that port
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/prometheus"
                        ).permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()
                        .requestMatchers(
                                "/api/admin/**",
                                "/api/admin/audit-events/**"
                        ).hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter()
                        ))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain browserSecurityFilterChain(
            HttpSecurity http,
            AuditService auditService
    ) throws Exception {
        http
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR,
                                DispatcherType.FORWARD
                        ).permitAll()
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/error",
                                "/access-denied",
                                "/favicon.ico",
                                "/favicon.svg",
                                "/robots.txt",
                                "/css/**",
                                "/images/**",
                                "/livez",
                                "/readyz",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .requestMatchers("/customer/**")
                        .hasRole("CUSTOMER")
                        .requestMatchers("/staff/**")
                        .hasAnyRole("STAFF", "ADMIN")
                        .anyRequest().denyAll()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        .usernameParameter("email")
                        .successHandler((request, response, authentication) -> {
                            auditService.success(
                                    "LOGIN",
                                    "AUTHENTICATION",
                                    null,
                                    null
                            );
                            response.sendRedirect(
                                    request.getContextPath() + "/"
                            );
                        })
                        .failureHandler((request, response, exception) -> {
                            auditService.loginFailure();
                            response.sendRedirect(
                                    request.getContextPath() + "/login?error"
                            );
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedPage("/access-denied")
                );

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return authenticationConverter;
    }

    @Bean
    PasswordEncoder passwordEncoder(TentFlowSecurityProperties properties) {
        PasswordEncoder delegate = new DelegatingPasswordEncoder(
                "bcrypt",
                Map.of(
                        "bcrypt",
                        new BCryptPasswordEncoder(properties.bcryptStrength())
                )
        );

        return new Utf8BoundedPasswordEncoder(delegate);
    }

    @Bean
    AuthenticationManager authenticationManager(
            DatabaseUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    SecretKey jwtSecretKey(TentFlowSecurityProperties properties) {
        byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(properties.jwtSecret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "TENTFLOW_JWT_SECRET must be valid Base64.",
                    exception
            );
        }

        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "TENTFLOW_JWT_SECRET must decode to at least 32 bytes."
            );
        }

        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder
                .withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey secretKey,
            TentFlowSecurityProperties properties,
            Clock clock
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator();

        timestampValidator.setClock(clock);

        //spring permits a token without an exp claim by default
        //TentFlow requires every API access token to expire, so a missing exp is treated as invalid
        timestampValidator.setAllowEmptyExpiryClaim(false);

        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        timestampValidator,
                        new JwtIssuerValidator(properties.issuer()),
                        new JwtAudienceValidator(properties.audience()),

                        //nonblank JWT ID uniquely identifies every token and leaves room for future revocation support
                        new JwtClaimValidator<Object>(
                                "jti",
                                value -> value instanceof String jwtId
                                        && !jwtId.isBlank()
                        )
                );

        decoder.setJwtValidator(validator);

        return decoder;
    }
}