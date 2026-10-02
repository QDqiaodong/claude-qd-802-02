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
import com.linen.laundry.domain.WashRecipeVersion;
import com.linen.laundry.dto.RecipeActionRequest;
import com.linen.laundry.dto.RecipeDisableRequest;
import com.linen.laundry.dto.RecipeDraftCreateRequest;
import com.linen.laundry.dto.RecipeDraftSaveRequest;
import com.linen.laundry.service.WashRecipeService;

/**
 * 洗涤配方版本台。
 */
@RestController
@RequestMapping("/api/recipes")
public class WashRecipeController {

    private final WashRecipeService recipeService;

    public WashRecipeController(WashRecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public ApiResponse<List<WashRecipeVersion>> list(@RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(recipeService.list(categoryId));
    }

    /** 品类当前生效版本（排产对话框提示用）；未发布过返回 null */
    @GetMapping("/current")
    public ApiResponse<WashRecipeVersion> current(@RequestParam Long categoryId) {
        return ApiResponse.success(recipeService.current(categoryId));
    }

    @GetMapping("/{id}")
    public ApiResponse<WashRecipeVersion> detail(@PathVariable Long id) {
        return ApiResponse.success(recipeService.get(id));
    }

    /** 新建草稿（可带 baseVersionId 从既有版本复制） */
    @PostMapping("/drafts")
    public ApiResponse<WashRecipeVersion> createDraft(@RequestBody RecipeDraftCreateRequest request) {
        return ApiResponse.success("草稿已创建", recipeService.createDraft(request));
    }

    /** 保存草稿（整体覆盖，可反复调整；仅草稿可改） */
    @PutMapping("/drafts/{id}")
    public ApiResponse<WashRecipeVersion> updateDraft(@PathVariable Long id,
                                                      @RequestBody RecipeDraftSaveRequest request) {
        return ApiResponse.success("草稿已保存", recipeService.updateDraft(id, request));
    }

    @DeleteMapping("/drafts/{id}")
    public ApiResponse<Void> deleteDraft(@PathVariable Long id) {
        recipeService.deleteDraft(id);
        return ApiResponse.success("草稿已删除", null);
    }

    /** 复制生成新草稿（发布后不可原地改写的唯一调整途径） */
    @PostMapping("/{id}/copy")
    public ApiResponse<WashRecipeVersion> copy(@PathVariable Long id) {
        return ApiResponse.success("已复制为新草稿", recipeService.copy(id));
    }

    /** 发布：校验八道工序完整、参数合法、有可用设备；并发发布只生效一份 */
    @PostMapping("/{id}/publish")
    public ApiResponse<WashRecipeVersion> publish(@PathVariable Long id,
                                                  @RequestBody(required = false) RecipeActionRequest request) {
        String operator = request == null ? null : request.getOperator();
        return ApiResponse.success("发布成功，已设为当前版本", recipeService.publish(id, operator));
    }

    /** 停用当前版本：必须先选定接替版本 */
    @PostMapping("/{id}/disable")
    public ApiResponse<WashRecipeVersion> disable(@PathVariable Long id,
                                                  @RequestBody RecipeDisableRequest request) {
        Long successorId = request == null ? null : request.getSuccessorVersionId();
        String operator = request == null ? null : request.getOperator();
        return ApiResponse.success("已停用并切换接替版本", recipeService.disable(id, successorId, operator));
    }
}
