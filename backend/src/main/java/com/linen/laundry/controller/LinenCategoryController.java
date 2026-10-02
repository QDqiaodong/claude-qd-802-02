package com.linen.laundry.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linen.laundry.common.ApiResponse;
import com.linen.laundry.domain.LinenCategory;
import com.linen.laundry.dto.CountItem;
import com.linen.laundry.dto.CountResult;
import com.linen.laundry.service.LinenCategoryService;

/**
 * 布草品类台账与快速盘点。
 */
@RestController
@RequestMapping("/api/categories")
public class LinenCategoryController {

    private final LinenCategoryService categoryService;

    public LinenCategoryController(LinenCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<LinenCategory>> list() {
        return ApiResponse.success(categoryService.list());
    }

    /** 在库数低于最低周转量的品类（带缺口数） */
    @GetMapping("/shortage")
    public ApiResponse<List<LinenCategory>> shortage() {
        return ApiResponse.success(categoryService.shortage());
    }

    @GetMapping("/{id}")
    public ApiResponse<LinenCategory> detail(@PathVariable Long id) {
        return ApiResponse.success(categoryService.get(id));
    }

    /** 台账卡片上的 −/+ 步进器 */
    @PutMapping("/{id}/stock")
    public ApiResponse<LinenCategory> adjustStock(@PathVariable Long id, @RequestParam int delta) {
        return ApiResponse.success("在库数已调整", categoryService.adjustStock(id, delta));
    }

    /** 保存盘点：返回差异数与损益金额 */
    @PostMapping("/count")
    public ApiResponse<CountResult> saveCount(@RequestBody List<CountItem> items) {
        return ApiResponse.success("盘点已保存", categoryService.saveCount(items));
    }

    @PostMapping
    public ApiResponse<LinenCategory> create(@RequestBody LinenCategory category) {
        return ApiResponse.success("品类已创建", categoryService.create(category));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.success("品类已删除", null);
    }
}
