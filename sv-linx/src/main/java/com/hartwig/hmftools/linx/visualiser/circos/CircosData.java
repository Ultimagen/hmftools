package com.hartwig.hmftools.linx.visualiser.circos;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.position.GenomePosition;
import com.hartwig.hmftools.common.position.GenomePositions;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.linx.visualiser.SvCircosConfig;
import com.hartwig.hmftools.linx.visualiser.data.Connector;
import com.hartwig.hmftools.linx.visualiser.data.Connectors;
import com.hartwig.hmftools.linx.visualiser.data.CopyNumberAlteration;
import com.hartwig.hmftools.linx.visualiser.data.DisruptedGene;
import com.hartwig.hmftools.linx.visualiser.data.Exon;
import com.hartwig.hmftools.linx.visualiser.data.Exons;
import com.hartwig.hmftools.linx.visualiser.data.Fusion;
import com.hartwig.hmftools.linx.visualiser.data.Gene;
import com.hartwig.hmftools.linx.visualiser.data.Genes;
import com.hartwig.hmftools.linx.visualiser.data.Link;
import com.hartwig.hmftools.linx.visualiser.data.Links;
import com.hartwig.hmftools.linx.visualiser.data.Segment;

import org.jetbrains.annotations.NotNull;

public class CircosData
{
    private final List<Exon> exons;
    private final List<Link> links;
    private final List<Gene> genes;
    private final List<Segment> segments;
    private final List<GenomeRegion> lineElements;
    private final List<GenomeRegion> fragileSites;
    private final List<CopyNumberAlteration> alterations;
    private final List<GenomeRegion> disruptedGeneRegions;
    private final List<Connector> connectors;

    private final List<Link> unadjustedLinks;
    private final List<CopyNumberAlteration> unadjustedAlterations;

    private final Set<GenomePosition> contigLengths;

    private final Set<String> upstreamGenes;
    private final Set<String> downstreamGenes;

    private final SvCircosConfig config;

    private final int maxTracks;
    private final double maxPloidy;
    private final double maxCopyNumber;
    private final double maxMinorAllelePloidy;
    private final double labelSize;

    public CircosData(
            boolean showSimpleSvSegments,
            @NotNull final SvCircosConfig config,
            @NotNull final List<Segment> unadjustedSegments,
            @NotNull final List<Link> unadjustedLinks,
            @NotNull final List<CopyNumberAlteration> unadjustedAlterations,
            @NotNull final List<Exon> unadjustedExons,
            @NotNull final List<Fusion> fusions)
    {
        this.upstreamGenes = fusions.stream().map(Fusion::geneUp).collect(toSet());
        this.downstreamGenes = fusions.stream().map(Fusion::geneDown).collect(toSet());
        this.unadjustedLinks = unadjustedLinks;
        this.unadjustedAlterations = unadjustedAlterations;
        this.config = config;

        final List<GenomeRegion> unadjustedDisruptedGeneRegions = Lists.newArrayList();
        for (Fusion fusion : fusions)
        {
            unadjustedDisruptedGeneRegions.addAll(DisruptedGene.disruptedGeneRegions(fusion, unadjustedExons));
        }

        final List<Gene> unadjustedGenes = Genes.uniqueGenes(unadjustedExons);
        final List<Exon> unadjustedGeneExons = Exons.geneExons(unadjustedGenes, unadjustedExons);

        final List<GenomePosition> positionsToScale = Lists.newArrayList();
        positionsToScale.addAll(Links.allPositions(unadjustedLinks));
        positionsToScale.addAll(Span.allPositions(unadjustedSegments));
        unadjustedGenes.stream().map(x -> GenomePositions.create(x.chromosome(), x.namePosition())).forEach(positionsToScale::add);
        positionsToScale.addAll(Span.allPositions(unadjustedGeneExons));
        positionsToScale.addAll(Span.allPositions(unadjustedDisruptedGeneRegions));
        positionsToScale.addAll(config.interpolateCopyNumberPositions()
                ? Span.minMaxPositions(unadjustedAlterations)
                : Span.allPositions(unadjustedAlterations));

        final List<GenomeRegion> unadjustedFragileSites =
                Highlights.limitHighlightsToRegions(Highlights.fragileSites(), Span.spanPositions(positionsToScale));

        final List<GenomeRegion> unadjustedLineElements =
                Highlights.limitHighlightsToRegions(Highlights.lineElements(), Span.spanPositions(positionsToScale));

        final ScalePosition scalePosition = new ScalePosition(positionsToScale);
        contigLengths = scalePosition.contigLengths();
        segments = scalePosition.scaleSegments(unadjustedSegments);
        links = scalePosition.scaleLinks(unadjustedLinks);
        alterations = scalePosition.interpolateAlterations(unadjustedAlterations);
        fragileSites = scalePosition.interpolateRegions(unadjustedFragileSites);
        lineElements = scalePosition.interpolateRegions(unadjustedLineElements);
        genes = scalePosition.scaleGene(unadjustedGenes);
        disruptedGeneRegions = scalePosition.scaleRegions(unadjustedDisruptedGeneRegions);
        exons = scalePosition.interpolateExons(unadjustedGeneExons);

        maxTracks = segments.stream().mapToInt(Segment::track).max().orElse(0) + 1;
        maxCopyNumber = alterations.stream().mapToDouble(CopyNumberAlteration::copyNumber).max().orElse(0);
        maxMinorAllelePloidy = alterations.stream().mapToDouble(CopyNumberAlteration::minorAllelePloidy).max().orElse(0);

        double maxLinkPloidy = links.stream().mapToDouble(Link::ploidy).max().orElse(0);
        double maxSegmentsPloidy = segments.stream().mapToDouble(Segment::ploidy).max().orElse(0);

        maxPloidy = Math.max(maxLinkPloidy, maxSegmentsPloidy);
        connectors = new Connectors(showSimpleSvSegments).createConnectors(segments, links);
        labelSize = config.labelSize(untruncatedCopyNumberAlterationsCount());
    }

    public List<Connector> connectors()
    {
        return connectors;
    }

    @NotNull
    public List<GenomeRegion> disruptedGeneRegions()
    {
        return disruptedGeneRegions;
    }

    @NotNull
    public Set<String> upstreamGenes()
    {
        return upstreamGenes;
    }

    @NotNull
    public Set<String> downstreamGenes()
    {
        return downstreamGenes;
    }

    public boolean displayGenes()
    {
        return !exons.isEmpty() && Doubles.positive(config.geneRelativeSize());
    }

    public int maxTracks()
    {
        return maxTracks;
    }

    public double maxPloidy()
    {
        return maxPloidy;
    }

    public double maxCopyNumber()
    {
        return maxCopyNumber;
    }

    public double maxMinorAllelePloidy()
    {
        return maxMinorAllelePloidy;
    }

    @NotNull
    public List<Gene> genes()
    {
        return genes;
    }

    @NotNull
    public List<Link> unadjustedLinks()
    {
        return unadjustedLinks;
    }

    @NotNull
    public List<CopyNumberAlteration> unadjustedAlterations()
    {
        return unadjustedAlterations;
    }

    @NotNull
    public List<Segment> segments()
    {
        return segments;
    }

    @NotNull
    public List<Link> links()
    {
        return links;
    }

    @NotNull
    public List<CopyNumberAlteration> alterations()
    {
        return alterations;
    }

    @NotNull
    public List<Exon> exons()
    {
        return exons;
    }

    @NotNull
    public List<GenomeRegion> fragileSites()
    {
        return fragileSites;
    }

    @NotNull
    public List<GenomeRegion> lineElements()
    {
        return lineElements;
    }

    @NotNull
    public Set<GenomePosition> contigLengths()
    {
        return contigLengths;
    }

    public int totalContigLength()
    {
        return contigLengths().stream().mapToInt(x -> (int) x.position()).sum();
    }

    private long untruncatedCopyNumberAlterationsCount()
    {
        return alterations.stream().filter(x -> !x.truncated()).count();
    }

    public double labelSize()
    {
        return labelSize;
    }
}
