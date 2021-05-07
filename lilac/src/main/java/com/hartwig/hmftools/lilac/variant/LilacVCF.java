package com.hartwig.hmftools.lilac.variant;

import com.hartwig.hmftools.lilackt.hla.HlaAllele;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

import java.io.File;
import java.util.Set;
import java.util.StringJoiner;

public class LilacVCF implements AutoCloseable
{
    private final VCFFileReader mHeader;
    private final VariantContextWriter mWriter;

    public static final String HLA = "HLA";

    public LilacVCF(final String outputVCF, final String templateVCF)
    {
        mHeader = new VCFFileReader(new File(templateVCF), false);

        mWriter = new VariantContextWriterBuilder()
                .setReferenceDictionary(mHeader.getFileHeader().getSequenceDictionary())
                .setOutputFile(outputVCF)
                .build();
    }

    public final LilacVCF writeHeader(final String lilacVersion)
    {
        VCFHeader newHeader = new VCFHeader(mHeader.getFileHeader());
        newHeader.addMetaDataLine(new VCFHeaderLine("lilacVersion", lilacVersion));
        newHeader.addMetaDataLine(new VCFInfoHeaderLine(HLA, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "HLA Type"));
        mWriter.writeHeader(newHeader);
        return this;
    }

    public final void writeVariant(final VariantContext context, final Set<HlaAllele> alleles)
    {
        // CHECK what joiner is used
        StringJoiner sj = new StringJoiner("-");
        for(final HlaAllele allele : alleles)
        {
            sj.add(allele.toString());
        }

        String alleleStr = alleles.isEmpty() ? "UNKNOWN" : sj.toString();

        VariantContext newContext = new VariantContextBuilder(context).attribute(HLA, alleleStr).make();
        mWriter.add(newContext);
    }

    @Override
    public void close()
    {
        this.mWriter.close();
    }
}
