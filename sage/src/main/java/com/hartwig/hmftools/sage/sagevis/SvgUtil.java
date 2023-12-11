package com.hartwig.hmftools.sage.sagevis;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.jfree.svg.SVGGraphics2D;

public class SvgUtil
{
    public static Rectangle2D scaleRectangleFromCenter(final Rectangle2D rect, double factor)
    {
        double scaledWidth = factor * rect.getWidth();
        double scaledHeight = factor * rect.getHeight();
        return new Rectangle2D.Double(
                rect.getCenterX() - 0.5 * scaledWidth, rect.getCenterY() - 0.5 * scaledHeight, scaledWidth, scaledHeight);
    }

    public static Rectangle2D getStringBounds(final Font font, final String string)
    {
        SVGGraphics2D svgCanvas = new SVGGraphics2D(1.0, 1.0);
        svgCanvas.setFont(font);

        FontMetrics metrics = svgCanvas.getFontMetrics(font);

        Rectangle2D stringRect = metrics.getStringBounds(string, svgCanvas);
        return new Rectangle2D.Double(stringRect.getX(), stringRect.getY(), stringRect.getWidth(),
                stringRect.getHeight() + metrics.getDescent());
    }

    public static int getFontDescent(final Font font)
    {
        SVGGraphics2D svgCanvas = new SVGGraphics2D(1.0, 1.0);
        svgCanvas.setFont(font);

        FontMetrics metrics = svgCanvas.getFontMetrics(font);
        return metrics.getDescent();
    }

    public static Rectangle2D getStringBoundsFromCenter(final Font font, final String string, double centerX, double centerY)
    {
        Rectangle2D boundingRectOrigin = getStringBounds(font, string);
        double width = boundingRectOrigin.getWidth();
        double height = boundingRectOrigin.getHeight();
        return new Rectangle2D.Double(centerX - 0.5 * width, centerY - 0.5 * height, width, height);
    }

    public static void drawStringFromCenter(final SVGGraphics2D canvas, final String string, double centerX, double centerY)
    {
        Font currentFont = canvas.getFont();
        Rectangle2D boundingRect = getStringBoundsFromCenter(currentFont, string, centerX, centerY);
        canvas.drawString(string, (int) (centerX - 0.5 * boundingRect.getWidth()), (int) (centerY + 0.5 * boundingRect.getHeight()
                - getFontDescent(currentFont)));
    }

    public static void drawForwardArrow(final SVGGraphics2D svgCanvas, double left, double top, double width, double height)
    {
        Path2D.Double forwardArrowPath = new Path2D.Double();
        forwardArrowPath.moveTo(left, top);
        forwardArrowPath.lineTo(left + width, top + 0.5 * height);
        forwardArrowPath.lineTo(left, top + height);
        forwardArrowPath.closePath();

        svgCanvas.fill(forwardArrowPath);
    }

    public static void drawReverseArrow(final SVGGraphics2D svgCanvas, double left, double top, double width, double height)
    {
        Path2D.Double reverseArrowPath = new Path2D.Double();
        reverseArrowPath.moveTo(left, top + 0.5 * height);
        reverseArrowPath.lineTo(left + width, top);
        reverseArrowPath.lineTo(left + width, top + height);
        reverseArrowPath.closePath();

        svgCanvas.fill(reverseArrowPath);
    }
}
