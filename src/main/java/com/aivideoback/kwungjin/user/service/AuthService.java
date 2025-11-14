package com.aivideoback.kwungjin.user.service;

import com.aivideoback.kwungjin.security.jwt.JwtTokenProvider;
import com.aivideoback.kwungjin.user.dto.LoginRequest;
import com.aivideoback.kwungjin.user.dto.LoginResponse;
import com.aivideoback.kwungjin.user.dto.UserResponse;
import com.aivideoback.kwungjin.user.entity.User;
import com.aivideoback.kwungjin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUserId(),
                        request.getPassword()
                )
        );

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        User user = userRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        String token = jwtTokenProvider.createToken(user.getUserId(), authorities);

        return LoginResponse.builder()
                .token(token)
                .user(UserResponse.from(user))
                .build();
    }
}
