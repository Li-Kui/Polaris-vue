package com.polaris.common.core.domain;



import com.polaris.common.constant.HttpStatus;

import java.io.Serializable;

/**
 * 响应信息主体
 *
 * @author LK
 */
public class ResultData<T> implements Serializable {
    /**
     * 成功
     */
    public static final int SUCCESS = HttpStatus.SUCCESS;
    /**
     * 失败
     */
    public static final int FAIL = HttpStatus.ERROR;
    private static final long serialVersionUID = 1L;
    private int code;

    private String msg;

    private T data;

    public static <T> ResultData<T> ok() {
        return restResult(null, SUCCESS, "操作成功");
    }

    public static <T> ResultData<T> ok(T data) {
        return restResult(data, SUCCESS, "操作成功");
    }

    public static <T> ResultData<T> ok(T data, String msg) {
        return restResult(data, SUCCESS, msg);
    }

    public static <T> ResultData<T> fail() {
        return restResult(null, FAIL, "操作失败");
    }

    public static <T> ResultData<T> fail(String msg) {
        return restResult(null, FAIL, msg);
    }

    public static <T> ResultData<T> fail(T data) {
        return restResult(data, FAIL, "操作失败");
    }

    public static <T> ResultData<T> fail(T data, String msg) {
        return restResult(data, FAIL, msg);
    }

    public static <T> ResultData<T> fail(int code, String msg) {
        return restResult(null, code, msg);
    }

    public static <T> ResultData<T> fail(T data, int code, String msg) {
        return restResult(data, code, msg);
    }


    private static <T> ResultData<T> restResult(T data, int code, String msg) {
        ResultData<T> apiResult = new ResultData<>();
        apiResult.setCode(code);
        apiResult.setData(data);
        apiResult.setMsg(msg);
        return apiResult;
    }

    public static <T> Boolean isError(ResultData<T> ret) {
        return !isSuccess(ret);
    }

    public static <T> Boolean isSuccess(ResultData<T> ret) {
        return ResultData.SUCCESS == ret.getCode();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
