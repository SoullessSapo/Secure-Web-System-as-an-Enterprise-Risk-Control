package com.enterpriserisk.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RiskController {

  private static final Logger audit = LoggerFactory.getLogger("AUDIT");

  @GetMapping("/risk-status")
  public Map<String, Object> riskStatus(@AuthenticationPrincipal Jwt jwt) {
    // Audit trail of who accessed the resource. Never log the token itself.
    audit.info("risk-status accessed by subject={}", jwt.getSubject());
    return Map.of(
        "status", "protected",
        "subject", jwt.getSubject(),
        "control", "JWT validation and authenticated access");
  }
}
