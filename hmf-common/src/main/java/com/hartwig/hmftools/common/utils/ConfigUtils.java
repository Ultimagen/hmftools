package com.hartwig.hmftools.common.utils;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ConfigUtils
{
    public static final String LOG_DEBUG = "log_debug";

    private static final Logger LOGGER = LogManager.getLogger(ConfigUtils.class);

    public static double getConfigValue(final CommandLine cmd, final String configName, double defaultValue)
    {
        return cmd.hasOption(configName) ? Double.parseDouble(cmd.getOptionValue(configName)) : defaultValue;
    }

    public static int getConfigValue(final CommandLine cmd, final String configName, int defaultValue)
    {
        return cmd.hasOption(configName) ? Integer.parseInt(cmd.getOptionValue(configName)) : defaultValue;
    }

    public static boolean getConfigValue(final CommandLine cmd, final String configName, boolean defaultValue)
    {
        return cmd.hasOption(configName) ? Boolean.parseBoolean(cmd.getOptionValue(configName)) : defaultValue;
    }

    @NotNull
    public static <E extends Enum<E>> E defaultEnumValue(@NotNull final CommandLine cmd, @NotNull final String argument,
            @NotNull final E defaultValue) throws ParseException
    {
        if(cmd.hasOption(argument))
        {
            final String optionValue = cmd.getOptionValue(argument);
            try
            {
                final E value = E.valueOf(defaultValue.getDeclaringClass(), optionValue);
                if(!value.equals(defaultValue))
                {
                    LOGGER.info("Using non default value {} for parameter {}", optionValue, argument);
                }

                return value;
            } catch(IllegalArgumentException e)
            {
                throw new ParseException("Invalid validation stringency: " + optionValue);
            }
        }

        return defaultValue;
    }

    public static boolean containsFlag(@NotNull final CommandLine cmd, @NotNull final String opt)
    {
        if(cmd.hasOption(opt))
        {
            LOGGER.info("Using non default {} flag", opt);
            return true;
        }
        return false;
    }

    @NotNull
    public static String readableFile(@NotNull final CommandLine cmd, @NotNull final String opt) throws IOException, ParseException
    {
        if(!cmd.hasOption(opt))
        {
            throw new ParseException(opt + " is a required option");
        }
        final String file = cmd.getOptionValue(opt);
        if(!new File(file).exists())
        {
            throw new IOException("Unable to read file:  " + file);
        }

        return file;
    }

    @NotNull
    public static String writableOutputDirectory(@NotNull final CommandLine cmd, @NotNull final String opt)
            throws ParseException, IOException
    {
        if(!cmd.hasOption(opt))
        {
            throw new ParseException(opt + " is a required option");
        }

        final String outputDirString = cmd.getOptionValue(opt);
        final File outputDir = new File(outputDirString);
        if(!outputDir.exists() && !outputDir.mkdirs())
        {
            throw new IOException("Unable to write output directory " + outputDirString);
        }

        return outputDirString;
    }

}
