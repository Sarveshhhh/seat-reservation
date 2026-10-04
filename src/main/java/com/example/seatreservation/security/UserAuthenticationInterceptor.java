package com.example.seatreservation.security;

import com.example.seatreservation.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserAuthenticationInterceptor implements HandlerInterceptor {

    public static final String HEADER_USER_ID = "X-User-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(HEADER_USER_ID);
        
        // For endpoints requiring authentication, check header presence
        String uri = request.getRequestURI();
        boolean requiresAuth = uri.contains("/reserve") || uri.contains("/cancel");

        if (requiresAuth) {
            if (userId == null || userId.isBlank()) {
                throw new UnauthorizedException("Missing required authentication header '" + HEADER_USER_ID + "'");
            }
            SecurityContext.setCurrentUserId(userId.trim());
        } else if (userId != null && !userId.isBlank()) {
            SecurityContext.setCurrentUserId(userId.trim());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityContext.clear();
    }
}
