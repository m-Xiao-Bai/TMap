package com.mu.transitmap.enums;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ===== 通用 =====
    SUCCESS(200, HttpStatus.OK, "成功"),
    PARAM_ERROR(400, HttpStatus.BAD_REQUEST, "参数错误"),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED, "未登录"),
    FORBIDDEN(403, HttpStatus.FORBIDDEN, "无权限"),
    NOT_FOUND(404, HttpStatus.NOT_FOUND, "资源不存在"),
    SYSTEM_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR, "系统异常"),
    METHOD_NOT_ALLOWED(405, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    BAD_REQUEST(400, HttpStatus.BAD_REQUEST, "请求格式错误"),

    // ===== 客户端请求错误 =====
    PARAM_MISSING(40001,HttpStatus.OK, "请求参数缺失"),
    PARAM_INVALID(40002,HttpStatus.OK, "请求参数不合法"),
    PARAM_FORMAT_ERROR(40003, HttpStatus.OK,"参数格式错误"),

    // ===== 业务异常 =====
    DATA_NOT_EXIST(10001, HttpStatus.OK, "数据不存在"),
    STATUS_NOT_ALLOWED(10002, HttpStatus.OK, "当前状态不允许操作"),
    BUSINESS_ERROR(10003, HttpStatus.OK, "业务异常"),
    VERIFICATION_CODE_INCORRECT(102001,HttpStatus.OK, "验证码错误"),
    USERNAME_ALREADY_EXISTS(102010, HttpStatus.OK,"用户名已经被使用"),
    MOBILE_ALREADY_BIND(102011, HttpStatus.OK,"手机号已被绑定"),
    EMAIL_ALREADY_BIND(102012, HttpStatus.OK,"邮箱号已被绑定"),
    USER_REGISTER_FAILED(102013,HttpStatus.OK, "注册失败"),
    CAPTCHA_ERROR(102014, HttpStatus.OK,"验证码错误"),
    USER_NOT_FOUND_AND_PASSWORD_ERROR(102015, HttpStatus.OK,"账号错误或密码错误"),
    CITY_NULL_ID(102017,HttpStatus.OK, "城市不存在，id="),
    ACCOUNT_DISABLED(102016, HttpStatus.OK,"账号已被禁用"),
    ACCOUNT_LOCKED(102026, HttpStatus.OK, "登录失败次数过多，账号已临时锁定，请稍后再试"),
    TOKEN_INVALID(102018, HttpStatus.UNAUTHORIZED, "Token无效或已过期"),
    TOKEN_MISSING(102019, HttpStatus.UNAUTHORIZED, "缺少Token"),
    ROLE_NOT_MATCH(102020, HttpStatus.OK, "角色权限不匹配"),
    CANNOT_MODIFY_SELF_STATUS(102021, HttpStatus.OK, "不能修改自己的状态"),
    CANNOT_DELETE_USER(102022, HttpStatus.OK, "没有权限删除该用户"),
    CANNOT_CREATE_USER_WITH_ROLE(102023, HttpStatus.OK, "没有权限创建该角色用户"),
    CANNOT_MODIFY_USER(102024, HttpStatus.OK, "没有权限修改该用户"),
    PASSWORD_CANNOT_BE_BLANK(102025, HttpStatus.OK, "密码不能为空白字符"),
    REDIS_UNAVAILABLE(15003, HttpStatus.OK,"验证码服务暂不可用，请稍后再试"),
    EMAIL_SEND_FAILED(15004, HttpStatus.OK, "邮件发送失败，请稍后再试"),
    OPERATION_FAILED(15005, HttpStatus.OK, "操作失败"),
    COUNTRY_NOT_FOUND(15006, HttpStatus.OK, "国家不存在"),
    COUNTRY_NAME_ALREADY_EXISTS(15007, HttpStatus.OK, "国家名称已存在"),
    CANNOT_MODIFY_COUNTRY_NAME(15008, HttpStatus.OK, "没有权限修改国家名称"),
    CANNOT_DELETE_COUNTRY(15009, HttpStatus.OK, "没有权限删除国家"),
    COUNTRY_IMPORT_FAILED(15010, HttpStatus.OK, "国家批量导入失败"),
    FILE_FORMAT_ERROR(15011, HttpStatus.OK, "文件格式不支持，仅支持JSON和XLS格式"),
    CITY_NOT_FOUND(15012, HttpStatus.OK, "城市不存在"),
    CITY_NAME_ALREADY_EXISTS(15013, HttpStatus.OK, "城市名称已存在"),
    CANNOT_MODIFY_CITY_NAME(15014, HttpStatus.OK, "没有权限修改城市名称"),
    CITY_IMPORT_FAILED(15015, HttpStatus.OK, "城市批量导入失败"),
    CITY_WRONG_COUNTRY(15016, HttpStatus.OK, "所属国家不存在"),
    METRO_LINE_NOT_FOUND(15017, HttpStatus.OK, "地铁线路不存在"),
    METRO_LINE_ALREADY_EXISTS(15018, HttpStatus.OK, "该城市下线路编号已存在"),
    METRO_LINE_CANNOT_MODIFY_NAME(15019, HttpStatus.OK, "没有权限修改线路名称"),
    METRO_LINE_IMPORT_FAILED(15020, HttpStatus.OK, "地铁线路批量导入失败"),
    METRO_LINE_WRONG_COUNTRY(15021, HttpStatus.OK, "所属国家不存在"),
    METRO_LINE_WRONG_CITY(15022, HttpStatus.OK, "所属城市不存在"),
    METRO_LINE_PERMISSION_DENIED(15023, HttpStatus.OK, "没有权限执行此操作"),
    METRO_LINE_DATE_FORMAT_ERROR(15024, HttpStatus.OK, "日期/时间格式错误"),
    METRO_STATION_NOT_FOUND(15025, HttpStatus.OK, "地铁站不存在"),
    METRO_STATION_WRONG_COUNTRY(15026, HttpStatus.OK, "所属国家不存在"),
    METRO_STATION_WRONG_CITY(15027, HttpStatus.OK, "所属城市不存在或不属于所选国家"),
    METRO_STATION_CANNOT_MODIFY_NAME(15028, HttpStatus.OK, "没有权限修改站名"),
    METRO_STATION_PERMISSION_DENIED(15029, HttpStatus.OK, "没有权限执行此操作"),
    METRO_STATION_IMPORT_FAILED(15030, HttpStatus.OK, "地铁站批量导入失败"),
    METRO_STATION_NAME_ALREADY_EXISTS(15031, HttpStatus.OK, "该城市下站点名称已存在"),
    CANNOT_DELETE_CITY(15032, HttpStatus.OK, "没有权限删除城市"),
    COUNTRY_HAS_CITIES(15033, HttpStatus.OK, "该国家下存在城市，无法删除"),
    CITY_HAS_METRO_LINES(15034, HttpStatus.OK, "该城市下存在地铁线路，无法删除"),
    CITY_HAS_METRO_STATIONS(15035, HttpStatus.OK, "该城市下存在地铁站点，无法删除"),
    METRO_LINE_HAS_STATIONS(15036, HttpStatus.OK, "该线路下存在站点，无法删除"),
    METRO_STATION_HAS_ORDERS(15037, HttpStatus.OK, "该站点存在关联订单，无法删除"),

    // ===== 购票模块 =====
    TICKET_ORDER_NOT_FOUND(16001, HttpStatus.OK, "订单不存在"),
    TICKET_ORDER_STATUS_ERROR(16002, HttpStatus.OK, "订单状态不允许此操作"),
    TICKET_STATION_SAME(16005, HttpStatus.OK, "起始站和终点站不能相同"),
    TICKET_ROUTE_NOT_FOUND(16006, HttpStatus.OK, "未找到可用路线"),
    TICKET_QR_EXPIRED(16007, HttpStatus.OK, "二维码已过期"),
    TICKET_QR_USED(16008, HttpStatus.OK, "该票已使用"),
    TICKET_QR_INVALID(16009, HttpStatus.OK, "二维码无效"),
    TICKET_REFUND_PENDING(16010, HttpStatus.OK, "退票申请已提交，等待审核"),
    TICKET_REFUND_REJECTED(16011, HttpStatus.OK, "退票申请已被拒绝"),

    // ===== 微信登录 =====
    WECHAT_LOGIN_FAILED(17001, HttpStatus.OK, "微信登录失败，请稍后再试");


    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
