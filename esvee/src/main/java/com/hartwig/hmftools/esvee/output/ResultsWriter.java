package com.hartwig.hmftools.esvee.output;

import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.closeBufferedWriter;

import java.io.BufferedWriter;

import com.hartwig.hmftools.esvee.AssemblyConfig;
import com.hartwig.hmftools.esvee.alignment.DecoyChecker;

public class ResultsWriter
{
    private final BufferedWriter mDecoyMatchWriter;
    private final AssemblyWriter mAssemblyWriter;
    private final AssemblyReadWriter mReadWriter;
    private final PhaseGroupBuildWriter mPhaseGroupBuildWriter;
    private final BamWriter mBamWriter;

    public ResultsWriter(final AssemblyConfig config)
    {
        mAssemblyWriter = new AssemblyWriter(config);
        mReadWriter = new AssemblyReadWriter(config);
        mPhaseGroupBuildWriter = new PhaseGroupBuildWriter(config);
        mBamWriter = new BamWriter(config);
        mDecoyMatchWriter = DecoyChecker.initialiseWriter(config);
    }

    public BufferedWriter decoyMatchWriter() { return mDecoyMatchWriter; }
    public AssemblyWriter assemblyWriter() { return mAssemblyWriter; }
    public AssemblyReadWriter readWriter() { return mReadWriter; }
    public PhaseGroupBuildWriter phaseGroupBuildWriter() { return mPhaseGroupBuildWriter; }
    public BamWriter bamWriter() { return mBamWriter; }

    public void close()
    {
        mAssemblyWriter.close();
        mReadWriter.close();
        mPhaseGroupBuildWriter.close();
        mBamWriter.close();
        closeBufferedWriter(mDecoyMatchWriter);
    }
}
