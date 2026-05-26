package com.mu.transitmap.vo;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UserLoginVO {

    private Long id;

    private String username;

    private String avatar;

    private String email;

    private String mobile;

    private Integer genderCode;

    private LocalDate birthday;

    private Integer roleCode;

    private String role;

    private Integer statusCode;

    private String status;

    private String token;
}
