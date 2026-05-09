package com.minierp.uom.internal;

import com.minierp.shared.tenant.TenantContext;
import com.minierp.tenant.events.OrganizationCreatedEvent;
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
 * Defaults cover Mauritanian boutique/wholesale needs: count, mass, volume, length, area.
 * Tenants can extend or replace these via {@code POST /api/v1/uoms}.
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
            new SeedCategory("COUNT", "Count / Pieces", List.of(
                    new SeedUom("PCE", "Piece", BigDecimal.ONE, true, 0),
                    new SeedUom("DOZ", "Dozen", new BigDecimal("12"), false, 0),
                    new SeedUom("CTN_6", "Carton of 6", new BigDecimal("6"), false, 0),
                    new SeedUom("CTN_12", "Carton of 12", new BigDecimal("12"), false, 0),
                    new SeedUom("CTN_24", "Carton of 24", new BigDecimal("24"), false, 0)
            )),
            new SeedCategory("MASS", "Mass", List.of(
                    new SeedUom("G", "Gram", BigDecimal.ONE, true, 0),
                    new SeedUom("KG", "Kilogram", new BigDecimal("1000"), false, 3),
                    new SeedUom("T", "Tonne", new BigDecimal("1000000"), false, 3),
                    new SeedUom("LB", "Pound", new BigDecimal("453.592"), false, 3)
            )),
            new SeedCategory("VOLUME", "Volume", List.of(
                    new SeedUom("ML", "Milliliter", BigDecimal.ONE, true, 0),
                    new SeedUom("CL", "Centiliter", new BigDecimal("10"), false, 2),
                    new SeedUom("L", "Liter", new BigDecimal("1000"), false, 3)
            )),
            new SeedCategory("LENGTH", "Length", List.of(
                    new SeedUom("CM", "Centimeter", BigDecimal.ONE, true, 1),
                    new SeedUom("M", "Meter", new BigDecimal("100"), false, 2),
                    new SeedUom("KM", "Kilometer", new BigDecimal("100000"), false, 3)
            )),
            new SeedCategory("AREA", "Area", List.of(
                    new SeedUom("M2", "Square meter", BigDecimal.ONE, true, 2),
                    new SeedUom("HA", "Hectare", new BigDecimal("10000"), false, 4)
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
