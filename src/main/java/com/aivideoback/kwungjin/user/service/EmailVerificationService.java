// src/main/java/com/aivideoback/kwungjin/user/service/EmailVerificationService.java
package com.aivideoback.kwungjin.user.service;

import com.aivideoback.kwungjin.user.entity.EmailVerification;
import com.aivideoback.kwungjin.user.repository.EmailVerificationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.mail.from:no-reply@aicollector.co.kr}")
    private String fromAddress;

    @Value("${app.mail.from-name:AI 콜렉터}")
    private String fromName;

    private static final SecureRandom RNG = new SecureRandom();
    private static final long EXPIRE_MINUTES = 10L;

    @Transactional
    public void sendVerificationCode(String rawEmail) {
        String email = normalize(rawEmail);
        String code = make6digits();
        String codeHash = passwordEncoder.encode(code);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(EXPIRE_MINUTES);

        // 1) 메일 먼저 보내기 (실패 시 DB에 안 남게)
        sendMail(email, code);

        // 2) 성공하면 DB 저장
        EmailVerification ev = EmailVerification.builder()
                .email(email)
                .codeHash(codeHash)
                .attempts(0)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        emailVerificationRepository.save(ev);

        log.info("[EMAIL-CODE] sent to={}, expiresAt={}", email, expiresAt);
    }

    @Transactional
    public void verifyCode(String rawEmail, String rawCode) {
        String email = normalize(rawEmail);
        String code = rawCode == null ? "" : rawCode.trim();
        LocalDateTime now = LocalDateTime.now();

        EmailVerification latest = emailVerificationRepository
                .findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("인증 요청 내역이 없습니다. 먼저 인증번호를 받아 주세요."));

        if (latest.getVerifiedAt() != null) {
            // 이미 인증된 경우는 그냥 통과
            return;
        }

        if (now.isAfter(latest.getExpiresAt())) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다. 다시 요청해 주세요.");
        }

        if (!passwordEncoder.matches(code, latest.getCodeHash())) {
            int attempts = latest.getAttempts() == null ? 0 : latest.getAttempts();
            latest.setAttempts(attempts + 1);
            emailVerificationRepository.save(latest);
            throw new IllegalArgumentException("인증번호가 올바르지 않습니다.");
        }

        latest.setVerifiedAt(now);
        emailVerificationRepository.save(latest);
    }

    // 회원가입 직전에 이메일 인증이 되었는지 확인용
    public boolean isRecentlyVerified(String rawEmail) {
        String email = normalize(rawEmail);
        LocalDateTime border = LocalDateTime.now().minusHours(24);

        return emailVerificationRepository
                .findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
                .map(ev -> ev.getVerifiedAt() != null && ev.getVerifiedAt().isAfter(border))
                .orElse(false);
    }

    /* ---------- helpers ---------- */

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String make6digits() {
        int n = RNG.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private void sendMail(String to, String code) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject("[AI 콜렉터] 이메일 인증번호");

            String html = """
                    <div style="font-family: system-ui,-apple-system,BlinkMacSystemFont,'Noto Sans KR',sans-serif;">
                      <h2>이메일 인증</h2>
                      <p>아래 인증번호를 %d분 이내에 입력해 주세요.</p>
                      <div style="margin-top:16px;font-size:28px;font-weight:700;letter-spacing:4px;">%s</div>
                    </div>
                    """.formatted(EXPIRE_MINUTES, code);

            helper.setText(html, true);

            mailSender.send(msg);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("[EMAIL-CODE] failed to send mail to {}", to, e);
            throw new IllegalStateException("인증 메일 전송에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }
    }
}
