package cn.huntercat.lieshoucloudpro.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.resource.NoResourceFoundException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import reactor.core.publisher.Mono;

/** GlobalErrorWebExceptionHandler 单测（L2-1 收尾 · gateway reactive 错误体契约）. */
class GlobalErrorWebExceptionHandlerTest {

  private final GlobalErrorWebExceptionHandler handler = new GlobalErrorWebExceptionHandler();

  private String writeAndRead(Throwable ex) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/roles"));
    Mono<Void> m = handler.handle(exchange, ex);
    m.block();
    return exchange.getResponse().getBodyAsString().block();
  }

  @Test
  void connectException_mapsTo503ServiceUnavailable() {
    String body = writeAndRead(new ConnectException("Connection refused"));
    assertThat(body).contains("\"error\":\"SERVICE_UNAVAILABLE\"");
    assertThat(body).contains("\"message\"");
  }

  @Test
  void unknownException_mapsTo500InternalError() {
    String body = writeAndRead(new IllegalStateException("boom"));
    assertThat(body).contains("\"error\":\"INTERNAL_ERROR\"");
    assertThat(body).contains("网关内部错误");
  }

  @Test
  void noResourceFound_mapsTo404NotFound() {
    // 未注册路由（开源交付包关闭行业路由后）→ 404 契约体，而非 500
    String body = writeAndRead(new NoResourceFoundException("api/customers"));
    assertThat(body).contains("\"error\":\"NOT_FOUND\"");
    assertThat(body).contains("请求的资源不存在");
  }

  @Test
  void noResourceFound_statusIs404() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/customers"));
    handler.handle(exchange, new NoResourceFoundException("api/customers")).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void errorBodyMatchesContractShape() {
    String body = writeAndRead(new IllegalStateException("boom"));
    // 契约：{ "error": "...", "message": "..." }
    assertThat(body).startsWith("{").endsWith("}");
    assertThat(body).contains("\"error\":");
    assertThat(body).contains("\"message\":");
  }

  @Test
  void statusCodeSetFor503() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/roles"));
    handler.handle(exchange, new ConnectException("refused")).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
