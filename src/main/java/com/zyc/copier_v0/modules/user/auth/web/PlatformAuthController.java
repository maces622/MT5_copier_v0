package com.zyc.copier_v0.modules.user.auth.web;

import com.zyc.copier_v0.modules.user.auth.api.AuthenticatedPlatformUserResponse;
import com.zyc.copier_v0.modules.user.auth.api.LoginPlatformUserRequest;
import com.zyc.copier_v0.modules.user.auth.api.RegisterPlatformUserRequest;
import com.zyc.copier_v0.modules.user.auth.api.UpdatePlatformUserProfileRequest;
import com.zyc.copier_v0.modules.user.auth.service.PlatformAuthService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    public PlatformAuthController(PlatformAuthService platformAuthService) {
        this.platformAuthService = platformAuthService;
    }

    @PostMapping("/register")
    public AuthenticatedPlatformUserResponse register(
            @Valid @RequestBody RegisterPlatformUserRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        return platformAuthService.register(request, servletRequest, servletResponse);
    }

    @PostMapping("/login")
    public AuthenticatedPlatformUserResponse login(
            @Valid @RequestBody LoginPlatformUserRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        return platformAuthService.login(request, servletRequest, servletResponse);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        platformAuthService.logout(servletRequest, servletResponse);
    }

    @GetMapping("/me")
    public AuthenticatedPlatformUserResponse me(HttpServletRequest servletRequest) {
        return platformAuthService.me(servletRequest);
    }

    @PutMapping("/me")
    public AuthenticatedPlatformUserResponse updateProfile(
            @Valid @RequestBody UpdatePlatformUserProfileRequest request,
            HttpServletRequest servletRequest
    ) {
        return platformAuthService.updateProfile(request, servletRequest);
    }
}
