package pe.clinicacardenas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Versionado del API (v2.12). La ruta canónica es /api/v1/...; la app móvil la usa. Las rutas sin versión (/api/...)
 * se mantienen como sinónimo para la web, que ya estaba publicada. El filtro corre antes que la seguridad, así que
 * los permisos se aplican igual en las dos formas.
 */
@Configuration
public class ApiVersionFilter {

    static final String PREFIX = "/api/v1/";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> apiVersionAlias() {
        OncePerRequestFilter f = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                String uri = req.getRequestURI();
                String ctx = req.getContextPath();
                if (!uri.startsWith(ctx + PREFIX)) {
                    chain.doFilter(req, res);
                    return;
                }
                String path = "/api/" + uri.substring((ctx + PREFIX).length());
                res.setHeader("X-API-Version", "1");
                chain.doFilter(new HttpServletRequestWrapper(req) {
                    @Override public String getRequestURI() { return ctx + path; }
                    @Override public String getServletPath() { return path; }
                    @Override public String getPathInfo() { return null; }
                    @Override public StringBuffer getRequestURL() {
                        StringBuffer u = new StringBuffer(req.getScheme()).append("://").append(req.getServerName());
                        int port = req.getServerPort();
                        if (port != 80 && port != 443) u.append(':').append(port);
                        return u.append(ctx).append(path);
                    }
                }, res);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(f);
        reg.addUrlPatterns("/api/v1/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.setName("apiVersionAlias");
        return reg;
    }
}
