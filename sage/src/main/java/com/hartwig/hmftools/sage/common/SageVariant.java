package com.hartwig.hmftools.sage.common;

import java.util.List;
import java.util.Set;

import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.candidate.Candidate;
import com.hartwig.hmftools.sage.evidence.ReadContextCounter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SageVariant
{
    private final Candidate mCandidate;
    private final Set<String> mFilters;
    private final List<ReadContextCounter> mNormalReadCounters;
    private final List<ReadContextCounter> mTumorReadCounters;

    private int mLocalRealignSet;
    private int mMixedImpact;

    public SageVariant(
            final Candidate candidate, final Set<String> filters,
            final List<ReadContextCounter> normalCounters, final List<ReadContextCounter> tumorReadCounters)
    {
        mCandidate = candidate;
        mNormalReadCounters = normalCounters;
        mTumorReadCounters = tumorReadCounters;
        mFilters = filters;
        mLocalRealignSet = 0;
    }

    public Candidate candidate()
    {
        return mCandidate;
    }

    public String ref()
    {
        return variant().ref();
    }

    public String alt()
    {
        return variant().alt();
    }

    public int end()
    {
        return position() + ref().length() - 1;
    }

    public boolean isIndel()
    {
        return variant().ref().length() != variant().alt().length();
    }

    public boolean isMnv()
    {
        return variant().ref().length() >= 1 && variant().ref().length() == variant().alt().length();
    }

    public boolean isSnv()
    {
        return variant().ref().length() == 1 && variant().alt().length() == 1;
    }

    public boolean isInsert()
    {
        return variant().ref().length() < variant().alt().length();
    }

    public boolean isDelete()
    {
        return variant().ref().length() > variant().alt().length();
    }

    @Nullable
    public List<Integer> localPhaseSets()
    {
        if(mTumorReadCounters.isEmpty())
            return null;

        return mTumorReadCounters.get(0).localPhaseSets();
    }

    public boolean hasLocalPhaseSets()
    {
        if(mTumorReadCounters.isEmpty())
            return false;

        return mTumorReadCounters.get(0).localPhaseSets() != null;
    }

    public boolean hasMatchingLps(final List<Integer> otherLocalPhaseSets)
    {
        if(otherLocalPhaseSets == null)
            return false;

        if(mTumorReadCounters.isEmpty() || mTumorReadCounters.get(0).localPhaseSets() == null)
            return false;

        return mTumorReadCounters.get(0).localPhaseSets().stream().anyMatch(x -> otherLocalPhaseSets.contains(x));
    }

    @Nullable
    public List<double[]> localPhaseSetCounts()
    {
        if(mTumorReadCounters.isEmpty())
            return null;

        return mTumorReadCounters.get(0).lpsCounts();
    }

    public int localRealignSet() { return mLocalRealignSet; }
    public boolean hasLocalRealignSet() { return mLocalRealignSet > 0; }
    public void localRealignSet(int localRealignSet)
    {
        mLocalRealignSet = localRealignSet;
    }

    public int mixedGermlineImpact()
    {
        return mMixedImpact;
    }

    public void mixedGermlineImpact(final int mixedImpact)
    {
        mMixedImpact = mixedImpact;
    }

    public boolean isPassing()
    {
        return mFilters.isEmpty();
    }

    public boolean isTumorEmpty()
    {
        return mTumorReadCounters.isEmpty();
    }

    public boolean isNormalEmpty() { return mNormalReadCounters.isEmpty(); }

    @NotNull
    public VariantHotspot variant()
    {
        return mCandidate.variant();
    }

    @NotNull
    public VariantTier tier()
    {
        return mCandidate.tier();
    }

    @NotNull
    public Set<String> filters()
    {
        return mFilters;
    }

    @NotNull
    public ReadContext readContext() { return mTumorReadCounters.get(0).readContext(); }

    @NotNull
    public String microhomology()
    {
        return readContext().microhomology();
    }

    @NotNull
    public List<ReadContextCounter> normalAltContexts()
    {
        return mNormalReadCounters;
    }

    @NotNull
    public List<ReadContextCounter> tumorAltContexts()
    {
        return mTumorReadCounters;
    }

    @NotNull
    public String chromosome()
    {
        return variant().chromosome();
    }

    public int position() { return (int)variant().position(); }

    public int totalQuality()
    {
        return mTumorReadCounters.stream().mapToInt(ReadContextCounter::tumorQuality).sum();
    }

    public String toString() { return String.format("%s", mCandidate.toString()); }
}
