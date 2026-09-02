package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.PointsExchangeFormDTO;
import com.tianji.learning.domain.query.PointsExchangePageQuery;
import com.tianji.learning.domain.query.PointsMallPageQuery;
import com.tianji.learning.domain.vo.PointsExchangeRecordVO;
import com.tianji.learning.domain.vo.PointsMallItemVO;
import com.tianji.learning.service.IPointsMallService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@Validated
public class PointsMallController {
    private final IPointsMallService mallService;

    @GetMapping("/points-mall-items/page")
    public PageDTO<PointsMallItemVO> items(PointsMallPageQuery query) { return mallService.queryItems(query); }

    @GetMapping("/points-mall-items/{id}")
    public PointsMallItemVO item(@PathVariable Long id) { return mallService.queryItem(id); }

    @PostMapping("/points-exchange-records")
    public void exchange(@RequestBody @Valid PointsExchangeFormDTO form) { mallService.exchange(form); }

    @GetMapping("/points-exchange-records/user/page")
    public PageDTO<PointsExchangeRecordVO> records(PointsExchangePageQuery query) { return mallService.queryRecords(query); }

    @GetMapping("/points-exchange-records/{id}")
    public PointsExchangeRecordVO record(@PathVariable Long id) { return mallService.queryRecord(id); }

    @PutMapping("/points-exchange-records/cancel/{id}")
    public void cancel(@PathVariable Long id) { mallService.cancel(id); }
}
