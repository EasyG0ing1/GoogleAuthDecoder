package com.simtechdata.googleauthdecoder;

import java.io.File;
import java.util.List;

public class TestMain {

	public static void main(String[] args) {
		File file = new File("/Users/michael/Documents/QRImages/FromGoogleApp/2022/Everything.png");
		Decoder decoder = new Decoder(file);
		decoder.decode();
		List<OTPRecord> otpRecords = decoder.getRecords();
		List<String> lineList = decoder.getRawOTP();
		StringBuilder sb = new StringBuilder();
		for(OTPRecord record : otpRecords) {
			sb.append("otpAuthString: ").append(record.otpAuthString()).append("\n");
			sb.append("otpType: ").append(record.otpType()).append("\n");
			sb.append("otpName: ").append(record.otpName()).append("\n");
			sb.append("otpParams: ").append(record.otpParams()).append("\n");
			sb.append("algorithm: ").append(record.algorithm()).append("\n");
			sb.append("digits: ").append(record.digits()).append("\n");
			sb.append("issuer: ").append(record.issuer()).append("\n");
			sb.append("secret: ").append(record.secret()).append("\n\n");
		}
		System.out.println(sb);
		for(String line : lineList) {
			System.out.println(line);
		}
		System.exit(0);
	}
}
