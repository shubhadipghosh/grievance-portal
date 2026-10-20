package com.shubha.grievanceportal.grievance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A citizen filing a grievance. The department is the category's default;
 * the SLA deadline comes from the category.
 *
 * @param addressText nullable
 * @param latitude    nullable
 * @param longitude   nullable
 */
public record NewGrievance(
        @NotNull UUID categoryId,
        @NotNull UUID wardId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        @Size(max = 500) String addressText,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude) {
}
