package fr.esilv.poolup.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/reports}, e.g. {@code {"targetType": "MESSAGE", "targetId": 12, "reason": "..."}}. */
public record ReportRequest(
        @NotNull ReportTargetType targetType,
        @NotNull Long targetId,
        @NotBlank @Size(max = 1000) String reason) {
}
