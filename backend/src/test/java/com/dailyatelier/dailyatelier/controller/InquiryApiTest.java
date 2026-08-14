package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.InquiryAdminListResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryListResponseDto;
import com.dailyatelier.dailyatelier.entity.InquiryType;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.InquiryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({InquiryController.class, AdminInquiryController.class})
@Import(SecurityConfig.class)
class InquiryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InquiryService inquiryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void authenticatedUserCanCreateInquiry() throws Exception {
        InquiryDetailResponseDto inquiry = detail(7L, "member", false, null);
        when(inquiryService.createInquiry(eq("member"), any(), any())).thenReturn(inquiry);
        MockMultipartFile request = new MockMultipartFile(
                "request", "", "application/json",
                "{\"inquiryType\":\"DELIVERY\",\"title\":\"배송 문의\",\"content\":\"배송 일정이 궁금합니다.\",\"emailAlert\":true}".getBytes()
        );

        mockMvc.perform(multipart("/api/inquiries")
                        .file(request)
                        .with(authentication(userAuthentication("member"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inquiryId").value(7))
                .andExpect(jsonPath("$.answered").value(false));
    }

    @Test
    void nonAdminCannotReadAdminInquiryList() throws Exception {
        mockMvc.perform(get("/api/admin/inquiries")
                        .with(authentication(userAuthentication("member"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanReadAllInquiries() throws Exception {
        InquiryAdminListResponseDto inquiry = new InquiryAdminListResponseDto(
                8L, "member", "회원", InquiryType.POINT, "포인트 문의", false,
                LocalDateTime.of(2026, 8, 14, 9, 0), null
        );
        when(inquiryService.getAdminInquiries(any(), any()))
                .thenReturn(new PageImpl<>(List.of(inquiry), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/inquiries")
                        .with(authentication(adminAuthentication("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value("member"))
                .andExpect(jsonPath("$.content[0].answered").value(false));
    }

    @Test
    void anonymousUserCannotReadMyInquiries() throws Exception {
        mockMvc.perform(get("/api/inquiries/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private InquiryDetailResponseDto detail(Long inquiryId, String userId, boolean answered, String answer) {
        return new InquiryDetailResponseDto(
                inquiryId, userId, InquiryType.DELIVERY, "배송 문의", "배송 일정이 궁금합니다.", true,
                null, null, null, answered, answer,
                LocalDateTime.of(2026, 8, 14, 9, 0), answered ? LocalDateTime.of(2026, 8, 14, 10, 0) : null
        );
    }

    private UsernamePasswordAuthenticationToken userAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private UsernamePasswordAuthenticationToken adminAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
