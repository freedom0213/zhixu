package com.zhixu.learning.service;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.learning.domain.dto.NoteFormDTO;
import com.zhixu.learning.domain.po.Note;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.learning.domain.query.NoteAdminPageQuery;
import com.zhixu.learning.domain.query.NotePageQuery;
import com.zhixu.learning.domain.vo.NoteAdminDetailVO;
import com.zhixu.learning.domain.vo.NoteAdminVO;
import com.zhixu.learning.domain.vo.NoteVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 */
public interface INoteService extends IService<Note> {

    void saveNote(NoteFormDTO noteDTO);

    void gatherNote(Long id);

    void removeGatherNote(Long id);

    void updateNote(NoteFormDTO noteDTO);

    PageDTO<NoteVO> queryNotePage(NotePageQuery query);

    PageDTO<NoteAdminVO> queryNotePageForAdmin(NoteAdminPageQuery query);

    NoteAdminDetailVO queryNoteDetailForAdmin(Long id);

    void hiddenNote(Long id, boolean hidden);

    void removeMyNote(Long id);
}
