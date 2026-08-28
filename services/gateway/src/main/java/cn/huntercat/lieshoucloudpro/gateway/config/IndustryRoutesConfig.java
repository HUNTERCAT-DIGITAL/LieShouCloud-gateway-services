package cn.huntercat.lieshoucloudpro.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

/**
 * 行业/商业服务路由（闭源组件，不在 LieShouCloud 开源交付包内）· 条件注册。
 *
 * <p>开源交付包部署：设置 {@code lsc.industry-routes-enabled=false}（compose 环境变量注入）， 不注册 crm / inventory /
 * finance / file / iot / device-gateway 路由——对应服务未随包部署， 避免 /api/customers 等请求 503 与 swagger 聚合页 404
 * 噪音。
 *
 * <p>商业/客户仓部署：保持默认 {@code true}，行为与历史 application.yml 显式路由完全一致 （2026-08 由 YAML 迁至 Java
 * DSL，predicate / filter / uri 逐条对齐）。
 */
@Configuration
public class IndustryRoutesConfig {

  /** 行业路由开关（默认开启，保持客户仓行为不变；开源交付包 compose 显式关闭）。 */
  @Value("${lsc.industry-routes-enabled:true}")
  private boolean industryRoutesEnabled;

  @Bean
  public RouteLocator industryRouteLocator(RouteLocatorBuilder builder) {
    if (!industryRoutesEnabled) {
      return builder.routes().build();
    }

    RouteLocatorBuilder.Builder routes = builder.routes();

    // ----- 业务路由（各行业服务强制 X-Tenant-Id，见对应服务） -----
    routes.route("crm-route", r -> r.path("/api/customers/**").uri("lb://lieshoucloud-crm"));
    routes.route(
        "inventory-route", r -> r.path("/api/products/**").uri("lb://lieshoucloud-inventory"));
    routes.route(
        "finance-route",
        r -> r.path("/api/ledger/**", "/api/bank/**").uri("lb://lieshoucloud-finance"));
    routes.route("file-route", r -> r.path("/api/files/**").uri("lb://lieshoucloud-file"));
    routes.route("iot-route", r -> r.path("/api/iot/**").uri("lb://lieshoucloud-iot"));
    routes.route(
        "device-http-route",
        r -> r.path("/api/devices/v1/**").uri("lb://lieshoucloud-device-gateway"));

    // ----- OpenAPI 文档转发（/v3/api-docs/{service} → 对应服务） -----
    openapiRoute(routes, "crm", "openapi-crm");
    openapiRoute(routes, "inventory", "openapi-inventory");
    openapiRoute(routes, "finance", "openapi-finance");
    openapiRoute(routes, "file", "openapi-file");
    openapiRoute(routes, "iot", "openapi-iot");
    openapiRoute(routes, "device-gateway", "openapi-device-gateway");

    // ----- Swagger UI 转发 -----
    swaggerUiRoute(routes, "crm", "swagger-ui-crm");
    swaggerUiRoute(routes, "inventory", "swagger-ui-inventory");
    swaggerUiRoute(routes, "finance", "swagger-ui-finance");
    swaggerUiRoute(routes, "file", "swagger-ui-file");
    swaggerUiRoute(routes, "iot", "swagger-ui-iot");
    swaggerUiRoute(routes, "device-gateway", "swagger-ui-device-gateway");

    return routes.build();
  }

  /** /v3/api-docs/{svc} → lb://lieshoucloud-{svc}，RewritePath 剥掉服务名前缀。 */
  private void openapiRoute(RouteLocatorBuilder.Builder routes, String svc, String id) {
    routes.route(
        id,
        r ->
            r.path("/v3/api-docs/" + svc, "/v3/api-docs/" + svc + "/**")
                .filters(
                    f ->
                        f.rewritePath(
                            "/v3/api-docs/" + svc + "(?<segment>/.*)?", "/v3/api-docs${segment}"))
                .uri("lb://lieshoucloud-" + svc));
  }

  /** /swagger-ui/{svc}/** → lb://lieshoucloud-{svc}，RewritePath 剥掉服务名前缀。 */
  private void swaggerUiRoute(RouteLocatorBuilder.Builder routes, String svc, String id) {
    routes.route(
        id,
        r ->
            r.path("/swagger-ui/" + svc, "/swagger-ui/" + svc + "/**")
                .filters(
                    f ->
                        f.rewritePath(
                            "/swagger-ui/" + svc + "(?<segment>/.*)", "/swagger-ui${segment}"))
                .uri("lb://lieshoucloud-" + svc));
  }
}
