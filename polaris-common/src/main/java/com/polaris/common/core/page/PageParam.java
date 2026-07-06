package com.polaris.common.core.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 类说明：分页查询参数
 *
 */
@EqualsAndHashCode(callSuper=false)
@Data
public class PageParam extends Param {

	@Schema(description = "当前页码", example = "0")
	private int pageNum = 0;

	@Schema(description = "每页条数", example = "10")
	private int pageSize = 10;

	public <T> Page<T> pageable() {
		if (getPageNum() < 0) {
			setPageNum(1);
		}
		if (getPageSize() <= 0) {
			setPageSize(10);
		}
		Page<T> page = new Page<>(getPageNum(), getPageSize());
		return page;
	}

}
