package com.hartwig.hmftools.cup.somatics;

import static com.hartwig.hmftools.common.sigs.VectorUtils.sumVector;
import static com.hartwig.hmftools.common.utils.MatrixUtils.loadMatrixDataFile;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.sigs.LeastSquaresFit;
import com.hartwig.hmftools.common.utils.Matrix;

public class SomaticSigs
{
    private final List<String> mSignatureNames;
    private final Matrix mSignatures;
    private final LeastSquaresFit mLeastSquaresFitter;

    public static final Map<String,String> REPORTABLE_SIGS = Maps.newHashMap();

    public SomaticSigs(final String signaturesFile)
    {
        mSignatureNames = Lists.newArrayList();

        if(signaturesFile != null && !signaturesFile.isEmpty() && Files.exists(Paths.get(signaturesFile)))
        {
            mSignatures = loadMatrixDataFile(signaturesFile, mSignatureNames);
            mLeastSquaresFitter = new LeastSquaresFit(mSignatures.Rows, mSignatures.Cols);
        }
        else
        {
            mSignatures = null;
            mLeastSquaresFitter = null;
        }
    }

    public boolean hasValidData() { return mSignatures != null && !mSignatureNames.isEmpty(); }

    public static final String SIG_NAME_2 = "Sig2";
    public static final String SIG_NAME_13 = "Sig13";

    public static void populateReportableSignatures()
    {
        REPORTABLE_SIGS.clear();
        REPORTABLE_SIGS.put("Sig1", "SIG_1");
        REPORTABLE_SIGS.put("Sig2", "SIG_2_13_AID_APOBEC");
        REPORTABLE_SIGS.put("Sig13", "SIG_2_13_AID_APOBEC");
        REPORTABLE_SIGS.put("Sig4", "SIG_4_SMOKING");
        REPORTABLE_SIGS.put("Sig6", "SIG_6_MMR");
        REPORTABLE_SIGS.put("Sig7", "SIG_7_UV");
        REPORTABLE_SIGS.put("Sig10", "SIG_10_POLE");
        REPORTABLE_SIGS.put("Sig11", "SIG_11");
        REPORTABLE_SIGS.put("Sig17", "SIG_17");
    }

    public static final String signatureDisplayName(final String sigName)
    {
        final String displayName = REPORTABLE_SIGS.get(sigName);
        return displayName != null ? displayName : "UNKNOWN";
    }

    public String getSigName(int index) { return index < mSignatureNames.size() ? mSignatureNames.get(index) : ""; }

    public final double[] fitSampleCounts(final double[] sampleCounts)
    {
        double sampleTotal = sumVector(sampleCounts);

        if(sampleTotal == 0)
            return null;

        mLeastSquaresFitter.initialise(mSignatures.getData(), sampleCounts);
        mLeastSquaresFitter.solve();
        return mLeastSquaresFitter.getContribs();
    }
}
