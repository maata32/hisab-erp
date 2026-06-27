package com.hisaberp.catalog.api;

import com.hisaberp.catalog.api.AttributeDto.AttributeValueDto;
import com.hisaberp.catalog.internal.AttributeService;
import com.hisaberp.catalog.internal.AttributeService.SaveAttributeRequest;
import com.hisaberp.catalog.internal.AttributeService.SaveAttributeValueRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attributes")
@RequiredArgsConstructor
@Tag(name = "Product attributes")
public class AttributeController {

    private final AttributeService service;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public List<AttributeDto> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public AttributeDto create(@RequestBody SaveAttributeRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public AttributeDto update(@PathVariable UUID id, @RequestBody SaveAttributeRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/values")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.CREATED)
    public AttributeValueDto addValue(@PathVariable UUID id, @RequestBody SaveAttributeValueRequest req) {
        return service.addValue(id, req);
    }

    @PatchMapping("/{id}/values/{valueId}")
    @PreAuthorize("hasAuthority('product:update')")
    public AttributeValueDto updateValue(@PathVariable UUID id, @PathVariable UUID valueId,
                                         @RequestBody SaveAttributeValueRequest req) {
        return service.updateValue(id, valueId, req);
    }

    @DeleteMapping("/{id}/values/{valueId}")
    @PreAuthorize("hasAuthority('product:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteValue(@PathVariable UUID id, @PathVariable UUID valueId) {
        service.deleteValue(id, valueId);
    }
}
