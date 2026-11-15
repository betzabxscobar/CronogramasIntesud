package ec.edu.intesud.crono.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestCodeRequest(@NotBlank @Size(min = 6, max = 20) String nationalId) {}
