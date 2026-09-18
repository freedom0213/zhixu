import request from "@/utils/request.js"
const AI_API_PREFIX = "/ct"

// 用户会话模块
// assistantType: GLOBAL=全局助手 / COURSE=课程助教 / PRIVATE=私人助手

//新增会话
export const createUserSession = (data, assistantType, courseId) =>
    request({
        url: `${AI_API_PREFIX}/session`,
        method: 'post',
        params: { assistantType, courseId },
        data
    })
//查询用户会话列表（按助手类型过滤，默认 PRIVATE）
export const getUserSessionList = (assistantType) =>
    request({
        url: `${AI_API_PREFIX}/session/list`,
        method: 'get',
        params: { assistantType }
    })
//更改对话
export const updateUserSession = (id,params, assistantType) =>
    request({
        url: `${AI_API_PREFIX}/session/${id}`,
        method: 'put',
        params: { ...params, assistantType }
    })
//删除会话
export const deleteUserSession = (id, assistantType) =>
    request({
        url: `${AI_API_PREFIX}/session/${id}`,
        method: 'delete',
        params: { assistantType }
    })

//聊天接口

//根据会话id获取聊天记录
export const getChatRecord = (params, assistantType) =>
    request({
        url: `${AI_API_PREFIX}/chat/records`,
        method: 'get',
        params: { ...params, assistantType }
    })

// AI聊天接口
// 走 POST 请求体：出题等场景提示词较长，中文 URL 编码后体积膨胀约 3 倍，易超请求行上限（413）
export const memoryChatRedis = (params, signal) =>
    request({
        url: `${AI_API_PREFIX}/chat/simple`,
        method: 'post',
        data: params,
        signal,
        timeout: 60000
    })
//AI流式聊天接口
export const memoryChatRedisStream = (params) =>
    request({
        url: `${AI_API_PREFIX}/chat/`,
        method: 'get',
        params,
        responseType: 'stream' 
    })

// 知识库接口

// 上传文件到知识库
export const uploadMarkdown = (file, level = 2) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('level', level);
    return request({
    url: `${AI_API_PREFIX}/file/upload`,
    method: 'post',
    data: formData,
    headers: {
        'Content-Type': 'multipart/form-data'
    }
    });
};

// 说明：原 chatByMarkdownDoc（GET /ct/file/chat）已随 /main/ai/knowledge 页面一并移除。
// 该接口返回的是 {content:"<AI 回答>"}，与「知识库问答」/ct/file/chat/stream 属同一套逻辑，
// 仅流式与非流式之差，存在重复；前端统一改用流式接口。后端接口本身保留未动。

// 私人助手「学习工具」非流式接口（笔记整理 / 内容检查 / 自动出题 / 学习计划）
// 出题需要完整 JSON，因此走非流式；prose 类任务走 /ct/chat/task/stream
// 走 POST 请求体，避免长提示词经 URL 编码后触发 413
export const chatTask = (params, signal) =>
    request({
    url: `${AI_API_PREFIX}/chat/task`,
    method: 'post',
    data: params,
    signal,
    timeout: 120000
    });

// 分页查询用户知识库文件列表
export const queryMarkdownPage = (params) =>
    request({
    url: `${AI_API_PREFIX}/file/page`,
    method: 'get',
    params
    });

// 根据文件 id 查看文件内容
export const getMarkdown = (fileId) =>
    request({
    url: `${AI_API_PREFIX}/file/${fileId}`,
    method: 'get'
    });

// 更新文件内容
export const updateMarkdown = (markdownDocs) =>
    request({
    url: `${AI_API_PREFIX}/file/update`,
    method: 'put',
    data: markdownDocs
    });

// 根据文件 id 删除文件
export const deleteMarkdown = (fileId) =>
    request({
    url: `${AI_API_PREFIX}/file/${fileId}`,
    method: 'delete'
});

// 查询某门课程的知识库文件列表（用于判断课程是否已接入知识库）
export const queryCourseFilePage = (courseId) =>
    request({
    url: `${AI_API_PREFIX}/course/${courseId}/file/page`,
    method: 'get'
    });
