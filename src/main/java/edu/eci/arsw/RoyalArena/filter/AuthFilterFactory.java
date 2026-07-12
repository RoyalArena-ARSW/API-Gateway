package com.escuela.techcup.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthFilterFactory extends AbstractGatewayFilterFactory<AuthFilterFactory.Config> {
    private final AuthFilter authFilter;

    public AuthFilterFactory(AuthFilter authFilter) {
        super(Config.class);
        this.authFilter = authFilter;
    }

    @Override
    public String name() {
        return "AuthFilter";
    }

    @Override
    public GatewayFilter apply(Config config) {
        return authFilter;
    }

    public static class Config {
    }
}