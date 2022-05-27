package com.hartwig.hmftools.orange.report.tables;

import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;

import com.hartwig.hmftools.common.isofox.IsofoxData;
import com.hartwig.hmftools.common.rna.RnaFusion;
import com.hartwig.hmftools.common.sv.linx.FusionLikelihoodType;
import com.hartwig.hmftools.common.sv.linx.LinxFusion;
import com.hartwig.hmftools.orange.report.ReportResources;
import com.hartwig.hmftools.orange.report.util.Cells;
import com.hartwig.hmftools.orange.report.util.Tables;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.UnitValue;

import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FusionTable {

    private static final DecimalFormat SINGLE_DIGIT = ReportResources.decimalFormat("#0.0");

    private FusionTable() {
    }

    @NotNull
    public static Table build(@NotNull String title, float width, @NotNull List<LinxFusion> fusions, @Nullable IsofoxData isofox) {
        if (fusions.isEmpty()) {
            return Tables.createEmpty(title, width);
        }

        Table table = Tables.createContent(width,
                new float[] { 1, 5 },
                new Cell[] { Cells.createHeader("Fusion"), Cells.createHeader("Details") });

        for (LinxFusion fusion : sortLinx(fusions)) {
            table.addCell(Cells.createContent(fusion.name()));

            Table details = new Table(UnitValue.createPercentArray(new float[] { 1, 3 }));
            details.addCell(Cells.createKey("5' End"));
            details.addCell(Cells.createValue(fiveEndString(fusion)));
            details.addCell(Cells.createKey("3' Start"));
            details.addCell(Cells.createValue(threeStartString(fusion)));
            details.addCell(Cells.createKey("Junction CN"));
            details.addCell(Cells.createValue(SINGLE_DIGIT.format(fusion.junctionCopyNumber())));
            details.addCell(Cells.createKey("RNA fragment support"));
            details.addCell(Cells.createValue(rnaFragmentSupportTable(isofox, fusion)).setKeepTogether(true));
            details.addCell(Cells.createKey("Phasing"));
            details.addCell(Cells.createValue(fusion.phased().displayStr()));
            details.addCell(Cells.createKey("Reported type (DL)"));
            details.addCell(Cells.createValue(fusion.reportedType() + " (" + fusion.likelihood().displayStr() + ")"));
            details.addCell(Cells.createKey("Chain links (terminated?)"));
            details.addCell(Cells.createValue(fusion.chainLinks() + (fusion.chainTerminated() ? " (Yes)" : " (No)")));
            details.addCell(Cells.createKey("Domains kept"));
            details.addCell(Cells.createValue(!fusion.domainsKept().isEmpty() ? fusion.domainsKept() : "-"));
            details.addCell(Cells.createKey("Domains lost"));
            details.addCell(Cells.createValue(!fusion.domainsLost().isEmpty() ? fusion.domainsLost() : "-"));
            // Need to keep this details table to avoid page-wrapping that cuts through the middle of a single fusion
            table.addCell(Cells.createContent(details).setKeepTogether(true));
        }

        return Tables.createWrapping(table, title);
    }

    @NotNull
    private static String fiveEndString(@NotNull LinxFusion fusion) {
        return fusion.geneStart() + " " + fusion.geneContextStart() + " (" + fusion.geneTranscriptStart() + ")";
    }

    @NotNull
    private static String threeStartString(@NotNull LinxFusion fusion) {
        return fusion.geneEnd() + " " + fusion.geneContextEnd() + " (" + fusion.geneTranscriptEnd() + ")";
    }

    @NotNull
    private static IBlockElement rnaFragmentSupportTable(@Nullable IsofoxData isofox, @NotNull LinxFusion fusion) {
        if (isofox == null) {
            return new Paragraph("-");
        }

        List<RnaFusion> matches = Lists.newArrayList();
        for (RnaFusion rnaFusion : isofox.fusions()) {
            if (rnaFusion.name().equals(fusion.name())) {
                matches.add(rnaFusion);
            }
        }

        if (matches.isEmpty()) {
            return new Paragraph("None");
        }

        Table fragmentSupportTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }));
        for (RnaFusion rnaFusion : max5(sortRna(matches))) {
            String up = rnaFusion.chromosomeUp() + ":" + rnaFusion.positionUp();
            String down = rnaFusion.chromosomeDown() + ":" + rnaFusion.positionDown();
            fragmentSupportTable.addCell(Cells.createKey(up + "-" + down));

            String split = rnaFusion.splitFragments() + " split";
            String realigned = rnaFusion.realignedFrags() + " realig.";
            String discord = rnaFusion.discordantFrags() + " discord.";
            fragmentSupportTable.addCell(Cells.createValue(split + " / " + realigned + " / " + discord + " fragments"));
        }

        return fragmentSupportTable;
    }

    @NotNull
    private static List<RnaFusion> sortRna(@NotNull List<RnaFusion> rnaFusions) {
        return rnaFusions.stream().sorted((fusion1, fusion2) -> {
            int sumFragments1 = fusion1.splitFragments() + fusion1.realignedFrags() + fusion1.discordantFrags();
            int sumFragments2 = fusion2.splitFragments() + fusion2.realignedFrags() + fusion2.discordantFrags();
            return Integer.compare(sumFragments2, sumFragments1);
        }).collect(Collectors.toList());
    }

    @NotNull
    private static List<LinxFusion> sortLinx(@NotNull List<LinxFusion> fusions) {
        return fusions.stream().sorted((fusion1, fusion2) -> {
            if (fusion1.likelihood() == fusion2.likelihood()) {
                if (fusion1.geneStart().equals(fusion2.geneStart())) {
                    return fusion1.geneEnd().compareTo(fusion2.geneEnd());
                } else {
                    return fusion1.geneStart().compareTo(fusion2.geneStart());
                }
            } else {
                return fusion1.likelihood() == FusionLikelihoodType.HIGH ? -1 : 1;
            }
        }).collect(Collectors.toList());
    }

    @NotNull
    private static <T> List<T> max5(@NotNull List<T> elements) {
        return elements.subList(0, Math.min(5, elements.size()));
    }
}
