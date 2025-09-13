package org.example.easychat.utils;

import java.util.regex.Pattern;

public class ValidationUtils {

    /**
     * 校验昵称和用户名：1-11位，可以是任意字符
     */
    public static boolean isValidName(String nickName) {
        return nickName == null || nickName.isEmpty() || nickName.length() > 11;
    }

    /**
     * 校验密码：8-16位，必须包含字母、数字、特殊字符中至少两种
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 16) {
            return false;
        }
        boolean hasLetter = password.matches(".*[A-Za-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[^A-Za-z0-9].*");

        return (hasLetter && hasDigit) || (hasLetter && hasSpecial) || (hasDigit && hasSpecial);
    }

    /**
     * 校验邮箱：标准邮箱格式
     */
    public static boolean isValidEmail(String email) {
        return Pattern.matches("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$", email);
    }

    /**
     * 校验验证码：6位数字
     */
    public static boolean isValidVerifyCode(String code) {
        return Pattern.matches("^\\d{6}$", code);
    }
}
