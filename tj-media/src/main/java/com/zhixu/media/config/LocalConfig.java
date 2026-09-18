package com.zhixu.media.config;

import com.zhixu.media.storage.local.LocalFileStorage;
import com.zhixu.media.storage.local.LocalMediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地存储平台装配（P23）。
 * -----------------------------------------------------------------------------
 * 用户决策：本地环境**不配云凭证**。以前 platform 只能选 ALI/TENCENT，都没配 →
 * IFileStorage / IMediaStorage 两个 bean 都缺 → media-service 起不来（崩溃循环）。
 * 现在 `tj.file.platform=LOCAL` + `tj.platform.media=LOCAL` 即可全本地运行。
 */
@Configuration
public class LocalConfig {

    @Bean
    @ConditionalOnProperty(prefix = "tj.file", name = "platform", havingValue = "LOCAL")
    public LocalFileStorage localFileStorage(
            @Value("${tj.file.local.base-path:/data/media}") String basePath) {
        return new LocalFileStorage(basePath);
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "media", havingValue = "LOCAL")
    public LocalMediaStorage localMediaStorage(LocalFileStorage fileStorage) {
        return new LocalMediaStorage(fileStorage);
    }
}
