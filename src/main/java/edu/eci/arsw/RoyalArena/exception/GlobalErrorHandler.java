package edu.eci.arsw.RoyalArena.exception;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class GlobalErrorHandler extends DefaultErrorWebExceptionHandler {

    public GlobalErrorHandler(ErrorAttributes errorAttributes,
                               WebProperties webProperties,
                               ApplicationContext applicationContext,
                               ServerCodecConfigurer configurer) {
        super(errorAttributes, webProperties.getResources(), new ErrorProperties(), applicationContext);
        this.setMessageWriters(configurer.getWriters());
        this.setMessageReaders(configurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(
            ErrorAttributes errorAttributes) {
        return RouterFunctions.route(
                RequestPredicates.all(),
                this::renderErrorResponse
        );
    }

    @Override
    protected Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Map<String, Object> errorAttributes = getErrorAttributes(
                request,
                getErrorAttributeOptions(request, MediaType.APPLICATION_JSON)
        );

        int statusCode = (int) errorAttributes.getOrDefault("status", 500);
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        Map<String, Object> errorResponse = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    statusCode,
                "error",     obtenerMensaje(statusCode),
                "path",      errorAttributes.getOrDefault("path", "desconocido")
        );

        return ServerResponse
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(errorResponse));
    }

    private String obtenerMensaje(int statusCode) {
        return switch (statusCode) {
            case 401 -> "Token requerido o inválido";
            case 403 -> "No tiene permisos para esta operación";
            case 404 -> "Recurso no encontrado";
            case 503 -> "Servicio no disponible temporalmente";
            default  -> "Error interno del servidor";
        };
    }
}