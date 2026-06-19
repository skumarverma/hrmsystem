package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.dto.*;
import com.hrm.hrmsystem.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.hrmsystem.repository.EmployeeRepository employeeRepository;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.hrmsystem.repository.LeaveRepository leaveRepository;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.hrmsystem.repository.AttendanceRepository attendanceRepository;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.hrmsystem.engine.AttendanceEngine attendanceEngine;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/debug-employees")
    public ResponseEntity<?> debugEmployees() {
        try {
            List<com.hrm.hrmsystem.model.Employee> employees = employeeRepository.findAll();
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (com.hrm.hrmsystem.model.Employee emp : employees) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", emp.getId());
                map.put("name", emp.getFirstName() + " " + emp.getLastName());
                map.put("joiningDate", emp.getJoiningDate() != null ? emp.getJoiningDate().toString() : null);
                map.put("probationStatus", emp.getProbationStatus() != null ? emp.getProbationStatus().name() : null);
                map.put("probationMonths", emp.getProbationPeriodMonths());

                List<Map<String, Object>> leavesList = new java.util.ArrayList<>();
                for (com.hrm.hrmsystem.model.Leave l : leaveRepository.findByEmployeeId(emp.getId())) {
                    Map<String, Object> lm = new HashMap<>();
                    lm.put("id", l.getId());
                    lm.put("status", l.getStatus() != null ? l.getStatus().name() : null);
                    lm.put("startDate", l.getStartDate() != null ? l.getStartDate().toString() : null);
                    lm.put("endDate", l.getEndDate() != null ? l.getEndDate().toString() : null);
                    lm.put("totalDays", l.getTotalDays());
                    lm.put("paidDays", l.getPaidDays());
                    lm.put("unpaidDays", l.getUnpaidDays());
                    leavesList.add(lm);
                }
                map.put("leaves", leavesList);

                List<Map<String, Object>> attsList = new java.util.ArrayList<>();
                for (com.hrm.hrmsystem.model.Attendance a : attendanceRepository.findByEmployeeId(emp.getId())) {
                    Map<String, Object> am = new HashMap<>();
                    am.put("date", a.getDate() != null ? a.getDate().toString() : null);
                    am.put("status", a.getStatus() != null ? a.getStatus().name() : null);
                    am.put("halfType", a.getHalfType() != null ? a.getHalfType().name() : null);
                    attsList.add(am);
                }
                map.put("attendances", attsList);

                try {
                    java.time.YearMonth ym = java.time.YearMonth.of(2026, 6);
                    com.hrm.hrmsystem.engine.AttendanceSummary attSummary = attendanceEngine.calculate(emp.getId(), ym);
                    Map<String, Object> sumMap = new HashMap<>();
                    sumMap.put("workedDays", attSummary.workedDays);
                    sumMap.put("paidLeave", attSummary.paidLeave);
                    sumMap.put("unpaidLeave", attSummary.unpaidLeave);
                    sumMap.put("absent", attSummary.absent);
                    sumMap.put("paidAbsent", attSummary.paidAbsent);
                    sumMap.put("unpaidAbsent", attSummary.unpaidAbsent);
                    sumMap.put("payableDays", attSummary.payableDays);
                    map.put("summaryJune2026", sumMap);
                } catch (Exception e) {
                    map.put("summaryJune2026Error", e.getMessage());
                }

                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(authService.register(request));
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Registration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody SendSMSOTPRequest request) {
        try {
            String otp = authService.sendOtpForLogin(request);
            Map<String, String> success = new HashMap<>();
            success.put("message", "OTP sent to your mobile number successfully");
            return ResponseEntity.ok(success);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to send OTP: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifySMSOTPRequest request) {
        try {
            return ResponseEntity.ok(authService.verifyOtpAndLogin(request));
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Login failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(authService.getUserById(id));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserDTO> updateUserRole(
            @PathVariable Long id,
            @RequestParam String role) {
        return ResponseEntity.ok(authService.updateUserRole(id, role));
    }

    @PostMapping("/users/{id}/deactivate")
    public ResponseEntity<UserDTO> deactivateUser(@PathVariable Long id) {
        return ResponseEntity.ok(authService.deactivateUser(id));
    }

    @PostMapping("/users/{id}/activate")
    public ResponseEntity<UserDTO> activateUser(@PathVariable Long id) {
        return ResponseEntity.ok(authService.activateUser(id));
    }

    /**
     * Change password for currently logged-in user
     * Employees can only change their own password
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            authService.changePassword(request);
            Map<String, String> success = new HashMap<>();
            success.put("message", "Password changed successfully");
            return ResponseEntity.ok(success);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Password change failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get current user profile
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            return ResponseEntity.ok(authService.getCurrentUser());
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get user: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Forgot Password - Request OTP
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            String otp = authService.requestPasswordResetOTP(request);
            Map<String, String> success = new HashMap<>();
            success.put("message", "OTP sent to your email successfully");
            // Always include OTP in response so UI can show it if email delivery fails
            if (otp != null) {
                success.put("devOtp", otp);
            }
            return ResponseEntity.ok(success);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to send OTP: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Verify Password Reset OTP
     */
    @PostMapping("/verify-reset-otp")
    public ResponseEntity<?> verifyOTP(@RequestBody VerifyOTPRequest request) {
        try {
            boolean isValid = authService.verifyOTP(request);
            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("message", isValid ? "OTP verified successfully" : "Invalid or expired OTP");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to verify OTP: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Reset Password with OTP
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPasswordWithOTP(request);
            Map<String, String> success = new HashMap<>();
            success.put("message", "Password reset successfully. You can now login with your new password.");
            return ResponseEntity.ok(success);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to reset password: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
