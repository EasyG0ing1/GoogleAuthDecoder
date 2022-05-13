package com.simtechdata.googleauthdecoder;

public record OTPRecord(String otpAuthString, String otpType, String otpName, String otpParams, String algorithm, String digits, String issuer, String secret) {

}
