package cn.huntercat.lieshoucloudpro.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * CORS 白名单配置（详见 .ai/SECURITY.md §5）。
 *
 * <p>Spring Cloud Gateway 基于 WebFlux，必须用 {@code reactive.*} 包下的 CORS 类； 用 {@code CorsWebFilter}
 * 注册到全局路由。
 *
 * <p>关键：禁止 {@code Access-Control-Allow-Origin: *} —— 前端必须经由白名单进入；allowCredentials = true 时 origin
 * 也不能是 *。
 *
 * <p>同源放行（2026-08 修复）：浏览器对同源请求也会携带 {@code Origin} 头（POST 等），若未命中白名单会被 CorsWebFilter 拦为 403 ——
 * 导致每个新部署域名都要手工加白名单。本配置对「Origin 与请求 Host 一致」的请求 直接放行（不返回 CORS 头），跨域请求仍严格按白名单校验。
 */
@Configuration
public class CorsConfig {

  @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
  private String allowedOriginsCsv;

  @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
  private String allowedMethodsCsv;

  @Value("${app.cors.allowed-headers:Authorization,Content-Type,X-Requested-With}")
  private String allowedHeadersCsv;

  @Value("${app.cors.allow-credentials:true}")
  private boolean allowCredentials;

  @Bean
  public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();

    // 1. 显式 origin 列表（不能用 *）
    List<String> origins = Arrays.asList(allowedOriginsCsv.split("\\s*,\\s*"));
    origins.forEach(config::addAllowedOrigin);

    // 2. 显式 methods
    Arrays.stream(allowedMethodsCsv.split("\\s*,\\s*")).forEach(config::addAllowedMethod);

    // 3. 显式 headers
    Arrays.stream(allowedHeadersCsv.split("\\s*,\\s*")).forEach(config::addAllowedHeader);

    // 4. credentials 显式
    config.setAllowCredentials(allowCredentials);

    // 5. 预检有效期（秒）—— 1 day
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source =
        new UrlBasedCorsConfigurationSource() {
          @Override
          public CorsConfiguration getCorsConfiguration(ServerWebExchange exchange) {
            String origin = exchange.getRequest().getHeaders().getOrigin();
            String host = exchange.getRequest().getHeaders().getFirst("Host");
            // 同源请求（Origin 与 Host 一致）直接放行，避免新部署域名都要手工加白名单
            if (origin != null && host != null && sameOrigin(origin, host)) {
              return null;
            }
            return super.getCorsConfiguration(exchange);
          }
        };
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
  }

  /**
   * Origin（https://host[:port]）与请求 Host 头是否同源（忽略默认端口与大小写）。
   *
   * <p>Host 头可能不带端口（443/80 默认）或带显式端口（如 localhost:5173）；Origin 的 URI 解析出 host + port。
   */
  private boolean sameOrigin(String origin, String hostHeader) {
    try {
      URI uri = URI.create(origin);
      String originHost = uri.getHost();
      if (originHost == null) return false;
      int originPort = uri.getPort();
      // Host 头拆 host[:port]
      String host = hostHeader;
      int hostPort = -1;
      int colon = hostHeader.lastIndexOf(':');
      if (colon > 0 && hostHeader.indexOf(']') < colon) {
        try {
          hostPort = Integer.parseInt(hostHeader.substring(colon + 1));
          host = hostHeader.substring(0, colon);
        } catch (NumberFormatException ignored) {
          // 非端口后缀（IPv6 等），保留原串
        }
      }
      if (!originHost.equalsIgnoreCase(host)) return false;
      if (originPort == -1 || hostPort == -1) {
        // 任一侧未显式端口：http→80 / https→443 视为等价
        int op =
            originPort == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : originPort;
        int hp = hostPort == -1 ? op : hostPort;
        return op == hp;
      }
      return originPort == hostPort;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
