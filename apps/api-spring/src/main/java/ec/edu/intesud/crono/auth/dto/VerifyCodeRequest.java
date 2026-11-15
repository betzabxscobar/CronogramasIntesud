package ec.edu.intesud.crono.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyCodeRequest(
    @NotBlank @Size(min = 6, max = 20) String nationalId,
    @NotNull @Pattern(regexp = "^\\d{6}$") String code) {}
