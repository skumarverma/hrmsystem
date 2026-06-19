package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.config.JwtUtil;
import com.hrm.hrmsystem.dto.*;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.PasswordResetOTP;
import com.hrm.hrmsystem.model.OtpVerification;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.PasswordResetOTPRepository;
import com.hrm.hrmsystem.repository.OtpVerificationRepository;
import com.hrm.hrmsystem.repository.UserRepository;
import com.hrm.hrmsystem.util.EmailUtil;
import com.hrm.hrmsystem.util.SmsUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PasswordResetOTPRepository otpRepository;
    private final EmailUtil emailUtil;
    private final OtpVerificationRepository otpVerificationRepository;
    private final SmsUtil smsUtil;

    @Value("${fast2sms.otp-expiry-minutes}")
    private int otpExpiryMinutes;

    @Value("${fast2sms.cooldown-ms}")
    private long cooldownMs;

    @Value("${fast2sms.max-attempts}")
    private int maxAttempts;

    public AuthService(UserRepository userRepository, EmployeeRepository employeeRepository,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       PasswordResetOTPRepository otpRepository, EmailUtil emailUtil,
                       OtpVerificationRepository otpVerificationRepository, SmsUtil smsUtil) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.otpRepository = otpRepository;
        this.emailUtil = emailUtil;
        this.otpVerificationRepository = otpVerificationRepository;
        this.smsUtil = smsUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
               .role(User.Role.valueOf(request.getRole().toUpperCase())) 
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        if (request.getEmployeeId() != null) {
            Employee employee = employeeRepository.findByIdentifier(request.getEmployeeId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            user.setEmployee(employee);
        }

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .employeeId(user.getEmployee() != null ? user.getEmployee().getId() : null)
                .employeeName(user.getEmployee() != null ? 
                        user.getEmployee().getFirstName() + " " + user.getEmployee().getLastName() : null)
                .token(token)
                .message("Registration successful")
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        throw new UnsupportedOperationException("Email/Password login has been disabled. Please login using Mobile Number + OTP.");
    }

    @Transactional
    public AuthResponse verifyOtpAndLogin(VerifySMSOTPRequest request) {
        String cleanPhone = request.getMobileNumber().replaceAll("[^0-9]", "");
        if (cleanPhone.length() > 10) {
            cleanPhone = cleanPhone.substring(cleanPhone.length() - 10);
        }

        OtpVerification otpVerification = otpVerificationRepository
                .findFirstByMobileNumberAndUsedFalseOrderByCreatedAtDesc(cleanPhone)
                .orElseThrow(() -> new RuntimeException("No active OTP request found for this mobile number"));

        // Validate OTP value
        if (!otpVerification.getOtp().equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        // Validate expiration
        if (otpVerification.isExpired()) {
            throw new RuntimeException("OTP has expired. Please request a new OTP.");
        }

        List<Employee> employees = employeeRepository.findByPhone(cleanPhone);
        User user = null;
        for (Employee emp : employees) {
            user = userRepository.findByEmployeeId(emp.getId()).orElse(null);
            if (user != null) break;
        }

        if (user == null) {
            user = userRepository.findByUsername(cleanPhone)
                    .orElseThrow(() -> new RuntimeException("No user account found linked to this mobile number"));
        }

        if (!user.getIsActive()) {
            throw new RuntimeException("User account is deactivated");
        }

        otpVerification.setUsed(true);
        otpVerificationRepository.save(otpVerification);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .employeeId(user.getEmployee() != null ? user.getEmployee().getId() : null)
                .employeeName(user.getEmployee() != null ? 
                        user.getEmployee().getFirstName() + " " + user.getEmployee().getLastName() : null)
                .token(token)
                .message("Login successful")
                .build();
    }

    @Transactional
    public String sendOtpForLogin(SendSMSOTPRequest request) {
        String phone = request.getMobileNumber();
        if (phone == null || phone.trim().isEmpty()) {
            throw new RuntimeException("Mobile number is required");
        }
        
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        if (cleanPhone.length() > 10) {
            cleanPhone = cleanPhone.substring(cleanPhone.length() - 10);
        }

        // Check if an active employee exists with this phone number
        List<Employee> employees = employeeRepository.findByPhone(cleanPhone);
        if (employees.isEmpty()) {
            throw new RuntimeException("Mobile number '" + phone + "' is not registered in the system. Please check the number or contact your HR administrator.");
        }

        // Find the first active employee
        Employee employee = employees.stream()
                .filter(e -> e.getStatus() != Employee.EmployeeStatus.TERMINATED && e.getStatus() != Employee.EmployeeStatus.INACTIVE)
                .findFirst()
                .orElse(null);

        if (employee == null) {
            throw new RuntimeException("The account associated with mobile number '" + phone + "' is inactive or terminated. Please contact HR.");
        }

        // Check if user account is linked to this employee
        java.util.Optional<User> userOpt = userRepository.findByEmployeeId(employee.getId());
        if (userOpt.isEmpty()) {
            throw new RuntimeException("No active system user account is linked to the employee profile for '" + phone + "'. Please contact HR.");
        }

        User user = userOpt.get();
        if (!user.getIsActive()) {
            throw new RuntimeException("The user account associated with mobile number '" + phone + "' is deactivated. Please contact HR.");
        }

        // Check for cooldown restriction
        otpVerificationRepository.findFirstByMobileNumberAndUsedFalseOrderByCreatedAtDesc(cleanPhone)
                .ifPresent(existingOtp -> {
                    if (existingOtp.getCreatedAt().plusSeconds(cooldownMs / 1000).isAfter(LocalDateTime.now())) {
                        throw new RuntimeException("Please wait before requesting a new OTP");
                    }
                });

        // Delete old OTPs for this number
        otpVerificationRepository.deleteByMobileNumber(cleanPhone);

        // Generate 6-digit OTP
        String otp = generateOTP();

        // Save new OTP Verification session
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(otpExpiryMinutes);
        OtpVerification otpVerification = new OtpVerification(cleanPhone, otp, expiryTime);
        otpVerificationRepository.save(otpVerification);

        // Dispatch OTP SMS via Fast2SMS
        boolean sent = smsUtil.sendOtpSms(cleanPhone, otp);
        if (!sent) {
            throw new RuntimeException("Failed to send OTP SMS. Please try again later or contact support.");
        }

        return otp;
    }



    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDTO(user);
    }

    public UserDTO updateUserRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(User.Role.valueOf(role));
        user = userRepository.save(user);
        return convertToDTO(user);
    }

    public UserDTO deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(false);
        user = userRepository.save(user);
        return convertToDTO(user);
    }

    public UserDTO activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(true);
        user = userRepository.save(user);
        return convertToDTO(user);
    }

    /**
     * Change password for currently logged-in user
     * Users can only change their own password after verifying current password
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Validate new password and confirm password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New password and confirm password do not match");
        }

        // Validate password strength (minimum 6 characters)
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters long");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Clear security context to force re-login with new password
        SecurityContextHolder.clearContext();
    }

    /**
     * Get current logged-in user profile
     */
    public UserDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return convertToDTO(user);
    }

    /**
     * Forgot Password - Generate and send OTP to user's email
     */
    @Transactional
    public String requestPasswordResetOTP(ForgotPasswordRequest request) {
        String email = request.getEmail();
        String fullName = request.getFullName();

        if (email == null || email.trim().isEmpty()) {
            System.err.println("Email is required");
            throw new RuntimeException("Email is required");
        }

        if (fullName == null || fullName.trim().isEmpty()) {
            throw new RuntimeException("Full name is required");
        }

        email = email.trim().toLowerCase();
        fullName = fullName.trim();

        // Check if user exists with this email (case-insensitive)
        String finalEmail = email;
        String finalFullName = fullName;
        User user = userRepository.findByEmailIgnoreCase(finalEmail)
                .orElseThrow(() -> {
                    System.err.println("No account found with this email address: " + finalEmail);
                    return new RuntimeException("No account found with this email address");
                });

        // Verify that the provided full name matches the registered employee name
        if (user.getEmployee() != null) {
            String empFirstName = user.getEmployee().getFirstName() != null ? user.getEmployee().getFirstName().trim() : "";
            String empLastName = user.getEmployee().getLastName() != null ? user.getEmployee().getLastName().trim() : "";
            String registeredName = (empFirstName + " " + empLastName).trim().toLowerCase();
            if (!registeredName.equals(finalFullName.toLowerCase())) {
                throw new RuntimeException("Name does not match the registered account");
            }
        } else {
            // For system users without an employee record, check username as fallback
            if (!user.getUsername().equalsIgnoreCase(finalFullName) && 
                !(user.getEmail().equalsIgnoreCase(finalFullName))) {
                throw new RuntimeException("Name does not match the registered account");
            }
        }

        // Generate 6-digit OTP
        String otp = generateOTP();

        // Save OTP to database with 10-minute expiry
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(10);

        // Delete any existing OTPs for this email first to avoid duplicate key constraint
        otpRepository.deleteByEmail(finalEmail);

        // Create and save new OTP
        PasswordResetOTP passwordResetOTP = new PasswordResetOTP(finalEmail, otp, now, expiresAt);
        otpRepository.save(passwordResetOTP);

        // Send OTP email
        try {
            sendOTPEmail(finalEmail, otp, user.getUsername());
        } catch (Exception e) {
            System.err.println("⚠️ SMTP email sending failed: " + e.getMessage());
            System.err.println("==================================================");
            System.err.println("  🔑 [DEVELOPMENT OTP BYPASS] CODE: " + otp);
            System.err.println("==================================================");
        }
        return otp;
    }

    /**
     * Verify OTP
     */
    public boolean verifyOTP(VerifyOTPRequest request) {
        String email = request.getEmail();
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        String otp = request.getOtp();

        PasswordResetOTP passwordResetOTP = otpRepository
                .findByEmailAndOtpAndUsedFalseAndExpiresAtAfter(email, otp, LocalDateTime.now())
                .orElse(null);

        if (passwordResetOTP == null) {
            return false;
        }

        if (passwordResetOTP.isExpired()) {
            return false;
        }

        return true;
    }

    /**
     * Reset Password with OTP
     */
    @Transactional
    public void resetPasswordWithOTP(ResetPasswordRequest request) {
        String email = request.getEmail();
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        String otp = request.getOtp();
        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();

        // Validate new password and confirm password match
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("New password and confirm password do not match");
        }

        // Validate password strength (minimum 6 characters)
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters long");
        }

        // Verify OTP
        PasswordResetOTP passwordResetOTP = otpRepository
                .findByEmailAndOtpAndUsedFalseAndExpiresAtAfter(email, otp, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("Invalid or expired OTP"));

        if (passwordResetOTP.isExpired()) {
            throw new RuntimeException("OTP has expired");
        }

        // Get user and update password
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark OTP as used
        passwordResetOTP.setUsed(true);
        otpRepository.save(passwordResetOTP);
    }

    /**
     * Generate 6-digit OTP
     */
    private String generateOTP() {
        SecureRandom random = new SecureRandom();
        int otpNumber = 100000 + random.nextInt(900000);
        return String.valueOf(otpNumber);
    }

    /**
     * Send OTP email
     */
    private void sendOTPEmail(String email, String otp, String username) {
        try {
            String subject = "Password Reset OTP - HRMS System";
            String htmlBody = String.format(
                "<html><body>" +
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #4f46e5;'>🔐 Password Reset OTP</h2>" +
                "<p>Dear %s,</p>" +
                "<p>We received a request to reset your password. Use the following OTP to proceed:</p>" +
                "<div style='background: #f3f4f6; padding: 30px; border-radius: 8px; margin: 20px 0; text-align: center;'>" +
                "<h1 style='color: #4f46e5; font-size: 48px; margin: 0;'>%s</h1>" +
                "</div>" +
                "<p><strong>This OTP is valid for 10 minutes.</strong></p>" +
                "<p>If you did not request this, please ignore this email and your password will remain unchanged.</p>" +
                "<p style='color: #6b7280; font-size: 14px;'>This is an automated email. Please do not reply.</p>" +
                "</div>" +
                "</body></html>",
                username,
                otp
            );

            emailUtil.sendHtmlEmail(email, subject, htmlBody);
        } catch (Exception e) {
            System.err.println("Error sending OTP email: " + e.getMessage());
            throw new RuntimeException("Failed to send OTP email. Please try again.");
        }
    }

    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(getEffectiveRole(user))
                .employeeId(user.getEmployee() != null ? user.getEmployee().getId() : null)
                .employeeName(user.getEmployee() != null ? 
                        user.getEmployee().getFirstName() + " " + user.getEmployee().getLastName() : null)
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }

    private String getEffectiveRole(User user) {
        String roleName = user.getRole().name();
        if (user.getRole() == User.Role.ROLE_ADMIN || user.getRole() == User.Role.ROLE_HR) {
            roleName = "ROLE_HR";
        }
        
        if (user.getRole() != User.Role.ROLE_ADMIN && user.getRole() != User.Role.ROLE_HR) {
            if (user.getEmployee() != null && user.getEmployee().getDepartment() != null) {
                String deptName = user.getEmployee().getDepartment().getName().toLowerCase();
                
                if (deptName.contains("accountant")) {
                    roleName = "ROLE_ACCOUNTANT";
                } else if (deptName.contains("director")) {
                    roleName = "ROLE_DIRECTOR";
                } else if (deptName.contains("leave") || deptName.equals("leaves")) {
                    roleName = "ROLE_LEAVES";
                } else if (deptName.contains("hr") || deptName.equals("human resources")) {
                    roleName = "ROLE_HR";
                } else {
                    roleName = "ROLE_EMPLOYEE";
                }
            }
        }
        return roleName;
    }
}
