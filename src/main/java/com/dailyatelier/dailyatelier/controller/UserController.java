package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/register")
    public String registerForm(Model model){
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute User user){
            userService.register(user);
            return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginForm(){
        return "login";
    }
}
