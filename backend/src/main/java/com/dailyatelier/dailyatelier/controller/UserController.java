package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.ArtistRegisterDto;
import com.dailyatelier.dailyatelier.dto.LoginRequestDto;
import com.dailyatelier.dailyatelier.dto.LoginResponseDto;
import com.dailyatelier.dailyatelier.dto.UserProfileDto;
import com.dailyatelier.dailyatelier.dto.ProfileUpdateDto;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // POST /api/auth/login
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto dto){
        LoginResponseDto response = userService.login(dto);
        return ResponseEntity.ok(response);
    }

    //POST /api/auth/register/user
    @PostMapping("/auth/register/user")
    public ResponseEntity<Map<String, String>> registerUser(@RequestBody User user){
        userService.registerUser(user);
        return ResponseEntity.ok(Map.of("message","회원가입이 완료되었습니다."));
    }

    //POST /api/auth/register/artist
    @PostMapping("/auth/register/artist")
    public ResponseEntity<Map<String, String>> registerArtist(@RequestBody ArtistRegisterDto dto){
        userService.registerArtist(dto);
        return ResponseEntity.ok(Map.of("message","작가 회원가입이 완료되었습니다."));
    }

    //GET /api/check/userId?value=aaa
    @GetMapping("/check/userId")
    public ResponseEntity<Map<String, Boolean>> checkUserId(@RequestParam String value){
        boolean duplicate = userService.isUserIdDuplicate(value);
        return ResponseEntity.ok(Map.of("duplicate", duplicate));
    }

    //GET /api/check/nickname?value=홍길동
    @GetMapping("/check/nickname")
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestParam String value){
        boolean duplicate = userService.isNicknameDuplicate(value);
        return ResponseEntity.ok(Map.of("duplicate", duplicate));
    }

    //GET /api/users/me
    @GetMapping("/users/me")
    public ResponseEntity<UserProfileDto> getUserProfile(@AuthenticationPrincipal String userId) {
        UserProfileDto profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    //PUT /api/users/me
    @PutMapping("/users/me")
    public ResponseEntity<Map<String, String>> updateUserProfile(
            @AuthenticationPrincipal String userId,
            @RequestBody ProfileUpdateDto dto) {
        userService.updateUserProfile(userId, dto);
        return ResponseEntity.ok(Map.of("message", "회원 정보가 성공적으로 수정되었습니다."));
    }
}
