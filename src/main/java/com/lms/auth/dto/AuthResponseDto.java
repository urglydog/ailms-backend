package com.lms.auth.dto;

public class AuthResponseDto {

    public record TokenRes(
            String accessToken,
            String refreshToken
    ) {}
    
    public record MessageRes(
            String message
    ) {}
}
