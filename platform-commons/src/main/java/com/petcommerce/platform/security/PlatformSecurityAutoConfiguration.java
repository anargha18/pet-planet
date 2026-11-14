package com.petcommerce.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Servlet services: one RS256 {@link JwtDecoder} built from {@code jwt.public-key} with the platform validators,
 * the {@link PlatformHttpSecurity} helper and the {@link AuthenticatedCaller} controller argument (R-SEC-02).
 * Starts only when the service has Spring Security's resource-server support on its classpath.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, JwtDecoder.class, WebMvcConfigurer.class})
@EnableWebSecurity
@EnableMethodSecurity
public class PlatformSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder platformJwtDecoder(
            @Value("${jwt.public-key:}") String publicKey,
            @Value("${jwt.issuer:pet-planet}") String issuer,
            @Value("${jwt.audience:pet-planet}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(PlatformJwt.parsePublicKey(publicKey))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(PlatformJwt.validator(issuer, audience));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformHttpSecurity platformHttpSecurity(ObjectMapper objectMapper) {
        return new PlatformHttpSecurity(objectMapper);
    }

    @Bean
    public WebMvcConfigurer authenticatedCallerWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new AuthenticatedCallerArgumentResolver());
            }
        };
    }
}
