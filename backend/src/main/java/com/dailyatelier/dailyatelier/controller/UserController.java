package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.ArtistRegisterDto;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    //로그인
    @GetMapping("/login")
    public String loginForm(){
        return "login";
    }

    //회원 유형 선택
    @GetMapping("/register")
    public String registerSelect(){
        return  "register-select";
    }

    //일반 회원가입
    @GetMapping("/register/user")
    public String userRegisterForm(Model model){
        model.addAttribute("user", new User());
        return "register-user";
    }

    @PostMapping("/register/user")
    public String userRegister(@ModelAttribute User user){
            userService.registerUser(user);
            return "redirect:/login";
    }

    //작가 회원가입
    @GetMapping("/register/artist")
    public String artistRegisterForm(Model model){
        model.addAttribute("artistDto", new ArtistRegisterDto());
        return "register-artist";
    }

    @PostMapping("/register/artist")
    public String artistRegister(@ModelAttribute ArtistRegisterDto dto){
        userService.registerArtist(dto);
        return "redirect:/login";
    }

    //Ajax 중복확인 REST API
    @GetMapping("/api/check/userId")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkUserId(@RequestParam String value) {
        boolean duplicate = userService.isUserIdDuplicate(value);
        return ResponseEntity.ok(Map.of("duplicate", duplicate));
    }

    @GetMapping("/api/check/nickname")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestParam String value){
        boolean duplicate = userService.isNicknameDuplicate(value);
        return ResponseEntity.ok(Map.of("duplicate", duplicate));
    }
}
