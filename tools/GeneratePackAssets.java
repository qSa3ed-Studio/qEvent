import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates every binary asset shipped in qEvent's resource pack.
 *
 * <p>The PNGs are committed to the repository so builds never depend on the fonts installed
 * on a build machine; this program documents exactly how they were produced and is run only
 * when the artwork needs to change:</p>
 *
 * <pre>
 *   java tools/GeneratePackAssets.java
 * </pre>
 *
 * <p>Output: {@code resourcepack/assets/qevent/textures/...} plus {@code resourcepack/pack.png}.</p>
 */
public final class GeneratePackAssets {

    private static final Path ROOT = Path.of("resourcepack");
    private static final Path TEXTURES = ROOT.resolve("assets/qevent/textures");

    /** Baseline inside a cell: ascent 12, so the top of a cap sits at y = 1. */
    private static final int CELL_HEIGHT = 16;
    private static final int ASCENT = 12;
    private static final int BASELINE = 12;
    private static final int FONT_SIZE = 15;

    private static final Color PRIMARY = new Color(0xFF, 0xD1, 0x66);
    private static final Color SECONDARY = new Color(0xFF, 0x6B, 0x6B);

    private GeneratePackAssets() {
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(TEXTURES.resolve("font"));
        Files.createDirectories(TEXTURES.resolve("gui"));

        // Bitmap font sheets: one provider per width group so the typeface stays proportional.
        writeFontSheet("caps.png", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", 12, true);
        writeFontSheet("lower.png", "abcdefghijklmnopqrstuvwxyz", 10, false);
        writeFontSheet("digits.png", "0123456789", 12, true);
        writeFontSheet("narrow.png", "!?.,;:'\"()[]-+", 7, false);

        writeBadge();
        writeBracket("bracket_left.png", true);
        writeBracket("bracket_right.png", false);
        writeDivider();
        writePackIcon();

        System.out.println("Generated qEvent resource pack assets under " + ROOT.toAbsolutePath());
    }

    private static void writeFontSheet(String fileName, String characters, int cellWidth, boolean caps)
            throws IOException {
        int cells = characters.length();
        BufferedImage image = new BufferedImage(cellWidth * cells, CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        Font font = new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();

        for (int i = 0; i < cells; i++) {
            String glyph = String.valueOf(characters.charAt(i));
            int x = i * cellWidth + (cellWidth - metrics.stringWidth(glyph)) / 2;
            int y = ASCENT - metrics.getAscent() + BASELINE;
            g.setColor(Color.WHITE);
            g.drawString(glyph, x, y);
        }
        g.dispose();

        ImageIO.write(image, "png", TEXTURES.resolve("font").resolve(fileName).toFile());
        System.out.println("  font/" + fileName + " (" + cellWidth + "x" + CELL_HEIGHT + " x " + cells + ")");
    }

    private static void writeBadge() throws IOException {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        // Soft glow behind the mark.
        g.setPaint(new GradientPaint(0, 0, new Color(PRIMARY.getRed(), PRIMARY.getGreen(), PRIMARY.getBlue(), 90),
                size, size, new Color(SECONDARY.getRed(), SECONDARY.getGreen(), SECONDARY.getBlue(), 180)));
        g.fillOval(1, 1, size - 2, size - 2);

        // Eight point star.
        int cx = size / 2;
        int cy = size / 2;
        java.awt.geom.Path2D star = new java.awt.geom.Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            double outer = 6.4;
            double inner = 2.6;
            double mid = angle + Math.PI / 8;
            double ox = cx + Math.cos(angle) * outer;
            double oy = cy + Math.sin(angle) * outer;
            if (i == 0) {
                star.moveTo(ox, oy);
            } else {
                star.lineTo(ox, oy);
            }
            star.lineTo(cx + Math.cos(mid) * inner, cy + Math.sin(mid) * inner);
        }
        star.closePath();
        g.setColor(Color.WHITE);
        g.fill(star);
        g.dispose();

        ImageIO.write(image, "png", TEXTURES.resolve("gui").resolve("badge.png").toFile());
        System.out.println("  gui/badge.png (16x16)");
    }

    private static void writeBracket(String fileName, boolean left) throws IOException {
        int width = 7;
        int height = 20;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);
        g.setColor(PRIMARY);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int spine = left ? 2 : width - 3;
        int inset = left ? 1 : width - 2;
        g.drawLine(inset, 1, spine, 1);
        g.drawLine(spine, 1, spine, height - 2);
        g.drawLine(inset, height - 2, spine, height - 2);
        g.dispose();

        ImageIO.write(image, "png", TEXTURES.resolve("gui").resolve(fileName).toFile());
        System.out.println("  gui/" + fileName + " (" + width + "x" + height + ")");
    }

    private static void writeDivider() throws IOException {
        int width = 24;
        int height = 6;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);
        g.setPaint(new GradientPaint(0, 0, PRIMARY, width, 0, SECONDARY));
        g.fillRoundRect(0, 2, width, 2, 2, 2);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(width / 2.0 - 2.5, 0.5, 5, 5));
        g.dispose();

        ImageIO.write(image, "png", TEXTURES.resolve("gui").resolve("divider.png").toFile());
        System.out.println("  gui/divider.png (" + width + "x" + height + ")");
    }

    private static void writePackIcon() throws IOException {
        int size = 128;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);

        g.setPaint(new GradientPaint(0, 0, new Color(0x1B, 0x1B, 0x2F), size, size, new Color(0x33, 0x1A, 0x40)));
        g.fillRoundRect(0, 0, size, size, 24, 24);

        g.setPaint(new GradientPaint(size / 4f, size / 4f, PRIMARY, size * 3 / 4f, size * 3 / 4f, SECONDARY));
        g.fillOval(size / 6, size / 6, size * 2 / 3, size * 2 / 3);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 46));
        FontMetrics metrics = g.getFontMetrics();
        String label = "qE";
        g.drawString(label, (size - metrics.stringWidth(label)) / 2,
                (size - metrics.getHeight()) / 2 + metrics.getAscent());
        g.dispose();

        ImageIO.write(image, "png", ROOT.resolve("pack.png").toFile());
        System.out.println("  pack.png (" + size + "x" + size + ")");
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}