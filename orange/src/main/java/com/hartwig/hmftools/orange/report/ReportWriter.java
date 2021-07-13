package com.hartwig.hmftools.orange.report;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import com.hartwig.hmftools.orange.algo.OrangeReport;
import com.hartwig.hmftools.orange.report.chapters.ClinicalEvidenceChapter;
import com.hartwig.hmftools.orange.report.chapters.CohortComparisonChapter;
import com.hartwig.hmftools.orange.report.chapters.FrontPageChapter;
import com.hartwig.hmftools.orange.report.chapters.GermlineFindingsChapter;
import com.hartwig.hmftools.orange.report.chapters.ImmunologyChapter;
import com.hartwig.hmftools.orange.report.chapters.QualityControlChapter;
import com.hartwig.hmftools.orange.report.chapters.ReportChapter;
import com.hartwig.hmftools.orange.report.chapters.SomaticFindingsChapter;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.property.AreaBreakType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ReportWriter {

    private static final Logger LOGGER = LogManager.getLogger(ReportWriter.class);

    private final boolean writeToFile;
    @NotNull
    private final String outputDir;
    @NotNull
    private final ReportConfig reportConfig;

    public ReportWriter(final boolean writeToFile, @NotNull final String outputDir, @NotNull final ReportConfig reportConfig) {
        this.writeToFile = writeToFile;
        this.outputDir = outputDir;
        this.reportConfig = reportConfig;
    }

    public void write(@NotNull OrangeReport report) throws IOException {
        ReportChapter[] chapters = new ReportChapter[] { new FrontPageChapter(report, reportConfig.reportGermline()),
                new ClinicalEvidenceChapter(report, reportConfig), new SomaticFindingsChapter(report),
                new GermlineFindingsChapter(report, reportConfig.reportGermline()), new ImmunologyChapter(report),
                new CohortComparisonChapter(report), new QualityControlChapter(report) };
        writeReport(report, chapters);
    }

    private void writeReport(@NotNull OrangeReport report, @NotNull ReportChapter[] chapters) throws IOException {
        Document doc = initializeReport(report);
        PdfDocument pdfDocument = doc.getPdfDocument();

        PageEventHandler pageEventHandler = new PageEventHandler(report);
        pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, pageEventHandler);

        for (int i = 0; i < chapters.length; i++) {
            ReportChapter chapter = chapters[i];

            pageEventHandler.chapterTitle(chapter.name());
            pageEventHandler.resetChapterPageCounter();

            if (i > 0) {
                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            }
            chapter.render(doc);
        }

        pageEventHandler.writeDynamicTextParts(doc.getPdfDocument());

        doc.close();
        pdfDocument.close();
    }

    @NotNull
    private Document initializeReport(@NotNull OrangeReport report) throws IOException {
        PdfWriter writer;
        if (writeToFile) {
            String outputFilePath = outputDir + File.separator + report.sampleId() + ".orange.pdf";
            LOGGER.info("Writing report to {}", outputFilePath);
            writer = new PdfWriter(outputFilePath);
        } else {
            LOGGER.info("Generating report {}", report);
            writer = new PdfWriter(new ByteArrayOutputStream());
        }

        PdfDocument pdf = new PdfDocument(writer);
        pdf.setDefaultPageSize(PageSize.A4);
        pdf.getDocumentInfo().setTitle(ReportResources.METADATA_TITLE);
        pdf.getDocumentInfo().setAuthor(ReportResources.METADATA_AUTHOR);

        Document document = new Document(pdf);
        document.setMargins(ReportResources.PAGE_MARGIN_TOP,
                ReportResources.PAGE_MARGIN_RIGHT,
                ReportResources.PAGE_MARGIN_BOTTOM,
                ReportResources.PAGE_MARGIN_LEFT);

        return document;
    }
}
