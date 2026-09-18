package com.zhixu.exam.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.exam.domain.po.ExamSnapshotItem;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * exam.snapshot_items（JSON，发布时冻结的题目副本）↔ List&lt;ExamSnapshotItem&gt;
 * 同 ExamItemTypeHandler：MP 3.4.3 需要专用 Handler 才能反序列化自定义对象集合。
 * </p>
 */
public class ExamSnapshotItemTypeHandler extends BaseTypeHandler<List<ExamSnapshotItem>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<ExamSnapshotItem> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("序列化 exam.snapshot_items 失败", e);
        }
    }

    @Override
    public List<ExamSnapshotItem> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<ExamSnapshotItem> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<ExamSnapshotItem> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<ExamSnapshotItem> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<ExamSnapshotItem>>() {});
        } catch (Exception e) {
            throw new SQLException("反序列化 exam.snapshot_items 失败", e);
        }
    }
}
