import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates GameRoute's app icon as a multi-resolution .ico file, plus
 * optionally a standalone PNG preview. No external assets or downloads:
 * everything is drawn with Java2D (dark rounded-square badge, red gradient,
 * white lightning bolt -- matching the in-app brand mark and Optimizer icon).
 * <p>
 * Not part of the Maven build (the generated {@code gameroute.ico} /
 * {@code gameroute.png} are committed directly); this is here so the artwork
 * can be regenerated or tweaked later without redrawing it from scratch.
 * <p>
 * Usage: {@code javac IconGen.java && java IconGen gameroute.ico [preview.png]}
 */
public class IconGen {

    public static void main(String[] args) throws Exception {
        int[] sizes = {256, 128, 64, 48, 32, 16};
        List<byte[]> pngs = new ArrayList<>();
        List<Integer> dims = new ArrayList<>();
        for (int size : sizes) {
            BufferedImage img = render(size);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            pngs.add(baos.toByteArray());
            dims.add(size);
        }
        writeIco(pngs, dims, new File(args.length > 0 ? args[0] : "gameroute.ico"));
        System.out.println("Wrote ICO with " + sizes.length + " sizes.");

        if (args.length > 1) {
            ImageIO.write(render(512), "png", new File(args[1]));
            System.out.println("Wrote preview PNG.");
        }
    }

    private static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double pad = size * 0.04;
        double d = size - pad * 2;
        RoundRectangle2D badge = new RoundRectangle2D.Double(pad, pad, d, d, d * 0.28, d * 0.28);

        GradientPaint bg = new GradientPaint(0, 0, new Color(0x15, 0x19, 0x22), 0, size, new Color(0x09, 0x0B, 0x10));
        g.setPaint(bg);
        g.fill(badge);

        g.setStroke(new BasicStroke((float) (size * 0.015f)));
        g.setColor(new Color(255, 255, 255, 20));
        g.draw(badge);

        // Red glow ring behind the bolt
        Paint glow = new RadialGradientPaint(
                new Point2D.Double(size / 2.0, size / 2.0), (float) (size * 0.42),
                new float[]{0f, 1f},
                new Color[]{new Color(0xFF, 0x3B, 0x30, 120), new Color(0xFF, 0x3B, 0x30, 0)});
        g.setPaint(glow);
        g.fillOval((int) (size * 0.08), (int) (size * 0.08), (int) (size * 0.84), (int) (size * 0.84));

        // Lightning bolt, scaled from a 24x24 design grid (matches Icons.zap in-app)
        GeneralPath bolt = new GeneralPath();
        float s = size / 24f;
        bolt.moveTo(13 * s, 3 * s);
        bolt.lineTo(5 * s, 14 * s);
        bolt.lineTo(11 * s, 14 * s);
        bolt.lineTo(10 * s, 21 * s);
        bolt.lineTo(19 * s, 10 * s);
        bolt.lineTo(13 * s, 10 * s);
        bolt.closePath();

        GradientPaint boltPaint = new GradientPaint(0, 0, new Color(0xFF, 0x5A, 0x52), 0, size, new Color(0xFF, 0x3B, 0x30));
        g.setPaint(boltPaint);
        g.fill(bolt);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) (size * 0.01f)));
        g.draw(bolt);

        g.dispose();
        return img;
    }

    private static void writeIco(List<byte[]> pngs, List<Integer> dims, File out) throws IOException {
        int count = pngs.size();
        int headerSize = 6 + 16 * count;
        int offset = headerSize;

        ByteBuffer header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0); // reserved
        header.putShort((short) 1); // type = icon
        header.putShort((short) count);

        for (int i = 0; i < count; i++) {
            int dim = dims.get(i);
            int len = pngs.get(i).length;
            header.put((byte) (dim >= 256 ? 0 : dim)); // width (0 = 256)
            header.put((byte) (dim >= 256 ? 0 : dim)); // height
            header.put((byte) 0);  // color count
            header.put((byte) 0);  // reserved
            header.putShort((short) 1);  // planes
            header.putShort((short) 32); // bit count
            header.putInt(len);
            header.putInt(offset);
            offset += len;
        }

        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(header.array());
            for (byte[] png : pngs) {
                fos.write(png);
            }
        }
    }
}
