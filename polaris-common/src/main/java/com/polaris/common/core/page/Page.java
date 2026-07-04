package com.polaris.common.core.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Setter
public class Page<T> implements Serializable {

	private static final long serialVersionUID = 1417358680899547176L;

	@Schema(description = "当前页数")
	private long pageNum;

	@Schema(description = "每页数量")
	private long pageSize;

	@Schema(description = "总记录数")
	private long total;

	@Schema(description = "总页数")
	private long pages;

	@Schema(description = "数据")
	private List<T> rows;

	@Schema(description = "是否有下一页")
	private boolean hasNextPage = false;

	public Page() {
	}

	public Page(PageInfo<T> pageInfo) {
		this.rows = pageInfo.getList();
		this.pageNum = pageInfo.getPageNum();
		this.pageSize = pageInfo.getPageSize();
		this.total = pageInfo.getTotal();
		this.pages = pageInfo.getPages();
		this.hasNextPage = pageInfo.isHasNextPage();
	}

	public static <T> Page<T> of(PageInfo<T> pageInfo) {
		return new Page<>(pageInfo);
	}

	public static <T, R> Page<R> of(PageInfo<T> pageInfo, Function<T, R> function) {
		Page<R> page = new Page<>();
		page.setPageNum(pageInfo.getPageNum());
		page.setPageSize(pageInfo.getPageSize());
		page.setTotal(pageInfo.getTotal());
		page.setPages(pageInfo.getPages());
		page.setHasNextPage(pageInfo.isHasNextPage());
		page.setRows(pageInfo.getList().stream().map(function).collect(Collectors.toList()));
		return page;
	}

	public static <T> Page<T> getInstance(PageInfo<T> pageInfo) {
		return new Page<>(pageInfo);
	}

	// mybatis-plus
	public static <T> Page<T> of(IPage<T> iPage) {
		Page<T> page = new Page<>();
		page.setPageNum(iPage.getCurrent());
		page.setPageSize(iPage.getSize());
		page.setTotal(iPage.getTotal());
		page.setPages(iPage.getPages());
		page.setHasNextPage(iPage.getCurrent() < iPage.getPages());
		page.setRows(iPage.getRecords());
		return page;
	}

	public static <T, R> Page<R> of(IPage<T> iPage, Function<T, R> function) {
		Page<R> page = new Page<>();
		page.setPageNum(iPage.getCurrent());
		page.setPageSize(iPage.getSize());
		page.setTotal(iPage.getTotal());
		page.setPages(iPage.getPages());
		page.setHasNextPage(iPage.getCurrent() < iPage.getPages());
		page.setRows(iPage.getRecords().stream().map(function).collect(Collectors.toList()));
		return page;
	}

	// 转换
	public static <T, R> Page<R> of(Page<T> page, Function<T, R> function) {
		Page<R> pr = new Page<>();
		pr.setPageNum(page.getPageNum());
		pr.setPageSize(page.getPageSize());
		pr.setTotal(page.getTotal());
		pr.setPages(page.getPages());
		pr.setHasNextPage(page.isHasNextPage());
		pr.setRows(page.getRows().stream().map(function).collect(Collectors.toList()));
		return pr;
	}
}
