package com.zhixu.media.constants;

public interface FileErrorInfo {
    String MEDIA_NOT_EXISTS = "媒资不存在";
    /** 小节还没传视频 —— 这是正常业务状态，不能拿「媒资不存在」这种内部说法丢给学生 */
    String MEDIA_NOT_UPLOADED = "该小节暂未上传视频";
    String MEDIA_NOT_FREE = "课程不支持试看";
    String USER_NOT_EXISTS = "用户信息不存在";
    String MEDIA_QUOTE_NOT_EXISTS = "媒资引用信息查询异常";
}
