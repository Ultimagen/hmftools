package com.hartwig.hmftools.patientreporter.cfreport.chapters;

import java.util.List;

import com.hartwig.hmftools.common.lims.Lims;
import com.hartwig.hmftools.common.lims.LimsGermlineReportingLevel;
import com.hartwig.hmftools.common.peach.PeachGenotype;
import com.hartwig.hmftools.common.purple.copynumber.ReportableGainLoss;
import com.hartwig.hmftools.common.utils.DataUtil;
import com.hartwig.hmftools.common.variant.structural.linx.LinxFusion;
import com.hartwig.hmftools.patientreporter.SampleReport;
import com.hartwig.hmftools.patientreporter.algo.GenomicAnalysis;
import com.hartwig.hmftools.patientreporter.cfreport.MathUtil;
import com.hartwig.hmftools.patientreporter.cfreport.ReportResources;
import com.hartwig.hmftools.patientreporter.cfreport.components.InlineBarChart;
import com.hartwig.hmftools.patientreporter.cfreport.components.TableUtil;
import com.hartwig.hmftools.patientreporter.cfreport.data.GainsAndLosses;
import com.hartwig.hmftools.patientreporter.cfreport.data.GeneDisruptions;
import com.hartwig.hmftools.patientreporter.cfreport.data.GeneFusions;
import com.hartwig.hmftools.patientreporter.cfreport.data.GeneUtil;
import com.hartwig.hmftools.patientreporter.cfreport.data.HomozygousDisruptions;
import com.hartwig.hmftools.patientreporter.cfreport.data.Peach;
import com.hartwig.hmftools.patientreporter.cfreport.data.SomaticVariants;
import com.hartwig.hmftools.patientreporter.cfreport.data.TumorPurity;
import com.hartwig.hmftools.patientreporter.germline.GermlineReportingModel;
import com.hartwig.hmftools.patientreporter.virusbreakend.ReportableVirusBreakendTotal;
import com.hartwig.hmftools.patientreporter.virusbreakend.ReportableVirusbreakend;
import com.hartwig.hmftools.protect.linx.ReportableGeneDisruption;
import com.hartwig.hmftools.protect.linx.ReportableHomozygousDisruption;
import com.hartwig.hmftools.protect.purple.ReportableVariant;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.property.TextAlignment;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class GenomicAlterationsChapter implements ReportChapter {

    // TODO Remove this toggle-off once we can remove position (blocked by DEV-810)
    private static final boolean DISPLAY_CLONAL_COLUMN = false;
    private static final float TABLE_SPACER_HEIGHT = 5;

    @NotNull
    private final GenomicAnalysis genomicAnalysis;
    @NotNull
    private final SampleReport sampleReport;
    @NotNull
    private final GermlineReportingModel germlineReportingModel;

    public GenomicAlterationsChapter(@NotNull final GenomicAnalysis genomicAnalysis, @NotNull final SampleReport sampleReport,
            @NotNull final GermlineReportingModel germlineReportingModel) {
        this.genomicAnalysis = genomicAnalysis;
        this.sampleReport = sampleReport;
        this.germlineReportingModel = germlineReportingModel;
    }

    @Override
    @NotNull
    public String name() {
        return "Genomic alteration details";
    }

    @Override
    public void render(@NotNull Document reportDocument) {
        boolean hasReliablePurity = genomicAnalysis.hasReliablePurity();

        reportDocument.add(createPloidyPloidyTable(genomicAnalysis.averageTumorPloidy(),
                genomicAnalysis.impliedPurity(),
                hasReliablePurity));

        reportDocument.add(createTumorVariantsTable(genomicAnalysis.reportableVariants(),
                hasReliablePurity,
                germlineReportingModel,
                sampleReport.germlineReportingLevel()));

        reportDocument.add(createGainsAndLossesTable(genomicAnalysis.gainsAndLosses(), hasReliablePurity));
        reportDocument.add(createFusionsTable(genomicAnalysis.geneFusions(), hasReliablePurity));
        reportDocument.add(createHomozygousDisruptionsTable(genomicAnalysis.homozygousDisruptions()));
        reportDocument.add(createDisruptionsTable(genomicAnalysis.geneDisruptions(), hasReliablePurity));
        reportDocument.add(createVirusBreakendsTable(genomicAnalysis.virusBreakends(), sampleReport.reportViralInsertions()));
        reportDocument.add(createPeachGenotypesTable(genomicAnalysis.peachGenotypes()));

    }

    @NotNull
    private static Table createPloidyPloidyTable(double ploidy, double purity, boolean hasReliablePurity) {
        String title = "Tumor purity & ploidy";

        Table contentTable = TableUtil.createReportContentSmallTable(new float[] { 60, 30, 30 }, new Cell[] {});

        double impliedPurityPercentage = MathUtil.mapPercentage(purity, TumorPurity.RANGE_MIN, TumorPurity.RANGE_MAX);
        renderTumorPurity(hasReliablePurity,
                DataUtil.formatPercentage(impliedPurityPercentage),
                purity,
                TumorPurity.RANGE_MIN,
                TumorPurity.RANGE_MAX,
                contentTable);

        contentTable.addCell(TableUtil.createContentCell("Average tumor ploidy"));
        contentTable.addCell(TableUtil.createContentCellPurityPloidy(GeneUtil.copyNumberToString(ploidy, hasReliablePurity)));
        contentTable.addCell(TableUtil.createContentCell(Strings.EMPTY));

        return TableUtil.createWrappingReportTable(title, contentTable);

    }

    @NotNull
    private static InlineBarChart createInlineBarChart(double value, double min, double max) {
        InlineBarChart chart = new InlineBarChart(value, min, max);
        chart.setWidth(41);
        chart.setHeight(6);
        return chart;
    }

    private static void renderTumorPurity(boolean hasReliablePurity, @NotNull String valueLabel, double value, double min, double max,
            @NotNull Table table) {

        String label = "Tumor purity";
        table.addCell(TableUtil.createContentCell(label));

        if (hasReliablePurity) {
            table.addCell(TableUtil.createContentCellPurityPloidy(valueLabel));
            table.addCell(TableUtil.createContentCell(createInlineBarChart(value, min, max)));
        } else {
            table.addCell(TableUtil.createContentCell(Lims.PURITY_NOT_RELIABLE_STRING));
        }
    }

    @NotNull
    private static Table createTumorVariantsTable(@NotNull List<ReportableVariant> reportableVariants, boolean hasReliablePurity,
            @NotNull GermlineReportingModel germlineReportingModel, @NotNull LimsGermlineReportingLevel germlineReportingLevel) {
        String title = "Tumor specific variants";
        if (reportableVariants.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable;
        if (DISPLAY_CLONAL_COLUMN) {
            contentTable = TableUtil.createReportContentTable(new float[] { 60, 70, 80, 70, 60, 40, 30, 60, 60, 50, 50 },
                    new Cell[] { TableUtil.createHeaderCell("Gene"), TableUtil.createHeaderCell("Position"),
                            TableUtil.createHeaderCell("Variant"), TableUtil.createHeaderCell("Protein"),
                            TableUtil.createHeaderCell("Read depth").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Copies").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("tVAF").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Biallelic").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Hotspot").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Clonal").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Driver").setTextAlignment(TextAlignment.CENTER) });
        } else {
            contentTable = TableUtil.createReportContentTable(new float[] { 60, 70, 80, 70, 60, 40, 30, 60, 60, 50 },
                    new Cell[] { TableUtil.createHeaderCell("Gene"), TableUtil.createHeaderCell("Position"),
                            TableUtil.createHeaderCell("Variant"), TableUtil.createHeaderCell("Protein"),
                            TableUtil.createHeaderCell("Read depth").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Copies").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("tVAF").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Biallelic").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Hotspot").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("Driver").setTextAlignment(TextAlignment.CENTER) });
        }

        for (ReportableVariant variant : SomaticVariants.sort(reportableVariants)) {
            contentTable.addCell(TableUtil.createContentCell(SomaticVariants.geneDisplayString(variant,
                    germlineReportingModel,
                    germlineReportingLevel)));
            contentTable.addCell(TableUtil.createContentCell(variant.gDNA()));
            contentTable.addCell(TableUtil.createContentCell(variant.canonicalHgvsCodingImpact()));
            contentTable.addCell(TableUtil.createContentCell(variant.canonicalHgvsProteinImpact()));
            contentTable.addCell(TableUtil.createContentCell(new Paragraph(
                    variant.alleleReadCount() + " / ").setFont(ReportResources.fontBold())
                    .add(new Text(String.valueOf(variant.totalReadCount())).setFont(ReportResources.fontRegular()))
                    .setTextAlignment(TextAlignment.CENTER)));
            contentTable.addCell(TableUtil.createContentCell(SomaticVariants.copyNumberString(variant.totalCopyNumber(), hasReliablePurity))
                    .setTextAlignment(TextAlignment.CENTER));
            contentTable.addCell(TableUtil.createContentCell(SomaticVariants.tVAFString(variant.tVAF(), hasReliablePurity))
                    .setTextAlignment(TextAlignment.CENTER));
            contentTable.addCell(TableUtil.createContentCell(SomaticVariants.biallelicString(variant.biallelic(), hasReliablePurity))
                    .setTextAlignment(TextAlignment.CENTER));
            contentTable.addCell(TableUtil.createContentCell(SomaticVariants.hotspotString(variant.hotspot()))
                    .setTextAlignment(TextAlignment.CENTER));
            if (DISPLAY_CLONAL_COLUMN) {
                contentTable.addCell(TableUtil.createContentCell(SomaticVariants.clonalString(variant.clonalLikelihood()))
                        .setTextAlignment(TextAlignment.CENTER));
            }
            contentTable.addCell(TableUtil.createContentCell(variant.driverLikelihoodInterpretation().display()))
                    .setTextAlignment(TextAlignment.CENTER);
        }

        if (SomaticVariants.hasNotifiableGermlineVariant(reportableVariants, germlineReportingModel, germlineReportingLevel)) {
            contentTable.addCell(TableUtil.createLayoutCell(1, contentTable.getNumberOfColumns())
                    .add(new Paragraph("\n# Marked variant(s) are also present in the germline of the patient. Referral to a genetic "
                            + "specialist should be advised.").addStyle(ReportResources.subTextStyle())));
        }

        return TableUtil.createWrappingReportTable(title, contentTable);
    }

    @NotNull
    private static Table createGainsAndLossesTable(@NotNull List<ReportableGainLoss> gainsAndLosses, boolean hasReliablePurity) {
        String title = "Tumor specific gains & losses";
        if (gainsAndLosses.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 80, 80, 100, 80, 45, 120, 50 },
                new Cell[] { TableUtil.createHeaderCell("Chromosome"), TableUtil.createHeaderCell("Region"),
                        TableUtil.createHeaderCell("Gene"), TableUtil.createHeaderCell("Type"), TableUtil.createHeaderCell("Copies"),
                        TableUtil.createHeaderCell("Chromosome arm copies").setTextAlignment(TextAlignment.CENTER),
                        TableUtil.createHeaderCell("") });

        List<ReportableGainLoss> sortedGainsAndLosses = GainsAndLosses.sort(gainsAndLosses);
        for (ReportableGainLoss gainLoss : sortedGainsAndLosses) {
            contentTable.addCell(TableUtil.createContentCell(gainLoss.chromosome()));
            contentTable.addCell(TableUtil.createContentCell(gainLoss.chromosomeBand()));
            contentTable.addCell(TableUtil.createContentCell(gainLoss.gene()));
            contentTable.addCell(TableUtil.createContentCell(gainLoss.interpretation().display()));
            contentTable.addCell(TableUtil.createContentCell(hasReliablePurity ? String.valueOf(gainLoss.copies()) : DataUtil.NA_STRING)
                    .setTextAlignment(TextAlignment.CENTER));
            contentTable.addCell(TableUtil.createContentCell(hasReliablePurity ? "0" : DataUtil.NA_STRING)
                    .setTextAlignment(TextAlignment.CENTER));
            contentTable.addCell(TableUtil.createContentCell(""));
        }

        return TableUtil.createWrappingReportTable(title, contentTable);
    }

    @NotNull
    private static Table createHomozygousDisruptionsTable(@NotNull List<ReportableHomozygousDisruption> homozygousDisruptions) {
        String title = "Tumor specific homozygous disruptions";
        if (homozygousDisruptions.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 80, 80, 100, 80 },
                new Cell[] { TableUtil.createHeaderCell("Chromosome"), TableUtil.createHeaderCell("Region"),
                        TableUtil.createHeaderCell("Gene"), TableUtil.createHeaderCell("") });

        for (ReportableHomozygousDisruption homozygousDisruption : HomozygousDisruptions.sort(homozygousDisruptions)) {
            contentTable.addCell(TableUtil.createContentCell(homozygousDisruption.chromosome()));
            contentTable.addCell(TableUtil.createContentCell(homozygousDisruption.chromosomeBand()));
            contentTable.addCell(TableUtil.createContentCell(homozygousDisruption.gene()));
            contentTable.addCell(TableUtil.createContentCell(""));
        }

        return TableUtil.createWrappingReportTable(title, contentTable);
    }

    @NotNull
    private static Table createFusionsTable(@NotNull List<LinxFusion> fusions, boolean hasReliablePurity) {
        String title = "Tumor specific gene fusions";
        if (fusions.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 80, 80, 80, 40, 40, 40, 65, 40 },
                new Cell[] { TableUtil.createHeaderCell("Fusion"), TableUtil.createHeaderCell("5' Transcript"),
                        TableUtil.createHeaderCell("3' Transcript"), TableUtil.createHeaderCell("5' End"),
                        TableUtil.createHeaderCell("3' Start"), TableUtil.createHeaderCell("Copies").setTextAlignment(TextAlignment.CENTER),
                        TableUtil.createHeaderCell("Phasing").setTextAlignment(TextAlignment.CENTER),
                        TableUtil.createHeaderCell("Driver").setTextAlignment(TextAlignment.CENTER), });

        for (LinxFusion fusion : GeneFusions.sort(fusions)) {
            contentTable.addCell(TableUtil.createContentCell(GeneFusions.name(fusion)));
            contentTable.addCell(TableUtil.createContentCell(new Paragraph(fusion.geneTranscriptStart()))
                    .addStyle(ReportResources.dataHighlightLinksStyle())
                    .setAction(PdfAction.createURI(GeneFusions.transcriptUrl(fusion.geneTranscriptStart()))));
            contentTable.addCell(TableUtil.createContentCell(new Paragraph(fusion.geneTranscriptEnd()).addStyle(ReportResources.dataHighlightLinksStyle())
                    .setAction(PdfAction.createURI(GeneFusions.transcriptUrl(fusion.geneTranscriptEnd())))));
            contentTable.addCell(TableUtil.createContentCell(fusion.geneContextStart()));
            contentTable.addCell(TableUtil.createContentCell(fusion.geneContextEnd()));
            contentTable.addCell(TableUtil.createContentCell(GeneUtil.copyNumberToString(fusion.junctionCopyNumber(), hasReliablePurity))
                    .setTextAlignment(TextAlignment.CENTER));
            contentTable.addCell(TableUtil.createContentCell(fusion.phased().display()).setTextAlignment(TextAlignment.CENTER));
            contentTable.addCell(TableUtil.createContentCell(fusion.likelihood().display()).setTextAlignment(TextAlignment.CENTER));
        }

        return TableUtil.createWrappingReportTable(title, contentTable);
    }

    @NotNull
    private static Table createDisruptionsTable(@NotNull List<ReportableGeneDisruption> disruptions, boolean hasReliablePurity) {
        String title = "Tumor specific gene disruptions";
        if (disruptions.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 60, 80, 100, 50, 85, 85 },
                new Cell[] { TableUtil.createHeaderCell("Location"), TableUtil.createHeaderCell("Gene"),
                        TableUtil.createHeaderCell("Disrupted range"), TableUtil.createHeaderCell("Type"),
                        TableUtil.createHeaderCell("Disrupted copies").setTextAlignment(TextAlignment.CENTER),
                        TableUtil.createHeaderCell("Undisrupted copies").setTextAlignment(TextAlignment.CENTER) });

        for (ReportableGeneDisruption disruption : GeneDisruptions.sort(disruptions)) {
            contentTable.addCell(TableUtil.createContentCell(disruption.location()));
            contentTable.addCell(TableUtil.createContentCell(disruption.gene()));
            contentTable.addCell(TableUtil.createContentCell(disruption.range()));
            contentTable.addCell(TableUtil.createContentCell(disruption.type()));
            contentTable.addCell(TableUtil.createContentCell(GeneUtil.copyNumberToString(disruption.junctionCopyNumber(),
                    hasReliablePurity)).setTextAlignment(TextAlignment.CENTER));
            contentTable.addCell(TableUtil.createContentCell(GeneUtil.copyNumberToString(disruption.undisruptedCopyNumber(),
                    hasReliablePurity)).setTextAlignment(TextAlignment.CENTER));
        }
        return TableUtil.createWrappingReportTable(title, contentTable);
    }

    @NotNull
    private static Table createVirusBreakendsTable(@NotNull ReportableVirusBreakendTotal virusBreakends, boolean reportViralInsertions) {
        String title = "Tumor specific viral insertions";

        if (!reportViralInsertions) {
            return TableUtil.createNAReportTable(title);
        } else if (virusBreakends.reportableVirussen().isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        } else {
            Table contentTable = TableUtil.createReportContentTable(new float[] { 120, 120, 200 },
                    new Cell[] { TableUtil.createHeaderCell("Virus"),
                            TableUtil.createHeaderCell("Number of detected integration sites").setTextAlignment(TextAlignment.CENTER),
                            TableUtil.createHeaderCell("") });

            for (ReportableVirusbreakend virusBreakend : virusBreakends.reportableVirussen()) {
                contentTable.addCell(TableUtil.createContentCell(virusBreakend.virusName()));
                contentTable.addCell(TableUtil.createContentCell(Integer.toString(virusBreakend.integrations()))
                        .setTextAlignment(TextAlignment.CENTER));
                contentTable.addCell(TableUtil.createContentCell(""));
            }

            return TableUtil.createWrappingReportTable(title, contentTable);
        }
    }

    @NotNull
    private static Table createPeachGenotypesTable(@NotNull List<PeachGenotype> peachGenotypes) {
        String title = "Pharmacogenetics";
        if (peachGenotypes.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 60, 60, 60, 100, 60 },
                new Cell[] { TableUtil.createHeaderCell("Gene"), TableUtil.createHeaderCell("Genotype"),
                        TableUtil.createHeaderCell("Function"), TableUtil.createHeaderCell("Linked drugs"),
                        TableUtil.createHeaderCell("Source").setTextAlignment(TextAlignment.CENTER) });

        for (PeachGenotype peachGenotype : Peach.sort(peachGenotypes)) {
            contentTable.addCell(TableUtil.createContentCell(peachGenotype.gene()));
            contentTable.addCell(TableUtil.createContentCell(peachGenotype.haplotype()));
            contentTable.addCell(TableUtil.createContentCell(peachGenotype.function()));
            contentTable.addCell(TableUtil.createContentCell(peachGenotype.linkedDrugs()));
            contentTable.addCell(TableUtil.createContentCell(new Paragraph(Peach.sourceName(peachGenotype.urlPrescriptionInfo())).addStyle(
                    ReportResources.dataHighlightLinksStyle()))
                    .setAction(PdfAction.createURI(Peach.url(peachGenotype.urlPrescriptionInfo())))
                    .setTextAlignment(TextAlignment.CENTER));
        }
        return TableUtil.createWrappingReportTable(title, contentTable);
    }
}
