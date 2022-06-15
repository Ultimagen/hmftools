package com.hartwig.hmftools.common.wildtype;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGene;
import com.hartwig.hmftools.common.linx.ReportableGeneDisruption;
import com.hartwig.hmftools.common.linx.ReportableHomozygousDisruption;
import com.hartwig.hmftools.common.purple.PurpleQCStatus;
import com.hartwig.hmftools.common.purple.interpretation.GainLoss;
import com.hartwig.hmftools.common.sv.linx.LinxFusion;
import com.hartwig.hmftools.common.variant.ReportableVariant;

import org.jetbrains.annotations.NotNull;

public class WildTypeFactory {

    public WildTypeFactory() {
    }

    public static List<WildTypeGene> filterQCWildTypes(@NotNull Set<PurpleQCStatus> purpleQCStatus,
            @NotNull List<WildTypeGene> wildTypeGenes) {
        List<WildTypeGene> wildTypeGenesFilter = Lists.newArrayList();
        if (!purpleQCStatus.contains(PurpleQCStatus.FAIL_NO_TUMOR) && !purpleQCStatus.contains(PurpleQCStatus.WARN_LOW_PURITY)) {
            wildTypeGenesFilter = wildTypeGenes;
        }
        return wildTypeGenesFilter;
    }

    public static List<WildTypeGene> determineWildTypeGenes(@NotNull List<ReportableVariant> reportableGermlineVariant,
            @NotNull List<ReportableVariant> reportableSomaticVariant, @NotNull List<GainLoss> reportableSomaticGainsLosses,
            @NotNull List<LinxFusion> reportableFusions, @NotNull List<ReportableHomozygousDisruption> homozygousDisruptions,
            @NotNull List<ReportableGeneDisruption> geneDisruptions, @NotNull List<DriverGene> driverGenes) {

        List<WildTypeGene> wildTypeGenes = Lists.newArrayList();

        for (DriverGene driverGene : driverGenes) {

            boolean hasSomaticVariant = false;
            boolean hasGermlineVariant = false;
            boolean hasSomaticGainLoss = false;
            boolean hasFusion = false;
            boolean hasHomozygousDisruption = false;
            boolean hasGeneDisruption = false;

            for (ReportableVariant somaticVariant : reportableSomaticVariant) {
                if (driverGene.gene().equals(somaticVariant.gene())) {
                    hasSomaticVariant = true;
                }
            }

            for (ReportableVariant germlineVariant : reportableGermlineVariant) {
                if (driverGene.gene().equals(germlineVariant.gene())) {
                    hasGermlineVariant = true;
                }
            }

            for (GainLoss gainLoss : reportableSomaticGainsLosses) {
                if (driverGene.gene().equals(gainLoss.gene())) {
                    hasSomaticGainLoss = true;
                }
            }

            for (LinxFusion fusion : reportableFusions) {
                if (driverGene.gene().equals(fusion.geneStart())) {
                    hasFusion = true;
                }

                if (driverGene.gene().equals(fusion.geneEnd())) {
                    hasFusion = true;
                }
            }

            for (ReportableHomozygousDisruption homozygousDisruption : homozygousDisruptions) {
                if (driverGene.gene().equals(homozygousDisruption.gene())) {
                    hasFusion = true;
                }
            }

            for (ReportableGeneDisruption geneDisruption : geneDisruptions) {
                if (driverGene.gene().equals(geneDisruption.gene())) {
                    hasGeneDisruption = true;
                }
            }

            if (!hasSomaticVariant && !hasGermlineVariant && !hasSomaticGainLoss && !hasFusion && !hasHomozygousDisruption
                    && !hasGeneDisruption) {
                wildTypeGenes.add(ImmutableWildTypeGene.builder().gene(driverGene.gene()).build());
            }
        }

        return wildTypeGenes;
    }
}