package cn.huntercat.lieshoucloudpro.gateway.config;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;

import java.nio.charset.StandardCharsets;

/**
 * Gateway 统一错误体写入（L2-1 · Bottom-Up 收尾）.
 *
 * <p>与 L0 api-client 契约对齐：{@code { "error": "<机器可读码>", "message": "<人类可读信息>" }}。 gateway 为
 * webflux（reactive）栈，无法复用 common 的 servlet {@code @RestControllerAdvice}， 此处自行实现统一写错误体。
 */
public final class ApiErrorWriter {

  private ApiErrorWriter() {}

  /** 写标准化错误体并返回完成的响应信号 */
  public static reactor.core.publisher.Mono<Void> write(
      ServerHttpResponse response, HttpStatus status, String error, String message) {
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String json = "{\"error\":\"" + error + "\",\"message\":\"" + escape(message) + "\"}";
    DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(reactor.core.publisher.Mono.just(buffer));
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
  }
}
