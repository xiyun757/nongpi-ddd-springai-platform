package com.nongpi.fulfillment.alert.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 预警处理请求 DTO
 */
public record HandleRequest(@NotBlank(message = "处理人不能为空") String handler) {}
