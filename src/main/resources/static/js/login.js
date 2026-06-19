// Mobile OTP Login functionality
const loginForm = document.getElementById('login-form');
const mobileNumberInput = document.getElementById('mobileNumber');
const otpGroup = document.getElementById('otp-group');
const otpInput = document.getElementById('otp');
const sendOtpBtn = document.getElementById('send-otp-btn');
const sendBtnText = document.getElementById('send-btn-text');
const sendBtnLoading = document.getElementById('send-btn-loading');
const submitBtn = document.getElementById('submit-btn');
const btnText = document.getElementById('btn-text');
const btnLoading = document.getElementById('btn-loading');
const errorMessage = document.getElementById('error-message');
const otpTimerSpan = document.getElementById('otp-timer');
const resendLink = document.getElementById('resend-link');
const backToMobileBtn = document.getElementById('back-to-mobile');

let timerInterval = null;
let cooldownInterval = null;
let canResend = false;

if (backToMobileBtn) {
    backToMobileBtn.addEventListener('click', () => {
        otpGroup.style.display = 'none';
        submitBtn.style.display = 'none';
        sendOtpBtn.style.display = 'block';
        mobileNumberInput.readOnly = false;
        otpInput.value = '';
        hideError();
        clearInterval(timerInterval);
        clearInterval(cooldownInterval);
    });
}

// Handle Send OTP request
sendOtpBtn.addEventListener('click', async () => {
    const mobileNumber = mobileNumberInput.value.trim();
    
    // Simple validation for 10-digit mobile number
    if (!/^\d{10}$/.test(mobileNumber)) {
        showError('Please enter a valid 10-digit mobile number.');
        return;
    }

    hideError();
    setSendLoading(true);

    try {
        const response = await fetch('/api/auth/send-otp', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ mobileNumber })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'Failed to send OTP. Please check the number.');
        }

        // Success: show OTP input block
        otpGroup.style.display = 'block';
        submitBtn.style.display = 'block';
        sendOtpBtn.style.display = 'none';
        mobileNumberInput.readOnly = true;

        // Start OTP Expiry Timer (2 minutes)
        startExpiryTimer(120);

        // Start Resend Cooldown (60 seconds)
        startCooldownTimer(60);

        alert(data.message || 'OTP sent successfully!');

    } catch (err) {
        showError(err.message || 'Error occurred while sending OTP. Please try again.');
    } finally {
        setSendLoading(false);
    }
});

// Handle Resend OTP click
resendLink.addEventListener('click', async () => {
    if (!canResend) return;
    
    const mobileNumber = mobileNumberInput.value.trim();
    hideError();
    resendLink.style.display = 'none';
    canResend = false;

    try {
        const response = await fetch('/api/auth/send-otp', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ mobileNumber })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'Failed to resend OTP.');
        }

        alert('OTP resent successfully!');
        if (data.devOtp) {
            console.log('📢 [DEVELOPMENT OTP BYPASS] OTP Code: ' + data.devOtp);
            otpInput.value = data.devOtp;
        }

        startExpiryTimer(120);
        startCooldownTimer(60);

    } catch (err) {
        showError(err.message || 'Error resending OTP.');
        resendLink.style.display = 'block';
        canResend = true;
    }
});

// Handle Form submit (Verify & Sign In)
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const mobileNumber = mobileNumberInput.value.trim();
    const otp = otpInput.value.trim();

    if (!otp || otp.length !== 6) {
        showError('Please enter the 6-digit OTP code.');
        return;
    }

    hideError();
    setVerifyLoading(true);

    try {
        const response = await fetch('/api/auth/verify-otp', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ mobileNumber, otp })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'Verification failed. Invalid OTP.');
        }

        // Store JWT token and user info
        setAuth(data.token, {
            id: data.id,
            username: data.username,
            email: data.email,
            role: data.role,
            employeeId: data.employeeId,
            employeeName: data.employeeName
        });

        // Clear timers
        clearInterval(timerInterval);
        clearInterval(cooldownInterval);

        // Redirect to dashboard
        window.location.href = '/html/dashboard.html';

    } catch (err) {
        showError(err.message || 'Invalid or expired OTP. Please try again.');
    } finally {
        setVerifyLoading(false);
    }
});

// Start OTP Expiry countdown
function startExpiryTimer(seconds) {
    clearInterval(timerInterval);
    let timeRemaining = seconds;
    
    otpTimerSpan.style.color = '#4f46e5';
    otpTimerSpan.textContent = `OTP expires in ${formatTime(timeRemaining)}`;

    timerInterval = setInterval(() => {
        timeRemaining--;
        if (timeRemaining <= 0) {
            clearInterval(timerInterval);
            otpTimerSpan.textContent = 'OTP has expired';
            otpTimerSpan.style.color = '#dc2626';
        } else {
            otpTimerSpan.textContent = `OTP expires in ${formatTime(timeRemaining)}`;
        }
    }, 1000);
}

// Start Resend Cooldown countdown
function startCooldownTimer(seconds) {
    clearInterval(cooldownInterval);
    let cooldownRemaining = seconds;
    resendLink.style.display = 'none';
    canResend = false;

    cooldownInterval = setInterval(() => {
        cooldownRemaining--;
        if (cooldownRemaining <= 0) {
            clearInterval(cooldownInterval);
            resendLink.style.display = 'block';
            canResend = true;
        }
    }, 1000);
}

function formatTime(seconds) {
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes}:${secs < 10 ? '0' : ''}${secs}`;
}

// Loading indicators
function setSendLoading(loading) {
    sendOtpBtn.disabled = loading;
    if (loading) {
        sendBtnText.style.display = 'none';
        sendBtnLoading.style.display = 'flex';
    } else {
        sendBtnText.style.display = 'block';
        sendBtnLoading.style.display = 'none';
    }
}

function setVerifyLoading(loading) {
    submitBtn.disabled = loading;
    if (loading) {
        btnText.style.display = 'none';
        btnLoading.style.display = 'flex';
    } else {
        btnText.style.display = 'block';
        btnLoading.style.display = 'none';
    }
}

// Show/Hide Error
function showError(message) {
    errorMessage.textContent = message;
    errorMessage.style.display = 'block';
}

function hideError() {
    errorMessage.style.display = 'none';
    errorMessage.textContent = '';
}

// Authentication LocalStorage Helpers
function setAuth(token, userData) {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(userData));
}

function getAuth() {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user');
    return {
        token,
        user: user ? JSON.parse(user) : null
    };
}

function isAuthenticated() {
    const { token } = getAuth();
    return !!token;
}

// Check redirect if already authenticated
if (isAuthenticated()) {
    window.location.href = '/html/dashboard.html';
}

// Export auth helper functions
window.auth = {
    setAuth,
    getAuth,
    isAuthenticated
};
