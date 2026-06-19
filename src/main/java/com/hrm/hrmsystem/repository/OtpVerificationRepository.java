package com.hrm.hrmsystem.repository;

import com.hrm.hrmsystem.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findFirstByMobileNumberAndUsedFalseOrderByCreatedAtDesc(String mobileNumber);

    void deleteByMobileNumber(String mobileNumber);
}
