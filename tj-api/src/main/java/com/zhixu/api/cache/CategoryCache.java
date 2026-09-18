package com.zhixu.api.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.zhixu.api.client.course.CategoryClient;
import com.zhixu.api.dto.course.CategoryBasicDTO;
import com.zhixu.common.utils.CollUtils;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class CategoryCache {

    private final Cache<String, Map<Long, CategoryBasicDTO>> categoryCaches;

    private final CategoryClient categoryClient;

    public Map<Long, CategoryBasicDTO> getCategoryMap() {
        return categoryCaches.get("CATEGORY", key -> {
            // 1.从CategoryClient查询
            List<CategoryBasicDTO> list = categoryClient.getAllOfOneLevel();
            if (list == null || list.isEmpty()) {
                return CollUtils.emptyMap();
            }
            // 2.转换数据
            return list.stream().collect(Collectors.toMap(CategoryBasicDTO::getId, Function.identity()));
        });
    }

    public String getCategoryNames(List<Long> ids) {
        if (ids == null || ids.size() == 0) {
            return "";
        }
        // 1.读取分类缓存
        Map<Long, CategoryBasicDTO> map = getCategoryMap();
        // 2.根据id查询分类名称并组装
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            // ⚠️ 容错（2026-09-16）：id 可能为 null 或指向一个没维护的分类。
            //    这类脏数据不该打挂整个接口 —— 题库列表就是因为这里 NPE 直接 500。
            CategoryBasicDTO c = id == null ? null : map.get(id);
            if (c == null) {
                continue;
            }
            sb.append(c.getName()).append("/");
        }
        // 3.返回结果
        if (sb.length() == 0) {
            return "";
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    public List<String> getCategoryNameList(List<Long> ids) {
        if (ids == null || ids.size() == 0) {
            return CollUtils.emptyList();
        }
        // 1.读取分类缓存
        Map<Long, CategoryBasicDTO> map = getCategoryMap();
        // 2.根据id查询分类名称并组装
        List<String> list = new ArrayList<>(ids.size());
        for (Long id : ids) {
            // ⚠️ 容错：未命中给空串占位（保持与传入 ids 一一对应，调用方按位置取值不会错位），
            //    不抛空指针。调用方也会先把 null 过滤掉。
            CategoryBasicDTO c = id == null ? null : map.get(id);
            list.add(c == null ? "" : c.getName());
        }
        // 3.返回结果
        return list;
    }

    public List<CategoryBasicDTO> queryCategoryByIds(List<Long> ids) {
        if (ids == null || ids.size() == 0) {
            return CollUtils.emptyList();
        }
        Map<Long, CategoryBasicDTO> map = getCategoryMap();
        return ids.stream()
                .map(map::get)
                .collect(Collectors.toList());
    }

    public List<String> getNameByLv3Ids(List<Long> lv3Ids) {
        if (lv3Ids == null || lv3Ids.size() == 0) {
            return CollUtils.emptyList();
        }
        Map<Long, CategoryBasicDTO> map = getCategoryMap();
        List<String> list = new ArrayList<>(lv3Ids.size());
        for (Long lv3Id : lv3Ids) {
            list.add(fullPathName(map, lv3Id));
        }
        return list;
    }

    public String getNameByLv3Id(Long lv3Id) {
        return fullPathName(getCategoryMap(), lv3Id);
    }

    /**
     * 三级分类的「一级/二级/三级」全路径。
     * ⚠️ 容错：任一级缺失（id 为 null、或没维护）都给空串占位，不抛空指针 ——
     *    以前是 map.get(...).getParentId() 一路点下去，一个脏 id 就整条链崩。
     */
    private String fullPathName(Map<Long, CategoryBasicDTO> map, Long lv3Id) {
        CategoryBasicDTO lv3 = lv3Id == null ? null : map.get(lv3Id);
        if (lv3 == null) {
            return "";
        }
        CategoryBasicDTO lv2 = lv3.getParentId() == null ? null : map.get(lv3.getParentId());
        CategoryBasicDTO lv1 = (lv2 == null || lv2.getParentId() == null) ? null : map.get(lv2.getParentId());
        return (lv1 == null ? "" : lv1.getName()) + "/"
                + (lv2 == null ? "" : lv2.getName()) + "/"
                + lv3.getName();
    }
}
