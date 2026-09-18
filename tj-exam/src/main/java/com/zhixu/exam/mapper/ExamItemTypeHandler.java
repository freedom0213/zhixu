package com.zhixu.exam.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.exam.domain.po.ExamItem;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * exam.items（JSON 数组 [{questionId, score}]）↔ List&lt;ExamItem&gt;
 * MP 3.4.3 的 JacksonTypeHandler 不带类型参数，自定义对象集合会被读成
 * List&lt;LinkedHashMap&gt;（实测踩过：expandItems 里 ClassCastException）—— 所以要专用 Handler。
 * </p>
 */
@MappedTypes(List.class)
public class ExamItemTypeHandler extends BaseTypeHandler<List<ExamItem>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<ExamItem> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("序列化 exam.items 失败", e);
        }
    }

    @Override
    public List<ExamItem> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<ExamItem> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<ExamItem> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<ExamItem> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<ExamItem>>() {});
        } catch (Exception e) {
            throw new SQLException("反序列化 exam.items 失败", e);
        }
    }
}
