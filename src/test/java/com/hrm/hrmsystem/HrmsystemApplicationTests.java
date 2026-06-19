package com.hrm.hrmsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.beans.factory.annotation.Autowired;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.service.PayrollService;
import com.hrm.hrmsystem.repository.UserRepository;

@SpringBootTest
class HrmsystemApplicationTests {

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private com.hrm.hrmsystem.repository.PayrollRepository payrollRepository;

	@Autowired
	private PayrollService payrollService;

	@Test
	void contextLoads() {
		System.out.println("PAYROLL_DEBUG_START");
		payrollRepository.findAll().forEach(p -> {
			System.out.println("PAYROLL_RECORD: ID=" + p.getId() + " | Employee=" + p.getEmployee().getFirstName() + " | Month=" + p.getMonth() + "/" + p.getYear() + " | Status=" + p.getStatus());
		});
		
		// Attempt to unlock a record if exists
		payrollRepository.findAll().stream().findFirst().ifPresent(p -> {
			System.out.println("ATTEMPTING UNLOCK ON ID: " + p.getId());
			try {
				com.hrm.hrmsystem.dto.PayrollDTO unlocked = payrollService.unlockPayroll(p.getId());
				System.out.println("UNLOCK_SUCCESS: ID=" + unlocked.getId() + " | Status=" + unlocked.getStatus());
			} catch (Exception e) {
				System.out.println("UNLOCK_FAILED: " + e.getMessage());
			}
		});
		System.out.println("PAYROLL_DEBUG_END");
	}

}
