package com.hartwig.hmftools.sage.config;

import static com.hartwig.hmftools.common.cli.Configs.defaultIntValue;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.cli.Configs;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public interface SageConfig {

    Logger LOGGER = LogManager.getLogger(SageConfig.class);

    String THREADS = "threads";
    String REFERENCE = "reference";
    String REFERENCE_BAM = "reference_bam";
    String TUMOR = "tumor";
    String TUMOR_BAM = "tumor_bam";
    String REF_GENOME = "ref_genome";
    String OUTPUT_VCF = "out";
    String MIN_MAP_QUALITY = "min_map_quality";
    String MIN_BASE_QUALITY = "min_base_quality";
    String HIGH_CONFIDENCE_BED = "high_confidence_bed";
    String PANEL_BED = "panel_bed";
    String PANEL_ONLY = "panel_only";
    String GERMLINE_ONLY = "germline";
    String HOTSPOTS = "hotspots";
    String MAX_READ_DEPTH = "max_read_depth";
    String MAX_READ_DEPTH_PANEL = "max_read_depth_panel";
    String MAX_REALIGNMENT_DEPTH = "max_realignment_depth";

    int DEFAULT_THREADS = 2;
    int DEFAULT_MIN_MAP_QUALITY = 0;
    int DEFAULT_MIN_BASE_QUALITY = 13;
    int DEFAULT_MAX_READ_DEPTH = 1000;
    int DEFAULT_MAX_READ_DEPTH_PANEL = 100_000;
    int DEFAULT_MAX_REALIGNMENT_DEPTH = 1000;

    @NotNull
    static Options createOptions() {
        final Options options = new Options();
        options.addOption(THREADS, true, "Number of threads [" + DEFAULT_THREADS + "]");
        options.addOption(REFERENCE, true, "Name of reference sample");
        options.addOption(REFERENCE_BAM, true, "Path to reference bam file");
        options.addOption(TUMOR, true, "Name of tumor sample");
        options.addOption(TUMOR_BAM, true, "Path to tumor bam file");
        options.addOption(REF_GENOME, true, "Path to indexed ref genome fasta file");
        options.addOption(OUTPUT_VCF, true, "Path to output vcf");
        options.addOption(MIN_MAP_QUALITY, true, "Min map quality [" + DEFAULT_MIN_MAP_QUALITY + "]");
        options.addOption(MIN_BASE_QUALITY, true, "Min base quality [" + DEFAULT_MIN_BASE_QUALITY + "]");

        options.addOption(MAX_READ_DEPTH, true, "Max depth to look for evidence [" + DEFAULT_MAX_READ_DEPTH + "]");
        options.addOption(MAX_READ_DEPTH_PANEL, true, "Max depth to look for evidence [" + DEFAULT_MAX_READ_DEPTH_PANEL + "]");
        options.addOption(MAX_REALIGNMENT_DEPTH, true, "Max depth to check for realignment [" + DEFAULT_MAX_REALIGNMENT_DEPTH + "]");
        options.addOption(HIGH_CONFIDENCE_BED, true, "High confidence regions bed file");
        options.addOption(PANEL_BED, true, "Panel regions bed file");
        options.addOption(PANEL_ONLY, false, "Only examine panel for variants");
        options.addOption(GERMLINE_ONLY, false, "Germline only mode");
        options.addOption(HOTSPOTS, true, "Hotspots");
        FilterConfig.createOptions().getOptions().forEach(options::addOption);
        QualityConfig.createOptions().getOptions().forEach(options::addOption);

        return options;
    }

    @NotNull
    String version();

    int threads();

    @NotNull
    List<String> reference();

    @NotNull
    List<String> referenceBam();

    @NotNull
    String refGenome();

    @NotNull
    String highConfidenceBed();

    @NotNull
    List<String> tumor();

    @NotNull
    List<String> tumorBam();

    @NotNull
    String outputFile();

    @NotNull
    String panelBed();

    boolean panelOnly();

    @NotNull
    String hotspots();

    @NotNull
    FilterConfig filter();

    @NotNull
    QualityConfig qualityConfig();

    default int typicalReadLength() {
        return 151;
    }

    default int regionSliceSize() {
        return 500_000;
    }

    int minMapQuality();

    int minBaseQuality();

    int maxRealignmentDepth();

    int maxReadDepth();

    int maxReadDepthPanel();

    default int maxSkippedReferenceRegions() {
        return 50;
    }

    default int readContextFlankSize() {
        return 25;
    }

    @NotNull
    static SageConfig createConfig(@NotNull final String version, @NotNull final CommandLine cmd) throws ParseException {

        final int threads = defaultIntValue(cmd, THREADS, DEFAULT_THREADS);

        final List<String> referenceList = Lists.newArrayList();
        if (cmd.hasOption(REFERENCE)) {
            referenceList.addAll(Arrays.asList(cmd.getOptionValue(REFERENCE).split(",")));
        }

        final List<String> referenceBamList = Lists.newArrayList();
        if (cmd.hasOption(REFERENCE_BAM)) {
            referenceBamList.addAll(Arrays.asList(cmd.getOptionValue(REFERENCE_BAM, Strings.EMPTY).split(",")));
        }

        if (referenceList.size() != referenceBamList.size()) {
            throw new ParseException("Each reference sample must have matching bam");
        }

        for (String referenceBam : referenceBamList) {
            if (!new File(referenceBam).exists()) {
                throw new ParseException("Unable to locate reference bam " + referenceBam);
            }
        }

        final List<String> tumorList = Lists.newArrayList();
        if (cmd.hasOption(TUMOR)) {
            tumorList.addAll(Arrays.asList(cmd.getOptionValue(TUMOR).split(",")));
        }

        final List<String> tumorBamList = Lists.newArrayList();
        if (cmd.hasOption(TUMOR_BAM)) {
            tumorBamList.addAll(Arrays.asList(cmd.getOptionValue(TUMOR_BAM, Strings.EMPTY).split(",")));
        }

        if (tumorList.size() != tumorBamList.size()) {
            throw new ParseException("Each tumor sample must have matching bam");
        }

        for (String tumorBam : tumorBamList) {
            if (!new File(tumorBam).exists()) {
                throw new ParseException("Unable to locate tumor bam " + tumorBam);
            }
        }

        if (tumorList.isEmpty()) {
            throw new ParseException("At least one tumor must be supplied");
        }

        return ImmutableSageConfig.builder()
                .version(version)
                .outputFile(cmd.getOptionValue(OUTPUT_VCF))
                .threads(threads)
                .reference(referenceList)
                .referenceBam(referenceBamList)
                .tumor(tumorList)
                .tumorBam(tumorBamList)
                .refGenome(cmd.getOptionValue(REF_GENOME))
                .minMapQuality(defaultIntValue(cmd, MIN_MAP_QUALITY, DEFAULT_MIN_MAP_QUALITY))
                .minBaseQuality(defaultIntValue(cmd, MIN_BASE_QUALITY, DEFAULT_MIN_BASE_QUALITY))
                .maxReadDepth(defaultIntValue(cmd, MAX_READ_DEPTH, DEFAULT_MAX_READ_DEPTH))
                .maxReadDepthPanel(defaultIntValue(cmd, MAX_READ_DEPTH_PANEL, DEFAULT_MAX_READ_DEPTH_PANEL))
                .maxRealignmentDepth(defaultIntValue(cmd, MAX_REALIGNMENT_DEPTH, DEFAULT_MAX_REALIGNMENT_DEPTH))
                .filter(FilterConfig.createConfig(cmd))
                .panelBed(cmd.getOptionValue(PANEL_BED, Strings.EMPTY))
                .highConfidenceBed(cmd.getOptionValue(HIGH_CONFIDENCE_BED, Strings.EMPTY))
                .hotspots(cmd.getOptionValue(HOTSPOTS, Strings.EMPTY))
                .qualityConfig(QualityConfig.createConfig(cmd))
                .panelOnly(Configs.containsFlag(cmd, PANEL_ONLY))
                .build();
    }
}
