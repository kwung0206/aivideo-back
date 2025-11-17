package com.aivideoback.kwungjin.user.service;

import com.aivideoback.kwungjin.user.entity.EmailVerification;
import com.aivideoback.kwungjin.user.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;   // ✅ JavaMailSender 대신 이 서비스 사용

    private static final long EXPIRE_MINUTES = 5L;

    /** 인증번호 발송 */
    @Transactional
    public void sendVerificationCode(String rawEmail) {
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일을 입력해 주세요.");
        }
        String email = rawEmail.trim().toLowerCase();

        // 이전 기록 삭제 (선택)
        emailVerificationRepository.deleteByEmail(email);

        String code = generateCode();

        EmailVerification ev = EmailVerification.builder()
                .email(email)
                .code(code) // 🔐 필요하면 나중에 해시로 변경 가능
                .expiresAt(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES))
                .verified("N")
                .build();

        // 1) 먼저 메일 전송 시도
        emailService.sendEmailCode(email, code);

        // 2) 전송 성공하면 DB 저장
        emailVerificationRepository.save(ev);
    }

    /** 인증번호 검증 */
    @Transactional
    public void verifyCode(String rawEmail, String rawCode) {
        if (rawEmail == null || rawEmail.trim().isEmpty()
                || rawCode == null || rawCode.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일과 인증번호를 모두 입력해 주세요.");
        }

        String email = rawEmail.trim().toLowerCase();
        String code = rawCode.trim();

        EmailVerification ev = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("먼저 인증번호를 요청해 주세요."));

        if (ev.isExpired()) {
            throw new IllegalArgumentException("인증번호 유효 시간이 만료되었습니다. 다시 요청해 주세요.");
        }

        if (!ev.getCode().equals(code)) {
            throw new IllegalArgumentException("인증번호가 올바르지 않습니다.");
        }

        ev.setVerified("Y");
    }

    /** 해당 이메일이 인증 완료 상태인지 확인 */
    @Transactional(readOnly = true)
    public boolean isVerified(String rawEmail) {
        if (rawEmail == null) return false;
        String email = rawEmail.trim().toLowerCase();

        return emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .map(ev -> ev.isVerifiedFlag() && !ev.isExpired())
                .orElse(false);
    }

    private String generateCode() {
        int num = ThreadLocalRandom.current().nextInt(100000, 1000000); // 100000~999999
        return Integer.toString(num);
    }
}
