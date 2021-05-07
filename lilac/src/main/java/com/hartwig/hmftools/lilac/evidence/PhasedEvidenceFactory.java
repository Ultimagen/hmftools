package com.hartwig.hmftools.lilac.evidence;

import static com.hartwig.hmftools.lilac.LilacConfig.LL_LOGGER;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.lilac.LilacConfig;
import com.hartwig.hmftools.lilac.SequenceCount;
import com.hartwig.hmftools.lilac.fragment.AminoAcidFragment;
import com.hartwig.hmftools.lilac.hla.HlaContext;
import com.hartwig.hmftools.lilac.fragment.ExpectedAlleles;
import com.sun.tools.javac.util.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class PhasedEvidenceFactory
{
    private final LilacConfig mConfig;

    public PhasedEvidenceFactory(final LilacConfig config)
    {
        mConfig = config;
    }

    public final List<PhasedEvidence> evidence(final HlaContext context, final List<AminoAcidFragment> fragments)
    {
        LL_LOGGER.info("Phasing HLA-" + context.Gene + " records:");
        List<PhasedEvidence> result = this.evidence(context.ExpectedAlleles, fragments);
        if(this.mConfig.DebugPhasing)
        {
            LL_LOGGER.info("  Consolidating evidence");
        }
        for(PhasedEvidence phasedEvidence : result)
        {
            LL_LOGGER.info("  " + phasedEvidence);
        }
        return result;
    }

    public final List<PhasedEvidence> evidence(
            final ExpectedAlleles expectedAlleles, final List<AminoAcidFragment> aminoAcidAminoAcidFragments)
    {
        SequenceCount aminoAcidCounts = SequenceCount.aminoAcids(mConfig.MinEvidence, aminoAcidAminoAcidFragments);

        List<Integer> heterozygousIndices = aminoAcidCounts.heterozygousLoci();
        
        if (mConfig.DebugPhasing)
        {
            LL_LOGGER.info("  Heterozygous Indices: $heterozygousIndices");
        }

        ExtendEvidence heterozygousEvidence = new ExtendEvidence(mConfig, heterozygousIndices, aminoAcidAminoAcidFragments, expectedAlleles);

        List<PhasedEvidence> finalisedEvidence = Lists.newArrayList();
        List<PhasedEvidence> unprocessedEvidence = Lists.newArrayList();
        unprocessedEvidence.addAll(heterozygousEvidence.pairedEvidence());

        if (mConfig.DebugPhasing)
        {
            LL_LOGGER.info("  Extending paired evidence");
        }

        while(!unprocessedEvidence.isEmpty())
        {
            PhasedEvidence top = unprocessedEvidence.remove(0);

            if (mConfig.DebugPhasing)
            {
                LL_LOGGER.info("  Processing top: {}", top);
            }

            Set<PhasedEvidence> others = Sets.newHashSet();
            others.addAll(finalisedEvidence);
            others.addAll(unprocessedEvidence);
            Pair<PhasedEvidence, Set<PhasedEvidence>> pair = heterozygousEvidence.merge(top, others);
            PhasedEvidence parent = pair.fst;
            Set<PhasedEvidence> children = pair.snd;

            if (!children.isEmpty())
            {
                if (mConfig.DebugPhasing)
                {
                    LL_LOGGER.info("  Produced child: {}", pair.fst);
                }

                finalisedEvidence.removeAll(children);
                unprocessedEvidence.removeAll(children);
                unprocessedEvidence.add(parent);
            }
            else
            {
                finalisedEvidence.add(parent);
            }

            Collections.sort(unprocessedEvidence);
        }

        Collections.sort(finalisedEvidence, new PhasedEvidenceSorter());
        return finalisedEvidence;
    }

    private static class PhasedEvidenceSorter implements Comparator<PhasedEvidence>
    {
        public int compare(final PhasedEvidence first, final PhasedEvidence second)
        {
            int firstAA = first.getAminoAcidIndexList().get(0);
            int secondAA = second.getAminoAcidIndexList().get(0);
            if(firstAA != secondAA)
                return firstAA > secondAA ? 1 : -1;

            return 0;
        }
    }

}
