package cn.huntercat.lieshoucloudpro.gateway.config;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebExchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;

/**
 * Gateway 全局路由错误处理（L2-1 · Bottom-Up 收尾）.
 *
 * <p>覆盖网关自身的异常（而非下游服务已返回的响应体）：
 *
 * <ul>
 *   <li>下游服务不可达（ConnectException / TimeoutException / 503）→ {@code 503 SERVICE_UNAVAILABLE}
 *   <li>其他未知异常 → {@code 500 INTERNAL_ERROR}（仅记日志，不泄露堆栈）
 * </ul>
 *
 * <p>与 {@link ApiErrorWriter} 配合输出统一契约体 {@code { error, message }}。
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    String error = "INTERNAL_ERROR";
    String message = "网关内部错误，请稍后重试";

    Throwable root = rootCause(ex);
    if (root instanceof NoResourceFoundException) {
      // 未注册路由（开源交付包关闭行业路由后，/api/customers 等无匹配）→ 404 而非 500
      status = HttpStatus.NOT_FOUND;
      error = "NOT_FOUND";
      message = "请求的资源不存在";
    } else if (root instanceof ConnectException
        || root instanceof TimeoutException
        || ex instanceof org.springframework.cloud.gateway.support.ServiceUnavailableException) {
      status = HttpStatus.SERVICE_UNAVAILABLE;
      error = "SERVICE_UNAVAILABLE";
      message = "下游服务暂不可用，请稍后重试";
    }

    if (status.is5xxServerError()) {
      log.error(
          "Gateway error on {}: {}", exchange.getRequest().getPath(), root.getMessage(), root);
    }
    return ApiErrorWriter.write(exchange.getResponse(), status, error, message);
  }

  private static Throwable rootCause(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    return cur;
  }
}
