package com.hartwig.hmftools.purple.config;

import static com.hartwig.hmftools.common.utils.config.ConfigUtils.getConfigValue;
import static com.hartwig.hmftools.purple.config.PurpleConfig.addDecimalConfigItem;
import static com.hartwig.hmftools.purple.config.PurpleConfig.addIntegerConfigItem;
import static com.hartwig.hmftools.purple.config.PurpleConstants.HIGHLY_DIPLOID_PERCENTAGE_DEFAULT;
import static com.hartwig.hmftools.purple.config.PurpleConstants.SOMATIC_MIN_PEAK_DEFAULT;
import static com.hartwig.hmftools.purple.config.PurpleConstants.SOMATIC_MIN_PURITY_DEFAULT;
import static com.hartwig.hmftools.purple.config.PurpleConstants.SOMATIC_MIN_PURITY_SPREAD_DEFAULT;
import static com.hartwig.hmftools.purple.config.PurpleConstants.SOMATIC_MIN_VARIANTS_DEFAULT;
import static com.hartwig.hmftools.purple.config.PurpleConstants.SOMATIC_PENALTY_WEIGHT_DEFAULT;

import com.hartwig.hmftools.common.utils.config.ConfigBuilder;

public class SomaticFitConfig
{
    public static final String SOMATIC_MIN_PEAK = "somatic_min_peak";
    public static final String SOMATIC_MIN_TOTAL = "somatic_min_variants";
    public static final String SOMATIC_MIN_PURITY = "somatic_min_purity";
    public static final String SOMATIC_MIN_PURITY_SPREAD = "somatic_min_purity_spread";
    public static final String SOMATIC_PENALTY_WEIGHT = "somatic_penalty_weight";
    public static final String HIGHLY_DIPLOID_PERCENTAGE = "highly_diploid_percentage";

    public int MinTotalVariants;
    public int MinPeakVariants;
    public double MinSomaticPurity;
    public double MinSomaticPuritySpread;
    public double SomaticPenaltyWeight;
    public double HighlyDiploidPercentage;

    public SomaticFitConfig(final ConfigBuilder configBuilder)
    {
        MinTotalVariants = configBuilder.getInteger(SOMATIC_MIN_TOTAL);
        MinPeakVariants = configBuilder.getInteger(SOMATIC_MIN_PEAK);
        MinSomaticPurity = configBuilder.getDecimal(SOMATIC_MIN_PURITY);
        MinSomaticPuritySpread = configBuilder.getDecimal(SOMATIC_MIN_PURITY_SPREAD);
        SomaticPenaltyWeight = configBuilder.getDecimal(SOMATIC_PENALTY_WEIGHT);
        HighlyDiploidPercentage = configBuilder.getDecimal(HIGHLY_DIPLOID_PERCENTAGE);
    }

    public static void addConfig(final ConfigBuilder configBuilder)
    {
        addIntegerConfigItem(
                configBuilder, SOMATIC_MIN_PEAK, "Minimum number of somatic variants to consider a peak", SOMATIC_MIN_PEAK_DEFAULT);

        addIntegerConfigItem(
                configBuilder, SOMATIC_MIN_TOTAL,
                "Minimum number of somatic variants required to assist highly diploid fits", SOMATIC_MIN_VARIANTS_DEFAULT);

        addDecimalConfigItem(
                configBuilder, SOMATIC_MIN_PURITY, "Minimum spread within candidate purities before somatics can be used",
                SOMATIC_MIN_PURITY_DEFAULT);

        addDecimalConfigItem(
                configBuilder, SOMATIC_MIN_PURITY_SPREAD, "Minimum spread within candidate purities before somatics can be used",
                SOMATIC_MIN_PURITY_SPREAD_DEFAULT);

        addDecimalConfigItem(
                configBuilder, SOMATIC_PENALTY_WEIGHT, "Proportion of somatic deviation to include in fitted purity score",
                SOMATIC_PENALTY_WEIGHT_DEFAULT);

        addDecimalConfigItem(
                configBuilder, HIGHLY_DIPLOID_PERCENTAGE, "Proportion of genome that must be diploid before using somatic fit",
                HIGHLY_DIPLOID_PERCENTAGE_DEFAULT);
    }
}
