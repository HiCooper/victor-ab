package com.gateflow.victor.controller;

import com.gateflow.victor.domain.entity.User;
import com.gateflow.victor.infra.mapper.UserMapper;
import com.gateflow.victor.security.JwtTokenProvider;
import com.gateflow.victor.service.rbac.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "用户认证接口")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RbacService rbacService;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        List<String> roles = rbacService.getRoleNamesByUserId(user.getId());

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles);
        log.info("User {} logged in with roles: {}", user.getUsername(), roles);

        return ResponseEntity.ok(Map.of(
            "token", token,
            "userId", user.getId(),
            "username", user.getUsername(),
            "roles", roles
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public ResponseEntity<?> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of(
            "userId", auth.getPrincipal(),
            "roles", auth.getAuthorities().stream().map(Object::toString).toList()
        ));
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
