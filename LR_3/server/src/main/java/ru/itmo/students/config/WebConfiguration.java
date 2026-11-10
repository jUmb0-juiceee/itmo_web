package ru.itmo.students.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.condition.AbstractRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ru.itmo.students.web.QueryMapping;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

@Configuration
public class WebConfiguration implements WebMvcRegistrations {
    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new RequestMappingHandlerMapping() {
            @Override
            protected RequestCondition<?> getCustomMethodCondition(Method method) {
                return AnnotatedElementUtils.hasAnnotation(method, QueryMapping.class)
                        ? new QueryCondition() : super.getCustomMethodCondition(method);
            }
        };
    }

    private static final class QueryCondition extends AbstractRequestCondition<QueryCondition> {
        @Override protected Collection<?> getContent() { return List.of("QUERY"); }
        @Override protected String getToStringInfix() { return " && "; }
        @Override public QueryCondition combine(QueryCondition other) { return this; }
        @Override public int compareTo(QueryCondition other, HttpServletRequest request) { return 0; }
        @Override public QueryCondition getMatchingCondition(HttpServletRequest request) {
            String method = request.getMethod();
            if (method.equals("OPTIONS")) method = request.getHeader("Access-Control-Request-Method");
            return "QUERY".equals(method) ? this : null;
        }
    }
}
