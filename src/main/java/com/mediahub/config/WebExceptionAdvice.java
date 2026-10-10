package com.mediahub.config;

import com.mediahub.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
/** 全局异常处理：拦截并包装运行时异常 */
public class WebExceptionAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /** 处理 @Valid 参数校验失败，返回第一条具体字段错误提示 */
    public Result handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(msg);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    /** 处理数据库唯一键冲突，提示勿重复提交 */
    public Result handleDuplicateKey(DuplicateKeyException e) {
        log.error(e.toString(), e);
        return Result.fail("数据已存在，请勿重复提交");
    }

    @ExceptionHandler(RuntimeException.class)
    /** 处理运行时异常，返回统一失败结果并记录日志 */
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}
