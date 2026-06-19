package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        }

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

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(roleName));
        if (roleName.equals("ROLE_HR")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            user.getIsActive(),
            true,
            true,
            true,
            authorities
        );
    }
}
