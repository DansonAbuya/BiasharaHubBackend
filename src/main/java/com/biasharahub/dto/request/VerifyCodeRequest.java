package com.biasharahub.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerifyCodeRequest {
    @NotBlank
    @Email
    private String email;

    /** 6-digit verification code (accept integer or string from client). Normalized to string for lookup. */
    @NotNull
    private String codeNormalized;

    /** Accept code as integer (e.g. 123456) or string (e.g. "123456") from JSON. */
    @JsonSetter("code")
    public void setCode(Object code) {
        if (code == null) {
            this.codeNormalized = null;
            return;
        }
        if (code instanceof Number) {
            int n = ((Number) code).intValue();
            this.codeNormalized = String.valueOf(n);
        } else {
            String s = code.toString().trim();
            this.codeNormalized = s.isEmpty() ? null : s;
        }
    }
}
