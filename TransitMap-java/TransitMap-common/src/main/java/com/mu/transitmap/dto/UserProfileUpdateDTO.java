package com.mu.transitmap.dto;

import jakarta.validation.constraints.Email;
import java.time.LocalDate;

public class UserProfileUpdateDTO {

    private String username;

    @Email
    private String email;

    private String mobile;

    private LocalDate birthday;

    private Integer genderCode;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public Integer getGenderCode() { return genderCode; }
    public void setGenderCode(Integer genderCode) { this.genderCode = genderCode; }
}
