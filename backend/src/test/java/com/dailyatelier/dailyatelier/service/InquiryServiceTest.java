package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.InquiryAnswerRequestDto;
import com.dailyatelier.dailyatelier.dto.InquiryCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.InquiryDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryStatus;
import com.dailyatelier.dailyatelier.entity.Inquiry;
import com.dailyatelier.dailyatelier.entity.InquiryType;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.InquiryRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private InquiryService inquiryService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setUserId("owner");
        owner.setNickname("소유자");
    }

    @Test
    void createsInquiryForAuthenticatedUser() {
        InquiryCreateRequestDto request = new InquiryCreateRequestDto();
        request.setInquiryType(InquiryType.DELIVERY);
        request.setTitle("배송 문의");
        request.setContent("배송 일정이 궁금합니다.");
        request.setEmailAlert(true);
        when(userRepository.findByUserId("owner")).thenReturn(owner);
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(invocation -> {
            Inquiry inquiry = invocation.getArgument(0);
            inquiry.setInquiryId(7L);
            inquiry.setCreatedAt(LocalDateTime.of(2026, 8, 14, 10, 0));
            return inquiry;
        });

        InquiryDetailResponseDto response = inquiryService.createInquiry("owner", request, null);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(owner);
        assertThat(captor.getValue().getInquiryType()).isEqualTo(InquiryType.DELIVERY);
        assertThat(response.getInquiryId()).isEqualTo(7L);
        assertThat(response.isAnswered()).isFalse();
    }

    @Test
    void rejectsOtherUsersInquiryDetail() {
        Inquiry inquiry = inquiry(3L, owner, null);
        when(inquiryRepository.findById(3L)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryService.getInquiryDetail("other", false, 3L))
                .isInstanceOf(DomainApiException.class)
                .satisfies(exception -> {
                    DomainApiException domainException = (DomainApiException) exception;
                    assertThat(domainException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(domainException.getCode()).isEqualTo("INQUIRY_ACCESS_FORBIDDEN");
                });
    }

    @Test
    void answersPendingInquiryOnlyOnce() {
        Inquiry inquiry = inquiry(4L, owner, null);
        InquiryAnswerRequestDto request = new InquiryAnswerRequestDto();
        request.setAnswer("확인 후 안내드리겠습니다.");
        when(inquiryRepository.findById(4L)).thenReturn(Optional.of(inquiry));

        InquiryDetailResponseDto response = inquiryService.answerInquiry(4L, request);

        assertThat(response.isAnswered()).isTrue();
        assertThat(response.getAnswer()).isEqualTo("확인 후 안내드리겠습니다.");
        assertThatThrownBy(() -> inquiryService.answerInquiry(4L, request))
                .isInstanceOf(DomainApiException.class)
                .satisfies(exception -> assertThat(((DomainApiException) exception).getCode())
                        .isEqualTo("INQUIRY_ALREADY_ANSWERED"));
    }

    @Test
    void filtersAdminInquiriesByAnswerStatus() {
        Inquiry pending = inquiry(5L, owner, null);
        when(inquiryRepository.findByAnsweredAtIsNullOrderByCreatedAtDesc(eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(pending)));

        var response = inquiryService.getAdminInquiries(InquiryStatus.PENDING, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).isAnswered()).isFalse();
    }

    private Inquiry inquiry(Long inquiryId, User user, LocalDateTime answeredAt) {
        Inquiry inquiry = new Inquiry();
        inquiry.setInquiryId(inquiryId);
        inquiry.setUser(user);
        inquiry.setInquiryType(InquiryType.OTHER);
        inquiry.setTitle("문의 제목");
        inquiry.setContent("문의 내용입니다.");
        inquiry.setCreatedAt(LocalDateTime.of(2026, 8, 14, 9, 0));
        inquiry.setAnsweredAt(answeredAt);
        return inquiry;
    }
}
