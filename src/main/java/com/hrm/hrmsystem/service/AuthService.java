package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.config.JwtUtil;
import com.hrm.hrmsystem.dto.*;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.model.OtpVerification;
import com.hrm.hrmsystem.repository.EmployeeRepository;
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
                       EmailUtil emailUtil,
                       OtpVerificationRepository otpVerificationRepository, SmsUtil smsUtil) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
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
        if (user.getRole() == User.Role.ROLE_ADMIN || user.getRole() == User.Role.ROLE_HR) {
            return "ROLE_HR";
        }

        if (user.getEmployee() != null && user.getEmployee().getDepartment() != null) {
            String deptName = user.getEmployee().getDepartment().getName().toLowerCase();
            
            if (deptName.contains("accountant")) {
                return "ROLE_ACCOUNTANT";
            } else if (deptName.contains("director")) {
                return "ROLE_DIRECTOR";
            } else if (deptName.contains("leave") || deptName.equals("leaves")) {
                return "ROLE_LEAVES";
            } else if (deptName.contains("hr") || deptName.equals("human resources")) {
                return "ROLE_HR";
            }
        }
        
        return "ROLE_EMPLOYEE";
    }

    /**
     * Generate 6-digit OTP
     */
    private String generateOTP() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        int otpNumber = 100000 + random.nextInt(900000);
        return String.valueOf(otpNumber);
    }
}
