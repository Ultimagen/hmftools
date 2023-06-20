package com.hartwig.hmftools.common.utils.config;

import static java.lang.String.format;

public final class CommonConfig
{
    public static final String PURPLE_DIR = toolDirectory("purple");
    public static final String PURPLE_DIR_DESC = toolDirectoryDesc("purple");

    public static final String AMBER_DIR = toolDirectory("amber");
    public static final String AMBER_DIR_DESC = toolDirectoryDesc("amber");

    public static final String COBALT_DIR = toolDirectory("cobalt");
    public static final String COBALT_DIR_DESC = toolDirectoryDesc("cobalt");

    public static final String LINX_DIR = toolDirectory("linx");
    public static final String LINX_DIR_DESC = toolDirectoryDesc("linx");

    public static final String CHORD_DIR = toolDirectory("chord");
    public static final String CHORD_DIR_DESC = toolDirectoryDesc("chord");

    public static final String LILAC_DIR = toolDirectory("lilac");
    public static final String LILAC_DIR_DESC = toolDirectoryDesc("lilac");

    public static final String ISOFOX_DIR = toolDirectory("isofox");
    public static final String ISOFOX_DIR_DESC = toolDirectoryDesc("isofox");

    private static String toolDirectory(final String toolName) { return format("%s_dir", toolName); }
    private static String toolDirectoryDesc(final String toolName) { return format("Path to %s pipeline files", toolName); }
}