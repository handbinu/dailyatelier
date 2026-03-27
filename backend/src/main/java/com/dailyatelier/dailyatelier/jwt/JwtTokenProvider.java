package com.dailyatelier.dailyatelier.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private  final SecretKey secretKey;
    private final long expirationMs;

    public  JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs
    ){
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    //토큰 생성
    public String generateToken(String userId, int userStatus){
        return Jwts.builder()
                .subject(userId)
                .claim("userStatus", userStatus)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+expirationMs))
                .signWith(secretKey)
                .compact();
    }

    // 토큰에서 userId 추출
    public String getUserId(String token){
        return getClaims(token).getSubject();
    }

    // 토큰에서 userStatus 추출
    public int getUserStatus(String token){
        return getClaims(token).get("userStatus", Integer.class);
    }

    //토큰 유효성 검증
    public boolean validateToken(String token){
        try{
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e){
            log.warn("만료된 JWT 토큰 : {}", e.getMessage());
        } catch ( JwtException | IllegalArgumentException e){
            log.warn("유효하지 않은 JWT 토큰 : {}", e.getMessage());
        }
        return false;
    }
    
    private Claims getClaims(String token){
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}