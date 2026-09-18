-- =============================================================================
-- P23 · 视频播放（本地媒资）
-- tj_media 库此前没有任何表（media-service 因缺 IFileStorage bean 从未启动过）。
-- 这里按 tj-media 模块的 Media / File PO 建表；幂等（CREATE TABLE IF NOT EXISTS）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS media (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（MP 全局 id-type=AUTO， setId 会被忽略）',
    file_id     VARCHAR(255) NOT NULL COMMENT '媒资文件唯一标识（LOCAL 模式 = 本地文件 key）',
    filename    VARCHAR(255)          DEFAULT NULL COMMENT '文件名',
    media_url   VARCHAR(255)          DEFAULT NULL COMMENT '播放地址（LOCAL = /ms/media-stream/{key}；VOD 为空）',
    cover_url   VARCHAR(255)          DEFAULT NULL COMMENT '封面地址',
    duration    FLOAT                 NOT NULL DEFAULT 0 COMMENT '视频时长（秒）',
    request_id  VARCHAR(255)          DEFAULT NULL COMMENT '上传请求id',
    status      INT                   NOT NULL DEFAULT 1 COMMENT '状态：1 上传中 / 2 已上传 / 3 已处理',
    size        BIGINT                DEFAULT NULL COMMENT '文件大小（字节）',
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    creater     BIGINT                DEFAULT NULL,
    updater     BIGINT                DEFAULT NULL,
    deleted     INT                   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_file_id (file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '媒资表';

CREATE TABLE IF NOT EXISTS `file` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `key`       VARCHAR(255) NOT NULL COMMENT '文件唯一标识',
    filename    VARCHAR(255)          DEFAULT NULL COMMENT '文件名',
    request_id  VARCHAR(255)          DEFAULT NULL COMMENT '上传请求id',
    status      INT                   NOT NULL DEFAULT 1 COMMENT '状态：1 上传中 / 2 已上传 / 3 已处理',
    platform    INT                   NOT NULL DEFAULT 1 COMMENT '存储平台：1 腾讯 / 2 阿里 / 3 七牛 / 0 本地',
    create_time DATETIME              DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    creater     BIGINT                DEFAULT NULL,
    updater     BIGINT                DEFAULT NULL,
    deleted     INT                   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_key (`key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文件表';
