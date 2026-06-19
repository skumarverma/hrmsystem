package com.hrm.hrmsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configure(http))
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Public pages
                .requestMatchers("/", "/index.html", "/login", "/register").permitAll()
                .requestMatchers("/html/login.html", "/html/register.html", "/html/home.html").permitAll()
                .requestMatchers("/static/**", "/images/**", "/assets/**", "/css/**", "/js/**", "/html/modules/**", "/vite.svg", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/files/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/documents/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/documents/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/documents/**").hasAnyRole("HR", "ADMIN")
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // === READ-ONLY APIs - All authenticated users can view ===
                .requestMatchers(HttpMethod.GET, "/api/employees/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/departments/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/attendance/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/leaves/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/payroll/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/payslips/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/dashboard/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/users/profile").authenticated()
                
                // === WRITE APIs - Restricted by role ===
                // Employee management (create/update/delete) - HR and Admin
                .requestMatchers(HttpMethod.POST, "/api/employees/**").hasAnyRole("HR", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/employees/**").hasAnyRole("HR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/employees/**").hasAnyRole("HR", "ADMIN")
                
                // Department management - HR and Admin
                .requestMatchers(HttpMethod.POST, "/api/departments/**").hasAnyRole("HR", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/departments/**").hasAnyRole("HR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/departments/**").hasAnyRole("HR", "ADMIN")
                
                // Attendance management (mark attendance) - HR/Manager/Admin
                .requestMatchers(HttpMethod.POST, "/api/attendance/**").hasAnyRole("HR", "MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/attendance/**").hasAnyRole("HR", "MANAGER", "ADMIN")
                
                // Leave management (approve/reject/modify) - HR/Leaves/Accountant/Admin
                .requestMatchers(HttpMethod.POST, "/api/leaves/apply").authenticated() // Employees can apply
                .requestMatchers(HttpMethod.POST, "/api/leaves/approve/**", "/api/leaves/reject/**", "/api/leaves/cancel/**", "/api/leaves/modify/**").hasAnyRole("HR", "LEAVES", "ACCOUNTANT", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/leaves/recalculate-paid-days").hasAnyRole("HR", "LEAVES", "ACCOUNTANT", "ADMIN") // Data fix endpoint
                
                // Payroll management - HR/Accountant/Admin
                .requestMatchers(HttpMethod.POST, "/api/payroll/unlock/**").hasAnyRole("HR", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/payroll/**").hasAnyRole("HR", "ACCOUNTANT", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/payroll/**").hasAnyRole("HR", "ACCOUNTANT", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/payroll/**").hasAnyRole("HR", "ADMIN")
                
                // Payslip management - HR/Accountant/Admin
                .requestMatchers(HttpMethod.POST, "/api/payslips/**").hasAnyRole("HR", "ACCOUNTANT", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/payslips/**").hasAnyRole("HR", "ACCOUNTANT", "ADMIN")
                
                // Accountant approvals
                .requestMatchers("/api/payroll/approvals/pending/accountant", "/api/payroll/approvals/*/accountant-approve", "/api/payroll/approvals/*/accountant-reject").hasRole("ACCOUNTANT")
                
                // Director approvals
                .requestMatchers("/api/payroll/approvals/pending/director", "/api/payroll/approvals/*/director-approve", "/api/payroll/approvals/*/director-reject").hasRole("DIRECTOR")
                
                // User profile management
                .requestMatchers(HttpMethod.GET, "/api/users/profile").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/users/update-email").authenticated()
                
                // HR only APIs (previously Admin) - HR and Admin
                .requestMatchers("/api/admin/**", "/api/users/**").hasAnyRole("HR", "ADMIN")
                
                // All HTML pages are public - frontend JS handles role checks
                .requestMatchers("/html/**").permitAll()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
