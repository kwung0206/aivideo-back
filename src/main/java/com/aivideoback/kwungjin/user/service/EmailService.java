package com.aivideoback.kwungjin.user.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // application.yml 에 이미 spring.mail.username 설정이 있으니 app.mail.* 쪽만 새로 추가해도 됨
    @Value("${app.mail.from:no-reply@aicollector.co.kr}")
    private String from;

    @Value("${app.mail.from-name:AI Collector}")
    private String fromName;

    /** 인증 코드 메일 발송 */
    public void sendEmailCode(String to, String code) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");

            h.setFrom(from, fromName);
            h.setTo(to);
            h.setSubject("[AI 콜렉터] 이메일 인증 코드");
            h.setText(buildHtml(code), true); // HTML 메일

            mailSender.send(msg);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("메일 전송 실패", e);
        }
    }

    private String buildHtml(String code) {
        return """
            <div style="font-family: system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
              <h2>이메일 인증</h2>
              <p>아래 인증 코드를 5분 이내에 입력해 주세요.</p>
              <div style="font-size:28px;font-weight:700;letter-spacing:4px;margin-top:12px">%s</div>
              <p style="margin-top:24px;font-size:13px;color:#6b7280">
                본 메일은 발신 전용입니다.
              </p>
            </div>
        """.formatted(code);
    }
}
