package com.hartwig.hmftools.sage.dedup;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.region.BaseRegion.positionsOverlap;
import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;
import static com.hartwig.hmftools.sage.SageConstants.INDEL_DEDUP_MAX_DIST_THRESHOLD;
import static com.hartwig.hmftools.sage.filter.SoftFilter.MIN_AVG_BASE_QUALITY;
import static com.hartwig.hmftools.sage.filter.SoftFilter.MIN_TUMOR_QUAL;
import static com.hartwig.hmftools.sage.filter.SoftFilter.STRAND_BIAS;
import static com.hartwig.hmftools.sage.vcf.VariantVCF.DEDUP_INDEL_FILTER;
import static com.hartwig.hmftools.sage.vcf.VariantVCF.DEDUP_INDEL_FILTER_OLD;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeInterface;
import com.hartwig.hmftools.sage.common.IndexedBases;
import com.hartwig.hmftools.sage.common.SageVariant;
import com.hartwig.hmftools.sage.evidence.ReadContextCounter;

public class IndelDeduper
{
    /* TO-DO: describe the logic

    */

    private final RefGenomeInterface mRefGenome;

    public IndelDeduper(final RefGenomeInterface refGenome)
    {
        mRefGenome = refGenome;
    }

    public void dedupVariants(final List<SageVariant> variants)
    {
        List<Variant> indels = Lists.newArrayList();
        List<Variant> candidates = Lists.newArrayList();

        for(SageVariant variant : variants)
        {
            if(!variant.hasLocalPhaseSets() || !hasValidFilters(variant))
                continue;

            Variant adjustedVariant = new Variant(variant);

            if(variant.isIndel())
                indels.add(adjustedVariant);

            candidates.add(adjustedVariant);
        }

        if(indels.isEmpty())
            return;

        // if(indels.size() + candidates.size() < 2)
        //    return;

        if(indels.size() > 1)
            Collections.sort(indels, new IndelScoreSorter());

        if(candidates.size() > 1)
            Collections.sort(candidates, new VariantFlankStartSorter());

        while(!indels.isEmpty())
        {
            Variant indel = indels.get(0);
            indels.remove(0);

            List<Variant> dedupGroup = findDedupGroup(indel, candidates);

            // de-dup this group
            dedupIndelGroup(indel, dedupGroup);

            // remove any filtered variants from the main candidate list and the indel list
            for(Variant variant : dedupGroup)
            {
                if(variant.Variant.filters().contains(DEDUP_INDEL_FILTER))
                {
                    candidates.remove(variant);

                    if(variant.Variant.isIndel())
                    {
                        indels.remove(variant);
                    }
                }
            }
        }
    }

    private static final Set<String> PERMITTED_FILTERS = Sets.newHashSet(
            MIN_TUMOR_QUAL.filterName(),
            MIN_AVG_BASE_QUALITY.filterName(),
            STRAND_BIAS.filterName(),
            DEDUP_INDEL_FILTER_OLD); // temporary

    private static boolean hasValidFilters(final SageVariant variant)
    {
        if(variant.isPassing())
            return true;

        return variant.filters().stream().allMatch(x -> PERMITTED_FILTERS.contains(x));
    }

    private static final int INDEL_DEDUP_PHASED_DIST_THRESHOLD = 60;

    private List<Variant> findDedupGroup(final Variant indel, final List<Variant> candidates)
    {
        List<Variant> dedupGroup = Lists.newArrayList();

        int indelFlankPosStart = indel.FlankPosStart;
        int indelFlankPosEnd = indel.FlankPosEnd;

        for(Variant variant : candidates)
        {
            if(variant == indel)
                continue;

            if(variant.FlankPosEnd < indelFlankPosStart - INDEL_DEDUP_PHASED_DIST_THRESHOLD)
                continue;

            if(variant.FlankPosStart > indelFlankPosEnd + INDEL_DEDUP_PHASED_DIST_THRESHOLD)
                break;

            if(!indel.Variant.hasMatchingLps(variant.Variant.localPhaseSets()))
                continue;

            if(positionsOverlap(indelFlankPosStart, indelFlankPosEnd, variant.FlankPosStart, variant.FlankPosEnd)
            || variant.ReadCounter.maxDistanceFromEdge() < INDEL_DEDUP_MAX_DIST_THRESHOLD)
            {
                dedupGroup.add(variant);
            }
        }

        return dedupGroup;
    }

    private void dedupIndelGroup(final Variant indel, final List<Variant> dedupGroup)
    {
        SG_LOGGER.trace("indel({}) with {} other variants", indel, dedupGroup.size() - 1);

        List<Variant> dedupedVariants = Lists.newArrayListWithCapacity(dedupGroup.size());

        int indelPosStart = indel.position();
        int indelPosEnd = indel.positionEnd();

        // any DEL overlapping the main INDEL can be de-duped immediately
        int index = 0;
        while(index < dedupGroup.size())
        {
            Variant variant = dedupGroup.get(index);

            if(variant.Variant.isDelete() && positionsOverlap(indelPosStart, indelPosEnd, variant.position(), variant.positionEnd()))
            {
                dedupGroup.remove(index);
                dedupedVariants.add(variant);
            }
            else
            {
                ++index;
            }
        }

        String refBases = mRefGenome.getBaseString(indel.Variant.chromosome(), indel.FlankPosStart, indel.FlankPosEnd);

        IndexedBases indelReadContextBases = indel.ReadCounter.readContext().indexedBases();
        String indelAltBases = indelReadContextBases.fullString();

        // first test just adding the indel back to the ref and checking for a match
        int indelAltIndex = indel.position() - indel.FlankPosStart;

        String refPlusIndelBases = refBases.substring(0, indelAltIndex) + indel.alt();

        if(indel.Variant.isInsert())
            refPlusIndelBases += refBases.substring(indelAltIndex + 1);
        else
            refPlusIndelBases += refBases.substring(indelAltIndex + 1 + indel.ref().length() - 1);

        if(indelAltBases.equals(refPlusIndelBases))
        {
            dedupedVariants.addAll(dedupGroup);
        }
        else
        {


        }

        dedupedVariants.forEach(x -> x.Variant.filters().add(DEDUP_INDEL_FILTER));
    }

    private void checkDedupCombinations(
            final Variant indel, final List<Variant> dedupGroup, final List<Variant> dedupedVariants,
            final String indelAltBases, final String refBases, final int refPosStart, final int refPosEnd)
    {
        // check for combinations of variants which together explain the sum of differences
        // variants are taken from within the flank positions of the indel



    }

    public static String buildAltBasesFromVariants(
            final String refBases, final int refPosStart, final int refPosEnd, final List<Variant> variants)
    {
        Collections.sort(variants, new VariantPositionStartSorter());

        String altBases = refBases;

        // add variants into the ref bases from right to left so the earlier positions remain unafffected by the added alts
        for(Variant variant : variants)
        {
            String newAltBases = altBases.substring(0, variant.position());

            newAltBases += variant.alt();

            int refBaseGap = variant.Variant.isInsert() ? 1 : variant.ref().length();

            newAltBases += altBases.substring(variant.position() + refBaseGap);

            altBases = newAltBases;
        }

        return altBases;
    }

    public static class VariantFlankStartSorter implements Comparator<Variant>
    {
        public int compare(final Variant first, final Variant second)
        {
            if(first.FlankPosStart == second.FlankPosStart)
                return 0;

            return first.FlankPosStart < second.FlankPosStart ? -1 : 1;
        }
    }

    public static class VariantPositionStartSorter implements Comparator<Variant>
    {
        public int compare(final Variant first, final Variant second)
        {
            if(first.position() == second.position())
                return 0;

            return first.position() < second.position() ? -1 : 1;
        }
    }

    public static class IndelScoreSorter implements Comparator<Variant>
    {
        public int compare(final Variant first, final Variant second)
        {
            if(first.IndelScore == first.IndelScore)
                return 0;

            return first.IndelScore > first.IndelScore ? -1 : 1;
        }
    }

    private static final int INDEL_LENGTH_FACTOR = 8;
    private static final int MIN_EVENTS_FACTOR = 12;

    private class Variant
    {
        public final SageVariant Variant;
        public final ReadContextCounter ReadCounter;
        public final int IndelScore;
        public final int FlankPosStart;
        public final int FlankPosEnd;

        public Variant(final SageVariant variant)
        {
            Variant = variant;
            ReadCounter = variant.tumorReadCounters().get(0);

            if(Variant.isIndel())
            {
                IndelScore = ReadCounter.indelLength() * INDEL_LENGTH_FACTOR
                        + ReadCounter.maxDistanceFromEdge()
                        - MIN_EVENTS_FACTOR * ReadCounter.minNumberOfEvents();
            }
            else
            {
                IndelScore = 0;
            }

            // flank positions are estimate since they aren't aware of other variants in their core and flanks
            final IndexedBases indexedBases = ReadCounter.readContext().indexedBases();

            int leftFlankFromCore = indexedBases.Index - indexedBases.LeftFlankIndex;
            FlankPosStart = variant.position() - leftFlankFromCore;

            int rightFlankFromCore = indexedBases.RightFlankIndex - indexedBases.Index;
            FlankPosEnd = positionEnd() + rightFlankFromCore;
        }

        public String ref()
        {
            return Variant.ref();
        }
        public String alt()
        {
            return Variant.alt();
        }

        public int position() { return Variant.position(); }

        public int positionEnd()
        {
            if(Variant.isSnv())
                return position();
            else if(Variant.isInsert())
                return position() + 1;
            else // deletes and MNVs
                return position() + Variant.ref().length() - 1;
        }

        public String toString() { return format("%s flankPos(%d - %d) score(%d)",
                Variant.toString(), FlankPosStart, FlankPosEnd, IndelScore); }
    }
}
