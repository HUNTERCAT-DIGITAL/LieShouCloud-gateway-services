package cn.huntercat.lieshoucloudpro.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.huntercat.lieshoucloudpro.jwt.JwtSupport;

/** JWT 共享能力 bean（HS256 · 单一事实源，自 jwt-support 模块；与 auth-service 同源 secret）. */
@Configuration
public class JwtSupportConfig {

  @Bean
  public JwtSupport jwtSupport(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer:lieshoucloud}") String issuer) {
    return new JwtSupport(secret, issuer);
  }
}
