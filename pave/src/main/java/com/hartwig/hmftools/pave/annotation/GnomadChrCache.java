package com.hartwig.hmftools.pave.annotation;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.pave.VariantData;

public class GnomadChrCache
{
    public final String Chromosome;

    private final Map<Integer,List<GnomadVariant>> mFrequencies;

    public GnomadChrCache(final String chromosome)
    {
        Chromosome = chromosome;
        mFrequencies = Maps.newHashMap();
    }

    public void addEntry(final int position, final String ref, final String alt, final double frequency)
    {
        List<GnomadVariant> posList = mFrequencies.get(position);

        if(posList == null)
        {
            posList = Lists.newArrayList();
            mFrequencies.put(position, posList);
        }

        posList.add(new GnomadVariant(ref, alt, frequency));
    }

    public void clear() { mFrequencies.clear(); }

    public String toString() { return format("chr(%s) entries(%d)", Chromosome, mFrequencies.size()); }

    private class GnomadVariant
    {
        public final String Ref;
        public final String Alt;
        public final double Frequency;

        public GnomadVariant(final String ref, final String alt, final double frequency)
        {
            Ref = ref;
            Alt = alt;
            Frequency = frequency;
        }

        public boolean matches(final String ref, final String alt) { return ref.equals(Ref) && alt.equals(Alt); }
    }

    public Double getFrequency(final VariantData variant)
    {
        if(variant.isMnv())
        {
            // check for successive bases for an MNV
            double minFreq = 0;
            for(int i = 0; i < variant.Ref.length(); ++i)
            {
                Double freq = getFrequency(variant.Position + i, variant.Ref.substring(i, i + 1), variant.Alt.substring(i, i + 1));

                if(freq == null)
                    return null;

                if(minFreq == 0 ||freq < minFreq)
                    minFreq = freq;
            }

            return minFreq;
        }
        else
        {
            return getFrequency(variant.Position, variant.Ref, variant.Alt);
        }
    }

    public Double getFrequency(int position, final String ref, final String alt)
    {
        List<GnomadVariant> posList = mFrequencies.get(position);

        if(posList == null)
            return null;

        GnomadVariant match = posList.stream().filter(x -> x.matches(ref, alt)).findFirst().orElse(null);
        return match != null ? match.Frequency : null;
    }
}
