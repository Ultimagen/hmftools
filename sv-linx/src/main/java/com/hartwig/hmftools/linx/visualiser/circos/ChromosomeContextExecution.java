package com.hartwig.hmftools.linx.visualiser.circos;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.hartwig.hmftools.common.r.RExecutor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.IOUtils;
import org.jetbrains.annotations.NotNull;

public class ChromosomeContextExecution
{
    private static final Logger LOGGER = LogManager.getLogger(ChromosomeContextExecution.class);

    private final String plotFile;
    private final String bandFile;
    private final String chromosomeFile;

    public ChromosomeContextExecution(final String sample, final String dataDir, final String plotDir)
    {
        this.plotFile = plotDir + File.separator + sample + ".png";
        this.bandFile = dataDir + File.separator + sample + ".cytoBand.txt";
        this.chromosomeFile = dataDir + File.separator + sample + ".chromosome.circos";
    }

    public Integer executeR() throws IOException, InterruptedException
    {
        writeCytobands();
        int result = RExecutor.executeFromClasspath("r/gvizPlot.R",
                chromosomeFile,
                bandFile,
                plotFile,
                "70",
                "150");
        if (result != 0)
        {
            LOGGER.warn("Error adding chromosomal context");
        }

        return result;
    }

    private void writeCytobands() throws IOException
    {
        final String template = readResource("/r/cytoBand.txt");
        Files.write(new File(bandFile).toPath(), template.getBytes(StandardCharsets.UTF_8));
    }

    @NotNull
    private String readResource(@NotNull final String resource) throws IOException
    {
        InputStream in = getClass().getResourceAsStream(resource);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        return IOUtils.toString(reader);
    }

}
