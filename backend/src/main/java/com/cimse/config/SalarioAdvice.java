package com.cimse.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Iterator;

/**
 * Los sueldos solo los puede ver el administrador (permiso SEGURIDAD). Para cualquier otro usuario se quitan
 * los campos de sueldo de las respuestas de personal y del visor de tablas.
 */
@RestControllerAdvice
public class SalarioAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper mapper;

    public SalarioAdvice(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    private boolean esAdministrador() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) {
            return false;
        }
        for (GrantedAuthority g : a.getAuthorities()) {
            if ("PERM_SEGURIDAD".equals(g.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String ruta = request.getURI().getPath();
        if (body == null || body instanceof String || esAdministrador()
                || !(ruta.startsWith("/api/personal") || ruta.startsWith("/api/tablas"))) {
            return body;
        }
        JsonNode nodo = mapper.valueToTree(body);
        quitarSueldos(nodo);
        return nodo;
    }

    private void quitarSueldos(JsonNode nodo) {
        if (nodo instanceof ObjectNode) {
            ObjectNode o = (ObjectNode) nodo;
            Iterator<String> nombres = o.fieldNames();
            java.util.List<String> quitar = new java.util.ArrayList<>();
            while (nombres.hasNext()) {
                String n = nombres.next();
                if (n.toLowerCase().contains("sueldo") || n.toLowerCase().contains("salario")) {
                    quitar.add(n);
                }
            }
            o.remove(quitar);
            o.elements().forEachRemaining(this::quitarSueldos);
        } else if (nodo instanceof ArrayNode) {
            nodo.elements().forEachRemaining(this::quitarSueldos);
        }
    }
}
