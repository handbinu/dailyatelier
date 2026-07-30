package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtistRegisterDto;
import com.dailyatelier.dailyatelier.dto.LoginRequestDto;
import com.dailyatelier.dailyatelier.dto.LoginResponseDto;
import com.dailyatelier.dailyatelier.dto.UserProfileDto;
import com.dailyatelier.dailyatelier.dto.ProfileUpdateDto;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.entity.Address;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import com.dailyatelier.dailyatelier.repository.AddressRepository;
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
    private final AddressRepository addressRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PointAccountService pointAccountService;

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
        userRepository.save(user);
        pointAccountService.initializeAccount(user.getUserId());
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
        Artist artist = new Artist();
        artist.setUser(user);
        String artistName = (dto.getArtistName() != null && !dto.getArtistName().isBlank())
                ? dto.getArtistName() : dto.getNickname();
        artist.setArtistName(artistName);
        artist.setHomepage(dto.getHomepage());
        artist.setArtistSns(dto.getArtistSns());
        artist.setArtistIntro("");
        artistRepository.saveAndFlush(artist);
        pointAccountService.initializeAccount(user.getUserId());
    }


    //중복 검사
    public boolean isUserIdDuplicate(String userId){
        return userRepository.existsByUserId(userId);
    }
    public boolean isNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    // 마이페이지 프로필 조회
    public UserProfileDto getUserProfile(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다");
        }

        UserProfileDto dto = new UserProfileDto();
        dto.setUserId(user.getUserId());
        dto.setName(user.getName());
        dto.setNickname(user.getNickname());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setEmail(user.getEmail());
        dto.setUserStatus(user.getUserStatus());
        dto.setReserve(pointAccountService.getAvailableBalance(userId));
        dto.setEmailAgree(user.getEmailAgree());

        // 주소 정보 로드
        addressRepository.findById(userId).ifPresent(addr -> {
            dto.setZipCode(addr.getZipCode());
            dto.setUserAddress1(addr.getUserAddress1());
            dto.setUserAddress2(addr.getUserAddress2());
        });

        // 작가 정보 로드
        if (user.getUserStatus() == 1) {
            artistRepository.findByUser(user).ifPresent(artist -> {
                dto.setArtistName(artist.getArtistName());
                dto.setArtistIntro(artist.getArtistIntro());
                dto.setHomepage(artist.getHomepage());
                dto.setArtistSns(artist.getArtistSns());
            });
        }

        return dto;
    }

    // 마이페이지 프로필 수정
    @Transactional
    public void updateUserProfile(String userId, ProfileUpdateDto dto) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다");
        }

        // 비밀번호 변경 요청 시 검증
        if (dto.getNewPw() != null && !dto.getNewPw().isBlank()) {
            if (dto.getCurrentPw() == null || dto.getCurrentPw().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호를 입력해주세요");
            }
            if (!passwordEncoder.matches(dto.getCurrentPw(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다");
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPw()));
        }

        // 기본 정보 업데이트
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            if (!dto.getNickname().equals(user.getNickname()) && userRepository.existsByNickname(dto.getNickname())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 닉네임입니다");
            }
            user.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhoneNumber() != null && !dto.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(dto.getPhoneNumber());
        }
        if (dto.getEmailAgree() != null) {
            user.setEmailAgree(dto.getEmailAgree());
        }
        userRepository.save(user);

        // 주소 정보 업데이트
        Address address = addressRepository.findById(userId).orElseGet(() -> {
            Address newAddr = new Address();
            newAddr.setUser(user);
            return newAddr;
        });
        address.setZipCode(dto.getZipCode());
        address.setUserAddress1(dto.getUserAddress1());
        address.setUserAddress2(dto.getUserAddress2());
        addressRepository.save(address);

        // 작가 정보 업데이트
        if (user.getUserStatus() == 1) {
            Artist artist = artistRepository.findByUser(user).orElseGet(() -> {
                Artist newArtist = new Artist();
                newArtist.setUser(user);
                return newArtist;
            });
            if (dto.getArtistName() != null) {
                artist.setArtistName(dto.getArtistName().isBlank() ? user.getNickname() : dto.getArtistName());
            }
            if (dto.getArtistIntro() != null) {
                artist.setArtistIntro(dto.getArtistIntro());
            }
            if (dto.getHomepage() != null) {
                artist.setHomepage(dto.getHomepage());
            }
            if (dto.getArtistSns() != null) {
                artist.setArtistSns(dto.getArtistSns());
            }
            artistRepository.save(artist);
        }
    }
}
