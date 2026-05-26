package com.mu.transitmap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户信息表
 * </p>
 *
 * @author muxiaobai
 * @since 2026-01-21
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一ID（雪花算法）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 国家唯一ID
     */
    private Long countryId;

    /**
     * 国家名称（中文或英文）
     */
    private String countryName;

    /**
     * 用户名称
     */
    private String username;

    /**
     * 用户头像URL
     */
    private String avatar;

    /**
     * 用户性别码（0未知 1男 2女）
     */
    private Integer genderCode;

    /**
     * 用户性别（未知/男/女）
     */
    private String gender;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 用户手机号
     */
    private String mobile;

    /**
     * 用户密码（加密存储）
     */
    private String password;

    /**
     * 用户微信ID
     */
    private String wechatId;

    /**
     * 用户QQ ID
     */
    private String qqId;

    /**
     * 用户出生日期
     */
    private LocalDate birthday;

    /**
     * 用户年龄（冗余字段，便于查询）
     */
    private Integer age;

    /**
     * 用户所在城市唯一ID
     */
    private Long cityId;

    /**
     * 用户所在城市名称（冗余）
     */
    private String cityName;

    /**
     * 用户状态码（0异常 1在线 2下线 3禁用）
     */
    private Integer statusCode;

    /**
     * 用户状态（异常/在线/下线/禁用）
     */
    private String status;

    /**
     * 用户权限码（1普通用户 2管理员用户 3超级管理员 4最高级管理员）
     */
    private Integer roleCode;

    /**
     * 用户权限（普通用户/管理员用户/超级管理员/最高级管理员）
     */
    private String role;

    /**
     * 扩展字段（JSON）
     */
    private String extra;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", countryId=" + countryId +
                ", countryName='" + countryName + '\'' +
                ", username='" + username + '\'' +
                ", avatar='" + avatar + '\'' +
                ", genderCode=" + genderCode +
                ", gender='" + gender + '\'' +
                ", email='" + email + '\'' +
                ", mobile='" + mobile + '\'' +
                ", password='" + password + '\'' +
                ", wechatId='" + wechatId + '\'' +
                ", qqId='" + qqId + '\'' +
                ", birthday=" + birthday +
                ", age=" + age +
                ", cityId=" + cityId +
                ", cityName='" + cityName + '\'' +
                ", statusCode=" + statusCode +
                ", status='" + status + '\'' +
                ", roleCode=" + roleCode +
                ", role='" + role + '\'' +
                ", extra='" + extra + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
