package com.lol.server.config;

import com.lol.server.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 把聊天上传的文件目录映射成静态资源。
 *
 * 单独起一个配置类，不去动 lol-common 里那个共享的 WebConfig —— 那个模块
 * 还被别的模块依赖，往里塞业务相关的映射不合适。
 */
@Configuration
public class UploadWebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:./uploads}")
    private String rootConfig;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(rootConfig).toAbsolutePath().normalize();
        // 结尾的斜杠不能少，否则映射不到目录而是拼成了前缀
        registry.addResourceHandler(FileStorageService.URL_PREFIX + "**")
                .addResourceLocations("file:" + root.toString().replace('\\', '/') + "/");
    }
}
