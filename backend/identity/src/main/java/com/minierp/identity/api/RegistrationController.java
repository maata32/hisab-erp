package com.minierp.identity.api;

import com.minierp.identity.internal.RegistrationService;
import com.minierp.identity.internal.RegistrationService.RegistrationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
@Tag(name = "Registration", description = "Public self-service tenant registration (request → approval)")
public class RegistrationController {

    private final RegistrationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit a new tenant registration request (creates a PENDING tenant + admin user)")
    public RegistrationResult register(@Valid @RequestBody RegisterRequest req) {
        return service.register(req);
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 50)
            @Pattern(regexp = "^[a-z0-9-]+$", message = "Code must be lowercase alphanumeric with dashes only")
            String tenantCode,
            @NotBlank @Size(min = 2, max = 200) String companyName,
            // Type is validated server-side against the configurable organization_types
            // table (must exist and be active); no hardcoded enum pattern here.
            @NotBlank @Size(max = 20) String companyType,
            @Size(max = 3) String currency,
            @Size(max = 10) String locale,
            @Size(max = 50) String timezone,
            @Size(max = 500) String companyAddress,
            @Size(max = 30) String companyPhone,
            @NotBlank @Size(max = 50) String planCode,
            @NotBlank @Size(min = 2, max = 200) String adminFullName,
            @NotBlank @Email @Size(max = 200) String adminEmail,
            @Size(max = 30) String adminPhone,
            @NotBlank @Size(min = 8, max = 200) String password) {}
}
