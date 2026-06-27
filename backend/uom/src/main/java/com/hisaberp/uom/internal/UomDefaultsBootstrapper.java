package com.hisaberp.uom.internal;

import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.tenant.events.OrganizationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the default UoM categories and units for a freshly created tenant.
 * Listens asynchronously to {@link OrganizationCreatedEvent}.
 *
 * Defaults cover Mauritanian boutique/wholesale needs: count, length, mass, volume.
 * The stored {@code name} is a French fallback; the admin UI localizes these built-in
 * codes (fr/ar/en) by code, so renaming is optional. Tenants can extend or replace
 * these via {@code POST /api/v1/uoms}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class UomDefaultsBootstrapper {

    private final UomCategoryRepository categories;
    private final UomRepository uoms;

    private record SeedUom(String code, String name, BigDecimal ratio, boolean base, int decimals) {}
    private record SeedCategory(String code, String name, List<SeedUom> units) {}

    private static final List<SeedCategory> SEED = List.of(
            new SeedCategory("COUNT", "Comptage / Pièces", List.of(
                    new SeedUom("PCE", "Pièce", BigDecimal.ONE, true, 0),
                    new SeedUom("DOZ", "Douzaine", new BigDecimal("12"), false, 0),
                    new SeedUom("CTN_6", "Carton de 6", new BigDecimal("6"), false, 0),
                    new SeedUom("CTN_12", "Carton de 12", new BigDecimal("12"), false, 0),
                    new SeedUom("CTN_24", "Carton de 24", new BigDecimal("24"), false, 0)
            )),
            new SeedCategory("LENGTH", "Longueur", List.of(
                    new SeedUom("M", "Mètre", BigDecimal.ONE, true, 2),
                    new SeedUom("CM", "Centimètre", new BigDecimal("0.01"), false, 0),
                    new SeedUom("KM", "Kilomètre", new BigDecimal("1000"), false, 3)
            )),
            new SeedCategory("MASS", "Masse", List.of(
                    new SeedUom("KG", "Kilogramme", BigDecimal.ONE, true, 3),
                    new SeedUom("G", "Gramme", new BigDecimal("0.001"), false, 0),
                    new SeedUom("T", "Tonne", new BigDecimal("1000"), false, 3)
            )),
            new SeedCategory("VOLUME", "Volume", List.of(
                    new SeedUom("L", "Litre", BigDecimal.ONE, true, 3)
            ))
    );

    @ApplicationModuleListener
    public void onOrgCreated(OrganizationCreatedEvent event) {
        try {
            TenantContext.set(event.organizationId());
            int unitCount = 0;
            for (SeedCategory sc : SEED) {
                UomCategory cat = UomCategory.builder().code(sc.code()).name(sc.name()).build();
                categories.save(cat);
                for (SeedUom su : sc.units()) {
                    Uom u = Uom.builder()
                            .categoryId(cat.getId())
                            .code(su.code())
                            .name(su.name())
                            .ratioToBase(su.ratio())
                            .isBase(su.base())
                            .decimalPlaces(su.decimals())
                            .build();
                    uoms.save(u);
                    unitCount++;
                }
            }
            log.info("Seeded {} UoM categories and {} units for tenant {}",
                    SEED.size(), unitCount, event.code());
        } finally {
            TenantContext.clear();
        }
    }
}
