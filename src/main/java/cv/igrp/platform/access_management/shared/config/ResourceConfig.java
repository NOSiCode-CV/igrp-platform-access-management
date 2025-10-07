package cv.igrp.platform.access_management.shared.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;
import java.util.stream.Collectors;

@Endpoint(id = "resources")
@Component
@SuppressWarnings("unused")
public class ResourceConfig {

    private final RequestMappingHandlerMapping handlerMapping;

    public ResourceConfig(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @ReadOperation
    public List<Map<String, String>> endpoints() {
        List<Map<String, String>> result = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();

            Set<String> paths = new HashSet<>();
            if (info.getPathPatternsCondition() != null) {
                paths.addAll(info.getPathPatternsCondition().getPatterns()
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.toSet()));
            } else if (info.getPatternsCondition() != null) {
                paths.addAll(info.getPatternsCondition().getPatterns());
            }

            Set<String> httpMethods = info.getMethodsCondition().getMethods()
                    .stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());
            if (httpMethods.isEmpty()) {
                httpMethods = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
            }

            for (String path : paths) {
                for (String httpMethod : httpMethods) {
                    Map<String, String> endpoint = new LinkedHashMap<>();
                    endpoint.put("name", method.getMethod().getName());
                    endpoint.put("description", method.getMethod().getName());
                    endpoint.put("url", path);
                    endpoint.put("resource", method.getBeanType().getSimpleName());
                    endpoint.put("method", httpMethod);
                    result.add(endpoint);
                }
            }
        }

        return result;
    }
}
