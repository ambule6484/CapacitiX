package com.capacitix.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;


    // =========================================================
    // TRAINER INVITATION
    // =========================================================

    public void sendTrainerInvitation(
            String trainerEmail,
            String trainerName,
            String invitationLink,
            String customMessage) {

        validateEmail(trainerEmail);
        validateInvitationLink(invitationLink);

        String message = cleanMessage(
                customMessage,
                "You have been invited to join Capacitix as a Trainer."
        );

        try {

            MimeMessage mimeMessage =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            mimeMessage,
                            true,
                            "UTF-8"
                    );

            helper.setTo(trainerEmail);

            helper.setSubject(
                    "Invitation to Join Capacitix as a Trainer"
            );

            String htmlContent =
                    buildInvitationEmail(
                            trainerName,
                            message,
                            invitationLink,
                            "Trainer",
                            "Accept Invitation"
                    );

            helper.setText(
                    htmlContent,
                    true
            );

            System.out.println(
                    "Sending trainer invitation through Gmail SMTP..."
            );

            mailSender.send(mimeMessage);

            System.out.println(
                    "TRAINER INVITATION SENT SUCCESSFULLY"
            );

        } catch (MessagingException e) {

            System.out.println(
                    "TRAINER EMAIL CREATION ERROR: "
                            + e.getMessage()
            );

            throw new RuntimeException(
                    "Unable to create trainer invitation email.",
                    e
            );

        } catch (MailException e) {

            printSmtpError(e);

            throw new RuntimeException(
                    "Unable to send trainer invitation email. "
                            + "Please check Gmail SMTP configuration "
                            + "and App Password.",
                    e
            );
        }
    }


    // =========================================================
    // EMPLOYEE INVITATION
    // =========================================================

    public void sendEmployeeInvitation(
            String employeeEmail,
            String employeeName,
            String invitationLink,
            String customMessage) {

        validateEmail(employeeEmail);
        validateInvitationLink(invitationLink);

        String message = cleanMessage(
                customMessage,
                "You have been invited to join Capacitix as an Employee."
        );

        try {

            MimeMessage mimeMessage =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            mimeMessage,
                            true,
                            "UTF-8"
                    );

            helper.setTo(employeeEmail);

            helper.setSubject(
                    "Invitation to Join Capacitix as an Employee"
            );

            String htmlContent =
                    buildInvitationEmail(
                            employeeName,
                            message,
                            invitationLink,
                            "Employee",
                            "Create Your Password"
                    );

            helper.setText(
                    htmlContent,
                    true
            );

            System.out.println(
                    "Sending employee invitation through Gmail SMTP..."
            );

            mailSender.send(mimeMessage);

            System.out.println(
                    "EMPLOYEE INVITATION SENT SUCCESSFULLY"
            );

            System.out.println(
                    "Recipient: " + employeeEmail
            );

            System.out.println(
                    "Invitation Link: " + invitationLink
            );

        } catch (MessagingException e) {

            System.out.println(
                    "EMPLOYEE EMAIL CREATION ERROR: "
                            + e.getMessage()
            );

            throw new RuntimeException(
                    "Unable to create employee invitation email.",
                    e
            );

        } catch (MailException e) {

            printSmtpError(e);

            throw new RuntimeException(
                    "Unable to send employee invitation email. "
                            + "Please check Gmail SMTP configuration "
                            + "and App Password.",
                    e
            );
        }
    }


    // =========================================================
    // PASSWORD RESET OTP
    // =========================================================

    public void sendPasswordResetOtp(
            String userName,
            String userEmail,
            String otp) {

        validateEmail(userEmail);

        if (otp == null ||
                otp.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "OTP is required."
            );
        }

        String safeUserName =
                userName == null ||
                        userName.trim().isEmpty()
                        ? "User"
                        : userName.trim();

        String safeOtp = otp.trim();

        try {

            MimeMessage mimeMessage =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            mimeMessage,
                            true,
                            "UTF-8"
                    );

            helper.setTo(userEmail);

            helper.setSubject(
                    "Capacitix Password Reset OTP"
            );

            String htmlContent =
                    buildPasswordResetOtpEmail(
                            safeUserName,
                            safeOtp
                    );

            helper.setText(
                    htmlContent,
                    true
            );

            System.out.println(
                    "Sending password reset OTP through Gmail SMTP..."
            );

            mailSender.send(mimeMessage);

            System.out.println(
                    "PASSWORD RESET OTP SENT SUCCESSFULLY"
            );

            System.out.println(
                    "Recipient: " + userEmail
            );

        } catch (MessagingException e) {

            System.out.println(
                    "PASSWORD RESET EMAIL CREATION ERROR: "
                            + e.getMessage()
            );

            throw new RuntimeException(
                    "Unable to create password reset OTP email.",
                    e
            );

        } catch (MailException e) {

            printSmtpError(e);

            throw new RuntimeException(
                    "Unable to send password reset OTP email. "
                            + "Please check Gmail SMTP configuration "
                            + "and App Password.",
                    e
            );
        }
    }


    // =========================================================
    // BUILD PASSWORD RESET OTP EMAIL
    // =========================================================

    private String buildPasswordResetOtpEmail(
            String userName,
            String otp) {

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport"
                          content="width=device-width, initial-scale=1.0">

                    <title>Capacitix Password Reset</title>
                </head>

                <body style="
                    margin:0;
                    padding:0;
                    background:#f5f7fb;
                    font-family:Arial,Helvetica,sans-serif;
                ">

                    <div style="
                        max-width:600px;
                        margin:40px auto;
                        background:#ffffff;
                        border-radius:12px;
                        overflow:hidden;
                        border:1px solid #e5e7eb;
                    ">

                        <!-- HEADER -->

                        <div style="
                            background:linear-gradient(
                                135deg,
                                #4f46e5,
                                #4338ca
                            );
                            padding:25px;
                            text-align:center;
                            color:white;
                        ">

                            <h1 style="
                                margin:0;
                                font-size:28px;
                            ">
                                Capacitix
                            </h1>

                            <p style="
                                margin:8px 0 0;
                                font-size:14px;
                            ">
                                Digital Capacity Building Platform
                            </p>

                        </div>


                        <!-- CONTENT -->

                        <div style="
                            padding:30px;
                            color:#1f2937;
                        ">

                            <h2 style="
                                margin-top:0;
                                color:#111827;
                            ">
                                Password Reset Request
                            </h2>

                            <p style="
                                color:#4b5563;
                                line-height:1.6;
                            ">
                                Hello %s,
                            </p>

                            <p style="
                                color:#4b5563;
                                line-height:1.6;
                            ">
                                We received a request to reset the password
                                for your Capacitix account.
                            </p>

                            <p style="
                                color:#4b5563;
                                line-height:1.6;
                            ">
                                Use the verification code below to continue
                                with your password reset:
                            </p>


                            <!-- OTP BOX -->

                            <div style="
                                text-align:center;
                                margin:30px 0;
                            ">

                                <div style="
                                    display:inline-block;
                                    padding:18px 30px;
                                    background:#eef2ff;
                                    border:2px solid #4f46e5;
                                    border-radius:12px;
                                ">

                                    <span style="
                                        font-size:32px;
                                        font-weight:bold;
                                        letter-spacing:8px;
                                        color:#4338ca;
                                    ">
                                        %s
                                    </span>

                                </div>

                            </div>


                            <p style="
                                color:#4b5563;
                                line-height:1.6;
                                text-align:center;
                            ">
                                This OTP is valid for
                                <strong>10 minutes</strong>.
                            </p>


                            <div style="
                                margin:25px 0;
                                padding:15px;
                                background:#f9fafb;
                                border-left:4px solid #4f46e5;
                                border-radius:6px;
                            ">

                                <p style="
                                    margin:0;
                                    color:#6b7280;
                                    font-size:13px;
                                    line-height:1.6;
                                ">
                                    For your security, never share this OTP
                                    with anyone. Capacitix will never ask
                                    you to share your verification code.
                                </p>

                            </div>


                            <p style="
                                color:#6b7280;
                                font-size:13px;
                                line-height:1.6;
                            ">
                                If you did not request a password reset,
                                you can safely ignore this email.
                            </p>

                        </div>


                        <!-- FOOTER -->

                        <div style="
                            padding:18px 30px;
                            background:#f9fafb;
                            border-top:1px solid #e5e7eb;
                            text-align:center;
                            color:#9ca3af;
                            font-size:12px;
                        ">

                            © Capacitix. All rights reserved.

                        </div>

                    </div>

                </body>
                </html>
                """.formatted(
                escapeHtml(userName),
                escapeHtml(otp)
        );
    }


    // =========================================================
    // BUILD INVITATION EMAIL
    // =========================================================

    private String buildInvitationEmail(
            String personName,
            String customMessage,
            String invitationLink,
            String role,
            String buttonText) {

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Capacitix Invitation</title>
                </head>

                <body style="
                    margin:0;
                    padding:0;
                    background:#f5f7fb;
                    font-family:Arial,Helvetica,sans-serif;
                ">

                    <div style="
                        max-width:600px;
                        margin:40px auto;
                        background:#ffffff;
                        border-radius:12px;
                        overflow:hidden;
                        border:1px solid #e5e7eb;
                    ">

                        <div style="
                            background:linear-gradient(
                                135deg,
                                #4f46e5,
                                #4338ca
                            );
                            padding:25px;
                            text-align:center;
                            color:white;
                        ">

                            <h1 style="
                                margin:0;
                                font-size:28px;
                            ">
                                Capacitix
                            </h1>

                            <p style="
                                margin:8px 0 0;
                                font-size:14px;
                            ">
                                Digital Capacity Building Platform
                            </p>

                        </div>


                        <div style="
                            padding:30px;
                            color:#1f2937;
                        ">

                            <h2 style="
                                margin-top:0;
                                color:#111827;
                            ">
                                Hello %s,
                            </h2>

                            <p style="
                                color:#4b5563;
                                line-height:1.6;
                            ">
                                %s
                            </p>

                            <p style="
                                color:#4b5563;
                                line-height:1.6;
                            ">
                                You have been invited to join
                                <strong>Capacitix</strong>
                                as a
                                <strong>%s</strong>.
                            </p>

                            <p style="
                                color:#4b5563;
                                line-height:1.6;
                            ">
                                Click the button below to accept your
                                invitation and create your account password.
                            </p>

                            <div style="
                                text-align:center;
                                margin:30px 0;
                            ">

                                <a href="%s"
                                   target="_blank"
                                   style="
                                       display:inline-block;
                                       padding:13px 25px;
                                       background:#4f46e5;
                                       color:#ffffff;
                                       text-decoration:none;
                                       border-radius:8px;
                                       font-weight:bold;
                                       font-size:14px;
                                   ">
                                    %s
                                </a>

                            </div>

                            <p style="
                                color:#6b7280;
                                font-size:12px;
                                line-height:1.6;
                                word-break:break-all;
                            ">
                                If the button does not work, copy and
                                paste this link into your browser:
                            </p>

                            <p style="
                                color:#4f46e5;
                                font-size:12px;
                                line-height:1.6;
                                word-break:break-all;
                            ">
                                %s
                            </p>

                            <p style="
                                color:#6b7280;
                                font-size:13px;
                                line-height:1.6;
                            ">
                                For security reasons, this invitation
                                link is valid for 24 hours.
                            </p>

                            <p style="
                                color:#6b7280;
                                font-size:13px;
                                line-height:1.6;
                            ">
                                If you did not expect this invitation,
                                you can safely ignore this email.
                            </p>

                        </div>


                        <div style="
                            padding:18px 30px;
                            background:#f9fafb;
                            border-top:1px solid #e5e7eb;
                            text-align:center;
                            color:#9ca3af;
                            font-size:12px;
                        ">
                            © Capacitix. All rights reserved.
                        </div>

                    </div>

                </body>
                </html>
                """.formatted(
                escapeHtml(personName),
                escapeHtml(customMessage),
                escapeHtml(role),
                escapeHtmlAttribute(invitationLink),
                escapeHtml(buttonText),
                escapeHtml(invitationLink)
        );
    }


    // =========================================================
    // VALIDATE EMAIL
    // =========================================================

    private void validateEmail(String email) {

        if (email == null ||
                email.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Email is required."
            );
        }

        if (!email.contains("@")) {

            throw new IllegalArgumentException(
                    "Invalid email address."
            );
        }
    }


    // =========================================================
    // VALIDATE INVITATION LINK
    // =========================================================

    private void validateInvitationLink(
            String invitationLink) {

        if (invitationLink == null ||
                invitationLink.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Invitation link is empty."
            );
        }

        if (invitationLink
                .trim()
                .equalsIgnoreCase("null")) {

            throw new IllegalArgumentException(
                    "Invitation link is null."
            );
        }

        if (!invitationLink.startsWith("http://") &&
                !invitationLink.startsWith("https://")) {

            throw new IllegalArgumentException(
                    "Invalid invitation link: "
                            + invitationLink
            );
        }
    }


    // =========================================================
    // CLEAN MESSAGE
    // =========================================================

    private String cleanMessage(
            String message,
            String defaultMessage) {

        if (message == null ||
                message.trim().isEmpty()) {

            return defaultMessage;
        }

        return message.trim();
    }


    // =========================================================
    // SMTP ERROR
    // =========================================================

    private void printSmtpError(
            MailException e) {

        System.out.println();
        System.out.println("==========================================");
        System.out.println("GMAIL SMTP ERROR");
        System.out.println("==========================================");

        System.out.println(
                "Message : " + e.getMessage()
        );

        Throwable cause = e.getCause();

        if (cause != null) {

            System.out.println(
                    "Cause   : " + cause.getMessage()
            );

            Throwable nestedCause =
                    cause.getCause();

            if (nestedCause != null) {

                System.out.println(
                        "Details : "
                                + nestedCause.getMessage()
                );
            }
        }

        System.out.println("==========================================");
    }


    // =========================================================
    // HTML ESCAPE
    // =========================================================

    private String escapeHtml(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }


    // =========================================================
    // HTML ATTRIBUTE ESCAPE
    // =========================================================

    private String escapeHtmlAttribute(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}