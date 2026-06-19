package com.hrm.hrmsystem.dto;

import lombok.Data;

@Data
public class VerifySMSOTPRequest {
    private String mobileNumber;
    private String otp;
}
