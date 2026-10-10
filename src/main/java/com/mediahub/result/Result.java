package com.mediahub.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一响应结果封装（success 标记请求是否成功，data 为业务数据，total 为列表总数）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    /**
     * 是否处理成功
     */
    private Boolean success;
    /**
     * 失败时的错误提示
     */
    private String errorMsg;
    /**
     * 返回的业务数据
     */
    private Object data;
    /**
     * 列表数据的总条数（分页场景使用）
     */
    private Long total;

    public static Result ok(){
        return new Result(true, null, null, null);
    }
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
