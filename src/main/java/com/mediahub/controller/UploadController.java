package com.mediahub.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.mediahub.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 评论配图上传控制器
 * 图片保存在 app.upload-dir 目录，通过 /images/** 静态资源映射对外访问。
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    // 图片上传根目录（来自配置 app.upload-dir）
    @Value("${app.upload-dir}")
    private String uploadDir;

    /** 初始化：确保上传目录存在 */
    @PostConstruct
    public void init() {
        // 将相对配置（如 ./upload）规范化为 JVM 工作目录下的绝对路径，
        // 避免 transferTo 把相对路径解析到 Tomcat multipart 临时目录导致写入失败
        //new File(uploadDir)：根据配置的路径创建 File 对象。如果 uploadDir 是相对路径（如 ./upload），它相对于当前 JVM 的工作目录。
        //.getAbsolutePath()：获取该文件的绝对路径。
        //然后把绝对路径重新赋值给 uploadDir。
        uploadDir = new File(uploadDir).getAbsolutePath();
        //new File(uploadDir)：基于绝对路径创建目录对象。
        //dir.exists()：判断目录是否已经存在。
        //dir.mkdirs()：如果不存在，就创建目录，包括所有不存在的父目录。
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        log.info("上传目录初始化完成：{}", uploadDir);
    }

    /** 上传评论配图，返回生成的相对路径文件名 */
    @PostMapping("/comment")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(getAbsolutePath(fileName)));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /** 删除指定的评论配图 */
    @GetMapping("/comment/delete")
    public Result deleteCommentImg(@RequestParam("name") String filename) {
        File file = new File(getAbsolutePath(filename));
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    /** 将相对文件名拼接为磁盘绝对路径 */
    private String getAbsolutePath(String fileName) {
        // fileName 形如 /reviews/1/2/xxx.jpg，去掉开头的 / 再拼接
        return uploadDir + File.separator + fileName.replaceFirst("^/+", "").replace('/', File.separatorChar);
    }

    /** 根据原始文件名生成新的散列分目录存储文件名 */
    private String createNewFileName(String originalFilename) {
        // 获取后缀  含义：StrUtil.subAfter从字符串中截取最后一个 . 之后的部分。
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成文件名
        String name = UUID.randomUUID().toString();
        // 按哈希散列成两级目录，避免单目录文件过多
        //name.hashCode()：获取 UUID 字符串的哈希值（int 类型，可能为负数）。
        //hash & 0xF：取低 4 位，结果范围 0 ~ 15，作为一级目录名 d1。
        //(hash >> 4) & 0xF：右移 4 位后再取低 4 位，结果范围 0 ~ 15，作为二级目录名 d2。
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 确保目录存在
        File dir = new File(getAbsolutePath(StrUtil.format("/reviews/{}/{}", d1, d2)));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/reviews/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
