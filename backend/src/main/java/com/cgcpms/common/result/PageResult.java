package com.cgcpms.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pagination result wrapper.
 *
 * @param <T> record type
 */
@Data
public class PageResult<T> implements Serializable {

    private long pageNo;

    private long pageSize;

    private long total;

    private List<T> records;

    public PageResult() {
    }

    public PageResult(long pageNo, long pageSize, long total, List<T> records) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        this.records = records;
    }

    /**
     * Convert a MyBatis-Plus {@link IPage} into a {@link PageResult}.
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setPageNo(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords() == null ? new ArrayList<>() : page.getRecords());
        return result;
    }
}
