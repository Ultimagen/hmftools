package com.hartwig.hmftools.isofox.common;

import static com.hartwig.hmftools.common.utils.ConfigUtils.loadGeneIdsFile;
import static com.hartwig.hmftools.isofox.IsofoxConfig.GENE_ID_FILE;
import static com.hartwig.hmftools.isofox.IsofoxConfig.ISF_LOGGER;
import static com.hartwig.hmftools.isofox.IsofoxConstants.ENRICHED_GENE_BUFFER;
import static com.hartwig.hmftools.isofox.IsofoxConstants.EXCLUDED_REGION_1_REF_37;
import static com.hartwig.hmftools.isofox.IsofoxConstants.EXCLUDED_REGION_1_REF_38;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.ITEM_DELIM;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.SUB_ITEM_DELIM;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;
import com.hartwig.hmftools.isofox.IsofoxConstants;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class GeneRegionFilters
{
    public final List<String> SpecificChromosomes;
    public final List<ChrBaseRegion> SpecificRegions;

    public final List<String> RestrictedGeneIds; // specific set of genes to process
    public final List<String> ExcludedGeneIds; // genes to ignore
    public final List<String> EnrichedGeneIds; // genes to count by not fully process for any functional purpose
    public final ChrBaseRegion ExcludedRegion;

    public final List<ChrBaseRegion> RestrictedGeneRegions; // limit analysis to these regions only
    public final List<ChrBaseRegion> ExcludedGeneRegions; // exclude these regions based on geneId, enriched or excluded regions
    public final List<ChrBaseRegion> ImmuneGeneRegions;

    public final RefGenomeVersion mRefGenomeVersion;

    // config
    private static final String SPECIFIC_CHR = "specific_chr";
    private static final String SPECIFIC_REGIONS = "specific_regions";
    public static final String RESTRICTED_GENE_IDS = "restricted_gene_ids";
    public static final String EXCLUDED_GENE_ID_FILE = "excluded_gene_id_file";
    private static final String ENRICHED_GENE_IDS = "enriched_gene_ids";

    public GeneRegionFilters(final RefGenomeVersion refGenomeVersion)
    {
        RestrictedGeneIds = Lists.newArrayList();
        ExcludedGeneIds = Lists.newArrayList();
        EnrichedGeneIds = Lists.newArrayList();
        SpecificChromosomes = Lists.newArrayList();
        SpecificRegions = Lists.newArrayList();

        ExcludedGeneRegions = Lists.newArrayList();
        RestrictedGeneRegions = Lists.newArrayList();
        ImmuneGeneRegions = Lists.newArrayList();

        mRefGenomeVersion = refGenomeVersion;
        ExcludedRegion = refGenomeVersion.is37() ? EXCLUDED_REGION_1_REF_37 : EXCLUDED_REGION_1_REF_38;
    }

    public static void addLoggingOptions(final Options options)
    {
        options.addOption(RESTRICTED_GENE_IDS, true, "Optional list of Ensmebl GeneIds separated by ';'");
        options.addOption(EXCLUDED_GENE_ID_FILE, true, "Optional CSV file of genes to ignore");
        options.addOption(ENRICHED_GENE_IDS, true, "Optional list of geneIds to treat as enriched");
        options.addOption(SPECIFIC_CHR, true, "Restrict to chromosome(s) separated by ';'");
        options.addOption(SPECIFIC_REGIONS, true, "Restrict to regions(s) separated by ';' in format Chr:PosStart:PosEnd");
    }

    public void loadConfig(final CommandLine cmd)
    {
        if(cmd.hasOption(ENRICHED_GENE_IDS))
        {
            Arrays.stream(cmd.getOptionValue(ENRICHED_GENE_IDS).split(ITEM_DELIM)).forEach(x -> EnrichedGeneIds.add(x));
        }
        else
        {
            IsofoxConstants.populateEnrichedGeneIds(EnrichedGeneIds, mRefGenomeVersion);
        }

        IsofoxConstants.populateImmuneRegions(ImmuneGeneRegions, mRefGenomeVersion);

        if(cmd.hasOption(GENE_ID_FILE))
        {
            final String inputFile = cmd.getOptionValue(GENE_ID_FILE);
            RestrictedGeneIds.addAll(loadGeneIdsFile(inputFile));

            if(!RestrictedGeneIds.isEmpty())
            {
                ISF_LOGGER.info("file({}) loaded {} restricted genes", inputFile, RestrictedGeneIds.size());
            }
        }
        else if(cmd.hasOption(RESTRICTED_GENE_IDS))
        {
            RestrictedGeneIds.addAll(Arrays.stream(cmd.getOptionValue(RESTRICTED_GENE_IDS).split(ITEM_DELIM)).collect(Collectors.toList()));
        }

        if(cmd.hasOption(EXCLUDED_GENE_ID_FILE))
        {
            final String inputFile = cmd.getOptionValue(EXCLUDED_GENE_ID_FILE);
            ExcludedGeneIds.addAll(loadGeneIdsFile(inputFile));
            ISF_LOGGER.info("file({}) loaded {} excluded genes", inputFile, ExcludedGeneIds.size());
        }

        if(cmd.hasOption(SPECIFIC_REGIONS))
        {
            // expected format:
            final List<String> regionStrs = Arrays.stream(cmd.getOptionValue(SPECIFIC_REGIONS).split(ITEM_DELIM, -1)).collect(Collectors.toList());
            for(String regionStr : regionStrs)
            {
                final String[] items = regionStr.split(SUB_ITEM_DELIM);
                if(items.length == 3)
                {
                    ChrBaseRegion region = new ChrBaseRegion(items[0], Integer.parseInt(items[1]), Integer.parseInt(items[2]));

                    if(!region.isValid())
                    {
                        ISF_LOGGER.error("invalid specific region: {}", region);
                        continue;
                    }

                    ISF_LOGGER.info("filtering for specific region: {}", region);
                    SpecificRegions.add(region);

                    if(!SpecificChromosomes.contains(region.Chromosome))
                        SpecificChromosomes.add(region.Chromosome);
                }
                else
                {
                    ISF_LOGGER.error("invalid specific region({})", regionStr);
                }
            }
        }
        else if(cmd.hasOption(SPECIFIC_CHR))
        {
            final String chromosomes = cmd.getOptionValue(SPECIFIC_CHR);
            ISF_LOGGER.info("filtering for specific chromosomes: {}", chromosomes);
            SpecificChromosomes.addAll(Arrays.stream(chromosomes.split(ITEM_DELIM)).collect(Collectors.toList()));
        }
    }

    public boolean containsExcludedEnrichedGene(final String geneId)
    {
        return ExcludedGeneIds.contains(geneId) || EnrichedGeneIds.contains(geneId);
    }

    public boolean excludeChromosome(final String chromosome)
    {
        return !SpecificChromosomes.isEmpty() && !SpecificChromosomes.contains(chromosome);
    }

    public boolean skipRead(final String chromosome, int position)
    {
        if(!HumanChromosome.contains(chromosome))
            return true;

        if(!SpecificChromosomes.isEmpty() && !SpecificChromosomes.contains(chromosome))
            return true;
        else if(!SpecificRegions.isEmpty() && !SpecificRegions.stream().anyMatch(x -> x.containsPosition(chromosome, position)))
            return true;

        if(!RestrictedGeneRegions.isEmpty() && !RestrictedGeneRegions.stream().anyMatch(x -> x.containsPosition(chromosome, position)))
        {
            return true;
        }

        if(ExcludedGeneRegions.stream().anyMatch(x -> x.containsPosition(chromosome, position)))
        {
            return true;
        }

        return false;
    }

    public void buildGeneRegions(final EnsemblDataCache geneTransCache)
    {
        ExcludedGeneRegions.add(ExcludedRegion);

        EnrichedGeneIds.stream()
                .map(x -> geneTransCache.getGeneDataById(x))
                .filter(x -> x != null)
                .forEach(x -> ExcludedGeneRegions.add(new ChrBaseRegion(
                        x.Chromosome, x.GeneStart - ENRICHED_GENE_BUFFER, x.GeneEnd + ENRICHED_GENE_BUFFER)));

        ExcludedGeneIds.stream()
                .map(x -> geneTransCache.getGeneDataById(x))
                .filter(x -> x != null)
                .forEach(x -> ExcludedGeneRegions.add(new ChrBaseRegion(
                        x.Chromosome, x.GeneStart, x.GeneEnd)));

        RestrictedGeneIds.stream()
                .map(x -> geneTransCache.getGeneDataById(x))
                .filter(x -> x != null)
                .forEach(x -> RestrictedGeneRegions.add(new ChrBaseRegion(
                        x.Chromosome, x.GeneStart - 1000, x.GeneEnd + 1000)));
    }
}