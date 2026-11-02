package com.lol.common.exception;


import com.lol.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 捕获业务异常（400）
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    // 参数类型不对，例如把 /api/user/abc/profile 里的 id 写成了非数字。
    // 这类请求走不到 Controller 的 try-catch，会直接落到下面的兜底分支，
    // 于是用户手动改地址栏时只能看到一句"网络异常"，完全看不出哪里写错了。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{} = {}", e.getName(), e.getValue());
        return Result.error(400, "地址里的 " + e.getName() + " 不是有效值，请检查链接是否完整");
    }

    // 请求体不是合法 JSON，或字段类型对不上。
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析：{}", e.getMessage());
        return Result.error(400, "提交的数据格式不正确，请刷新页面后重试");
    }

    // 上传的文件超过 spring.servlet.multipart 的上限。Tomcat 会在读完之前就掐断连接，
    // 请求根本进不到 Controller，不在这里接住就只有一句"网络异常"，
    // 用户完全不知道是自己视频太大了。
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限：{}", e.getMessage());
        return Result.error(400, "文件太大了，传不上去。头像最多 2MB，图片最多 5MB，视频最多 20MB");
    }

    // 表单里缺了 file 这一段（前端传参写错时会遇到）
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<?> handleMissingPart(MissingServletRequestPartException e) {
        return Result.error(400, "没有收到文件内容，请重新选择文件后再发");
    }

    // 请求的静态资源不存在 —— 最常见的是聊天里引用的图片/视频已被删除。
    // 不接住的话会落进下面的 500 兜底，日志被刷屏，调用方也分不清"没了"和"坏了"。
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<?> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return Result.error(404, "内容不存在或已被删除");
    }

    // 捕获系统内部异常（500）
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 把异常打到日志里。此前这里只返回一句"网络异常"且不记录任何东西，
        // 导致接口报 500 时前端和日志里都找不到任何线索。
        log.error("接口处理失败：{}", e.toString(), e);
        return Result.error(500, "网络异常，请稍后重试");
    }
}
