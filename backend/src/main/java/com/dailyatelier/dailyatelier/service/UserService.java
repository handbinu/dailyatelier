package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtistRegisterDto;
import com.dailyatelier.dailyatelier.dto.LoginRequestDto;
import com.dailyatelier.dailyatelier.dto.LoginResponseDto;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ArtistRepository artistRepository;
    private final JwtTokenProvider jwtTokenProvider;

    //로그인
    public LoginResponseDto login(LoginRequestDto dto){
        User user = userRepository.findByUserId(dto.getUserId());
        if(user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다");
        }
        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUserStatus());
        return new LoginResponseDto(token, user.getUserId(), user.getNickname(), user.getUserStatus());
    }

    //일반 회원가입
    @Transactional
    public void registerUser(User user){
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setJoinDate(LocalDateTime.now());
        user.setUserStatus(0);
        user.setReserve(0);
        userRepository.save(user);
    }

    //작가 회원가입
    @Transactional
    public void registerArtist(ArtistRegisterDto dto){
        User user = new User();
        user.setUserId(dto.getUserId());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setEmail(dto.getEmail());
        user.setJoinDate(LocalDateTime.now());
        user.setUserStatus(1);
        user.setReserve(0);
        userRepository.save(user);

        Artist artist = new Artist();
        artist.setUser(user);
        String artistName = (dto.getArtistName() != null && !dto.getArtistName().isBlank())
                ? dto.getArtistName() : dto.getNickname();
        artist.setArtistName(artistName);
        artist.setHomepage(dto.getHomepage());
        artist.setArtistSns(dto.getArtistSns());
        artist.setArtistIntro("");
        artistRepository.save(artist);
    }


    //중복 검사
    public boolean isUserIdDuplicate(String userId){
        return userRepository.existsByUserId(userId);
    }
    public boolean isNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }
}
