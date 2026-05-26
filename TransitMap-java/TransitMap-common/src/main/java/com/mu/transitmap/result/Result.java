package com.mu.transitmap.result;

import com.mu.transitmap.enums.DatePattern;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.utils.DateFormatUtil;

import java.time.LocalDateTime;

public class Result<T>  {

    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private String time;

    public Result() {
        LocalDateTime now = LocalDateTime.now();
        this.timestamp = now;
        this.time=DateFormatUtil.format(now, DatePattern.YEAR_MONTH_DAY_SECOND, DateFormatUtil.DOT);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
