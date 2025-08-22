package tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;

public class PenScanToPng {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage:\n  java PenScanToPng <input.jpg> <output.png> [edgeSamples=20] [t0=0.06] [t1=0.18] [blueBoost=0.35] [feather=0.8] [alphaBlurRadius=0]");
            System.out.println("Example:\n  java PenScanToPng scan.jpg out.png 24 0.06 0.16 0.45 1.0 1");
            return;
        }
        String inPath = args[0];
        String outPath = args[1];

        int edgeSamples   = args.length > 2 ? Integer.parseInt(args[2]) : 20;   // сколько пикселей с краёв берём для оценки бумаги
        double t0         = args.length > 3 ? Double.parseDouble(args[3]) : 0.06; // нижний порог smoothstep (почти бумага)
        double t1         = args.length > 4 ? Double.parseDouble(args[4]) : 0.18; // верхний порог smoothstep (точно не бумага)
        double blueBoost  = args.length > 5 ? Double.parseDouble(args[5]) : 0.35; // усиление альфы для «синих» пикселей
        double feather    = args.length > 6 ? Double.parseDouble(args[6]) : 0.8;  // дополнительное «перо» по краю (0..2)
        int alphaBlurRad  = args.length > 7 ? Integer.parseInt(args[7]) : 0;      // радиус лёгкого блюра альфы (0 = выкл)

        BufferedImage src = ImageIO.read(new File(inPath));
        if (src.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage tmp = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            tmp.getGraphics().drawImage(src, 0, 0, null);
            src = tmp;
        }

        int w = src.getWidth(), h = src.getHeight();
        int[] pixels = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();

        // 1) Оценим «цвет бумаги»
        int[] paper = estimatePaperRGB(src, edgeSamples);
        double pr = paper[0], pg = paper[1], pb = paper[2];

        // 2) Рассчёт альфы для каждого пикселя
        double maxDist = Math.sqrt(255*255*3); // нормировка евклидовой дистанции в RGB
        int[] out = Arrays.copyOf(pixels, pixels.length);

        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int a = (argb >>> 24) & 0xff;
            int r = (argb >>> 16) & 0xff;
            int g = (argb >>>  8) & 0xff;
            int b = (argb       ) & 0xff;

            // расстояние цвета до бумаги
            double dist = Math.sqrt(sq(r - pr) + sq(g - pg) + sq(b - pb)) / maxDist;

            // базовая альфа через плавный порог
            double baseAlpha = smoothstep(t0, t1, dist);

            // HSV для «синего бонуса»
            float[] hsv = rgbToHsv(r, g, b);
            double hue = hsv[0];       // 0..360
            double sat = hsv[1];       // 0..1
            double val = hsv[2];       // 0..1 (яркость)

            // гауссов «синего» вокруг 215–240°
            double hueCenter = 225.0;
            double hueSigma = 28.0;
            double hueScore = Math.exp(-0.5 * sq(angleDiff(hue, hueCenter) / hueSigma));

            // чем темнее и насыщеннее синий, тем сильнее сохраняем
            double blueAlpha = hueScore * clamp(sat, 0, 1) * clamp(1.0 - val, 0, 1);
            double alpha = baseAlpha + blueBoost * blueAlpha;

            // лёгкое «перо» на границе
            alpha = Math.pow(clamp(alpha, 0, 1), 1.0 / clamp(1e-6 + feather, 1e-6, 5));

            int A = (int) Math.round(clamp(alpha, 0, 1) * 255.0);

            // Цвет оставляем исходный, альфа — рассчитанная
            out[i] = (A << 24) | (r << 16) | (g << 8) | b;
        }

        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        dst.getRaster().setDataElements(0, 0, w, h, out);

        // 3) Лёгкий блюр альфы (опционально для ещё более мягкого края)
        if (alphaBlurRad > 0) {
            dst = blurAlpha(dst, alphaBlurRad);
        }

        ImageIO.write(dst, "PNG", new File(outPath));

        System.out.printf(
            "Done.\nPaper RGB ~ [%.1f, %.1f, %.1f]\nSaved: %s\n",
            pr, pg, pb, outPath);
    }

    private static double sq(double x) { return x * x; }
    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }

    private static double smoothstep(double e0, double e1, double x) {
        x = clamp((x - e0) / Math.max(1e-9, (e1 - e0)), 0.0, 1.0);
        return x * x * (3 - 2 * x);
    }

    // минимальная разность углов в градусах (0..180)
    private static double angleDiff(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180 ? 360 - d : d;
    }

    private static int[] estimatePaperRGB(BufferedImage img, int band) {
        int w = img.getWidth(), h = img.getHeight();
        band = Math.max(2, Math.min(Math.min(w, h) / 8, band));
        long rs = 0, gs = 0, bs = 0, n = 0;

        // верх/низ
        for (int y : new int[]{0, 1, h - 1, h - 2}) {
            for (int x = 0; x < w; x += 1) {
                int c = img.getRGB(x, y);
                rs += (c >> 16) & 0xff;
                gs += (c >> 8) & 0xff;
                bs += c & 0xff;
                n++;
            }
        }
        // лево/право
        for (int x : new int[]{0, 1, w - 1, w - 2}) {
            for (int y = 0; y < h; y += 1) {
                int c = img.getRGB(x, y);
                rs += (c >> 16) & 0xff;
                gs += (c >> 8) & 0xff;
                bs += c & 0xff;
                n++;
            }
        }
        int r = (int) (rs / n), g = (int) (gs / n), b = (int) (bs / n);
        return new int[]{r, g, b};
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == rf) h = (60 * ((gf - bf) / d) + 360) % 360;
        else if (max == gf) h = (60 * ((bf - rf) / d) + 120) % 360;
        else h = (60 * ((rf - gf) / d) + 240) % 360;
        float s = max == 0 ? 0 : d / max;
        float v = max;
        return new float[]{h, s, v};
    }

    // Бокс-блюр только альфы: один проход по строкам и столбцам
    private static BufferedImage blurAlpha(BufferedImage src, int radius) {
        int w = src.getWidth(), h = src.getHeight();
        int[] data = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();
        int[] a = new int[data.length];
        int[] tmpA = new int[data.length];

        for (int i = 0; i < data.length; i++) a[i] = (data[i] >>> 24) & 0xff;

        // горизонталь
        int win = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int sum = 0;
            int idx = y * w;
            for (int x = -radius; x <= radius; x++) {
                int xi = Math.max(0, Math.min(w - 1, x));
                sum += a[idx + xi];
            }
            for (int x = 0; x < w; x++) {
                tmpA[idx + x] = sum / win;
                int xOut = x - radius;
                int xIn = x + radius + 1;
                if (xOut >= 0) sum -= a[idx + xOut];
                if (xIn < w) sum += a[idx + xIn];
            }
        }
        // вертикаль
        for (int x = 0; x < w; x++) {
            int sum = 0;
            int idx = x;
            for (int y = -radius; y <= radius; y++) {
                int yi = Math.max(0, Math.min(h - 1, y));
                sum += tmpA[yi * w + x];
            }
            for (int y = 0; y < h; y++) {
                int A = sum / win;
                int i = y * w + x;
                int rgb = data[i] & 0x00ffffff;
                data[i] = (A << 24) | rgb;
                int yOut = y - radius;
                int yIn = y + radius + 1;
                if (yOut >= 0) sum -= tmpA[yOut * w + x];
                if (yIn < h) sum += tmpA[yIn * w + x];
            }
        }
        return src;
    }
}
