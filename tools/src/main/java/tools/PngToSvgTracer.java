package tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.util.*;
import java.awt.Color;

/**
 * PNG → SVG centerline tracer.
 * - Binarize (Otsu or custom threshold)
 * - Skeletonize (Zhang–Suen)
 * - Trace polylines (endpoints & cycles)
 * - Simplify (RDP)
 * - Smooth (Catmull–Rom → cubic Bezier)
 * - Export SVG paths with stroke (no fills, no rect mosaics)
 *
 * Usage:
 *   java tools.PngToSvgTracer in.png [out.svg] [threshold=128] [rdpEps=0.25] [tension=0.6]
 *       [minPath=4] [strokeWidth=0.5] [scale=1.0]
 *
 * Example:
 *   java tools.PngToSvgTracer hamster.png hamster_traced.svg 128 0.25 0.6 4 0.5 1.0
 */
public class PngToSvgTracer {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage:");
            System.out.println("  java tools.PngToSvgTracer in.png [out.svg] [threshold=128] [rdpEps=0.25] [tension=0.6] [minPath=4] [strokeWidth=0.5] [scale=1.0]");
            System.out.println("Example:");
            System.out.println("  java tools.PngToSvgTracer hamster.png hamster_traced.svg 128 0.25 0.6 4 0.5 1.0");
            return;
        }
        String inPath = args[0];
        String outPath = args.length > 1 ? args[1] : defaultOutPath(inPath);
        int threshold = args.length > 2 ? Integer.parseInt(args[2]) : 128;     // -1 = Otsu
        double rdpEps = args.length > 3 ? Double.parseDouble(args[3]) : 0.25;  // упрощение в px
        double tension = args.length > 4 ? Double.parseDouble(args[4]) : 0.6;  // 1.0 — классический CR→Bezier
        int minPath = args.length > 5 ? Integer.parseInt(args[5]) : 4;        // минимальная длина полилинии (точек)
        double strokeWidth = args.length > 6 ? Double.parseDouble(args[6]) : 0.5; // итоговая толщина штриха в SVG
        double scale = args.length > 7 ? Double.parseDouble(args[7]) : 1.0;   // масштаб точек в SVG

        BufferedImage src = ImageIO.read(new File(inPath));
        if (src.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage tmp = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            tmp.getGraphics().drawImage(src, 0, 0, null);
            src = tmp;
        }
        int W = src.getWidth(), H = src.getHeight();

        // === 1) to grayscale + alpha-aware ===
        double[][] gray = toGrayscale(src);      // 0..1 (1=white)
        // инверсия для "чернила": 1 - gray (чернила → большие значения)
        double[][] ink = new double[H][W];
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) ink[y][x] = 1.0 - gray[y][x];

        // === 2) Threshold (Otsu by default) ===
        boolean[][] bin = threshold(ink, threshold);

        // небольшая чистка: удалим изолированные пиксели/«пыль»
        bin = despeckle(bin);

        // === 3) Skeletonize (Zhang–Suen) ===
        boolean[][] skel = skeletonize(bin);

        // === 4) Trace polylines ===
        List<List<Point>> paths = tracePaths(skel);

        // === 5) RDP simplify ===
        for (int i=0; i<paths.size(); i++) {
            List<Point> p = paths.get(i);
            p = rdp(p, rdpEps);
            if (p.size() >= minPath) paths.set(i, p); else { paths.remove(i); i--; }
        }

        // === 6) Build SVG d-strings using Catmull–Rom → cubic Bezier ===
        String inkColor = estimateInkColor(src, bin); // средний цвет "чернил"
        StringBuilder svgPaths = new StringBuilder();
        for (List<Point> p : paths) {
            String d = catmullRomToCubicPath(p, tension, scale);
            if (!d.isEmpty()) {
                svgPaths.append("<path d=\"").append(d)
                    .append("\" fill=\"none\" stroke=\"").append(inkColor)
                    .append("\" stroke-width=\"").append(fmt(strokeWidth))
                    .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n");
            }
        }

        // === 7) Write SVG ===
        String svg = ""
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + (int)Math.round(W*scale)
            + "\" height=\"" + (int)Math.round(H*scale) + "\" viewBox=\"0 0 " + (int)Math.round(W*scale)
            + " " + (int)Math.round(H*scale) + "\" shape-rendering=\"geometricPrecision\">\n"
            + svgPaths
            + "</svg>\n";

        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"))) {
            w.write(svg);
        }
        System.out.println("SVG saved: " + outPath);
        System.out.println("Paths: " + paths.size() + ", stroke=" + strokeWidth + ", inkColor=" + inkColor);
    }

    // ---------- Image helpers ----------

    // grayscale 0..1 from ARGB, учитывая альфу
    static double[][] toGrayscale(BufferedImage img) {
        int W = img.getWidth(), H = img.getHeight();
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        double[][] g = new double[H][W];
        for (int y=0; y<H; y++) {
            for (int x=0; x<W; x++) {
                int argb = px[y*W + x];
                int a = (argb >>> 24) & 0xff;
                int r = (argb >>> 16) & 0xff;
                int g8 = (argb >>> 8)  & 0xff;
                int b =  argb         & 0xff;
                // линейная яркость + учитываем прозрачность (прозрачное ≈ фон/белый)
                double lum = (0.2126*r + 0.7152*g8 + 0.0722*b) / 255.0;
                double alpha = a / 255.0;
                double v = alpha*lum + (1.0-alpha)*1.0; // смешиваем с белым
                g[y][x] = clamp01(v);
            }
        }
        return g;
    }

    // Otsu / fixed threshold in [0..255] for 'ink' (0..1)
    static boolean[][] threshold(double[][] ink01, int thr) {
        int H = ink01.length, W = ink01[0].length;
        boolean[][] out = new boolean[H][W];

        int t;
        if (thr >= 0) {
            t = clamp((int)Math.round(thr), 0, 255);
        } else {
            // Otsu — на гистограмме [0..255] от ink01 (1=чернила)
            int[] hist = new int[256];
            for (int y=0;y<H;y++) for (int x=0;x<W;x++) {
                int v = (int)Math.round(clamp01(ink01[y][x]) * 255.0);
                hist[v]++;
            }
            t = otsu(hist);
        }

        for (int y=0;y<H;y++) for (int x=0;x<W;x++) {
            int v = (int)Math.round(clamp01(ink01[y][x]) * 255.0);
            out[y][x] = v >= t;
        }
        return out;
    }

    static int otsu(int[] hist) {
        long total = 0;
        long sum = 0;
        for (int i=0;i<256;i++) { total += hist[i]; sum += (long)i*hist[i]; }
        long sumB = 0, wB = 0;
        double varMax = -1;
        int threshold = 0;
        for (int t=0;t<256;t++) {
            wB += hist[t];
            if (wB==0) continue;
            long wF = total - wB;
            if (wF==0) break;
            sumB += (long)t * hist[t];
            double mB = sumB / (double) wB;
            double mF = (sum - sumB) / (double) wF;
            double varBetween = wB * wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }
        return threshold;
    }

    // удаление изолированных пикселей
    static boolean[][] despeckle(boolean[][] a) {
        int H = a.length, W = a[0].length;
        boolean[][] out = copy(a);
        int[] dx = {-1,0,1,-1,1,-1,0,1};
        int[] dy = {-1,-1,-1,0,0,1,1,1};
        for (int y=1;y<H-1;y++) for (int x=1;x<W-1;x++) {
            if (!a[y][x]) continue;
            int n=0;
            for (int k=0;k<8;k++) if (a[y+dy[k]][x+dx[k]]) n++;
            if (n==0) out[y][x]=false; // удаляем только полностью изолированные пиксели
        }
        return out;
    }

    // ---------- Skeletonization (Zhang–Suen) ----------

    static boolean[][] skeletonize(boolean[][] img) {
        int H = img.length, W = img[0].length;
        boolean[][] a = copy(img);
        boolean changed;
        do {
            changed = false;
            List<int[]> toWhite = new ArrayList<>();
            for (int y=1;y<H-1;y++) for (int x=1;x<W-1;x++) {
                if (!a[y][x]) continue;
                int bp1 = neighborsCount(a, x, y);
                int ap  = transitions01(a, x, y);
                boolean p2 = a[y-1][x], p4 = a[y][x+1], p6=a[y+1][x], p8=a[y][x-1];
                if (bp1>=2 && bp1<=6 && ap==1 && (! (p2 && p4 && p6)) && (! (p4 && p6 && p8))) {
                    toWhite.add(new int[]{x,y});
                }
            }
            if (!toWhite.isEmpty()) { changed=true; for (int[] p:toWhite) a[p[1]][p[0]]=false; }

            toWhite.clear();
            for (int y=1;y<H-1;y++) for (int x=1;x<W-1;x++) {
                if (!a[y][x]) continue;
                int bp1 = neighborsCount(a, x, y);
                int ap  = transitions01(a, x, y);
                boolean p2 = a[y-1][x], p4 = a[y][x+1], p6=a[y+1][x], p8=a[y][x-1];
                if (bp1>=2 && bp1<=6 && ap==1 && (! (p2 && p4 && p8)) && (! (p2 && p6 && p8))) {
                    toWhite.add(new int[]{x,y});
                }
            }
            if (!toWhite.isEmpty()) { changed=true; for (int[] p:toWhite) a[p[1]][p[0]]=false; }

        } while (changed);
        return a;
    }

    static int neighborsCount(boolean[][] a, int x, int y) {
        int n=0;
        if (a[y-1][x]) n++; if (a[y-1][x+1]) n++; if (a[y][x+1]) n++; if (a[y+1][x+1]) n++;
        if (a[y+1][x]) n++; if (a[y+1][x-1]) n++; if (a[y][x-1]) n++; if (a[y-1][x-1]) n++;
        return n;
    }

    static int transitions01(boolean[][] a, int x, int y) {
        // порядок p2,p3,p4,p5,p6,p7,p8,p9 (по часовой)
        boolean[] p = new boolean[]{
            a[y-1][x], a[y-1][x+1], a[y][x+1], a[y+1][x+1],
            a[y+1][x], a[y+1][x-1], a[y][x-1], a[y-1][x-1]
        };
        int s=0;
        for (int i=0;i<8;i++) if (!p[i] && p[(i+1)%8]) s++;
        return s;
    }

    // ---------- Trace skeleton to polylines ----------

    static class Point { double x,y; Point(double x,double y){this.x=x;this.y=y;} }
    static int idx(int x,int y,int W){ return y*W+x; }

    static List<List<Point>> tracePaths(boolean[][] skel) {
        int H = skel.length, W = skel[0].length;
        boolean[] used = new boolean[W*H];

        int[] dx8 = {-1,0,1,-1,1,-1,0,1};
        int[] dy8 = {-1,-1,-1,0,0,1,1,1};

        // степень узла
        int[] deg = new int[W*H];
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) if (skel[y][x]) {
            int d=0;
            for (int k=0;k<8;k++){
                int nx=x+dx8[k], ny=y+dy8[k];
                if (nx>=0&&nx<W&&ny>=0&&ny<H&&skel[ny][nx]) d++;
            }
            deg[idx(x,y,W)] = d;
        }

        List<List<Point>> paths = new ArrayList<>();

        // Сначала — от эндпоинтов (deg==1)
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) if (skel[y][x] && deg[idx(x,y,W)]==1 && !used[idx(x,y,W)]) {
            paths.add(traceFrom(x,y,skel,used,deg));
        }

        // Затем — циклы (все оставшиеся неиспользованные пиксели с deg==2)
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) if (skel[y][x] && !used[idx(x,y,W)]) {
            List<Point> p = traceFrom(x,y,skel,used,deg);
            if (p.size()>=2) paths.add(p);
        }

        return paths;
    }

    static List<Point> traceFrom(int sx,int sy, boolean[][] skel, boolean[] used, int[] deg) {
        int H = skel.length, W = skel[0].length;
        int[] dx8 = {-1,0,1,-1,1,-1,0,1};
        int[] dy8 = {-1,-1,-1,0,0,1,1,1};

        List<Point> poly = new ArrayList<>();
        int x = sx, y = sy;
        int px = Integer.MIN_VALUE, py = Integer.MIN_VALUE;

        while (true) {
            int id = idx(x,y,W);
            if (used[id]) break;
            used[id] = true;
            poly.add(new Point(x+0.5, y+0.5)); // центр пикселя

            // ищем следующий сосед (кроме предыдущего)
            int nextX = Integer.MIN_VALUE, nextY = Integer.MIN_VALUE, candidates=0;
            for (int k=0;k<8;k++) {
                int nx=x+dx8[k], ny=y+dy8[k];
                if (nx<0||nx>=W||ny<0||ny>=H||!skel[ny][nx]) continue;
                if (nx==px && ny==py) continue;
                if (!used[idx(nx,ny,W)]) { nextX=nx; nextY=ny; candidates++; }
            }

            if (candidates==0) break; // конец пути
            // для развилок (deg>2) стараемся выбрать ближайшего направления к вектору (x-px,y-py)
            if (deg[id]>2 && px!=Integer.MIN_VALUE) {
                double bestDot = -1e9;
                int bx=nextX, by=nextY;
                double vx = x - px, vy = y - py;
                for (int k=0;k<8;k++){
                    int nx=x+dx8[k], ny=y+dy8[k];
                    if (nx<0||nx>=W||ny<0||ny>=H||!skel[ny][nx]) continue;
                    if (nx==px && ny==py) continue;
                    if (used[idx(nx,ny,W)]) continue;
                    double dot = vx*(nx-x) + vy*(ny-y);
                    if (dot > bestDot) { bestDot=dot; bx=nx; by=ny; }
                }
                nextX=bx; nextY=by;
            }

            px = x; py = y;
            x = nextX; y = nextY;
        }
        return poly;
    }

    // ---------- Ramer–Douglas–Peucker ----------

    static List<Point> rdp(List<Point> pts, double eps) {
        if (pts.size()<=2) return new ArrayList<>(pts);
        boolean[] keep = new boolean[pts.size()];
        keep[0]=keep[pts.size()-1]=true;
        rdpRec(pts, 0, pts.size()-1, eps, keep);
        List<Point> out = new ArrayList<>();
        for (int i=0;i<pts.size();i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }
    static void rdpRec(List<Point> p, int s, int e, double eps, boolean[] keep) {
        double maxD=0; int idx=-1;
        Point a=p.get(s), b=p.get(e);
        for (int i=s+1;i<e;i++){
            double d = distPointToSeg(p.get(i), a, b);
            if (d>maxD){ maxD=d; idx=i; }
        }
        if (maxD>eps){
            keep[idx]=true;
            rdpRec(p,s,idx,eps,keep);
            rdpRec(p,idx,e,eps,keep);
        }
    }
    static double distPointToSeg(Point p, Point a, Point b){
        double vx=b.x-a.x, vy=b.y-a.y;
        double wx=p.x-a.x, wy=p.y-a.y;
        double t=(vx*wx+vy*wy)/(vx*vx+vy*vy+1e-9);
        t=Math.max(0,Math.min(1,t));
        double dx=a.x+t*vx-p.x, dy=a.y+t*vy-p.y;
        return Math.hypot(dx,dy);
    }

    // ---------- Catmull–Rom → cubic Bezier ----------

    static String catmullRomToCubicPath(List<Point> pts, double tension, double scale) {
        if (pts.size()<2) return "";
        StringBuilder d = new StringBuilder();
        Point p0 = pts.get(0);
        d.append("M ").append(fmt(p0.x*scale)).append(" ").append(fmt(p0.y*scale));

        for (int i=0; i<pts.size()-1; i++) {
            Point P0 = i>0 ? pts.get(i-1) : pts.get(i);
            Point P1 = pts.get(i);
            Point P2 = pts.get(i+1);
            Point P3 = (i+2<pts.size()) ? pts.get(i+2) : pts.get(i+1);

            // uniform Catmull–Rom → cubic Bezier
            double c1x = P1.x + (P2.x - P0.x) * (tension/6.0);
            double c1y = P1.y + (P2.y - P0.y) * (tension/6.0);
            double c2x = P2.x - (P3.x - P1.x) * (tension/6.0);
            double c2y = P2.y - (P3.y - P1.y) * (tension/6.0);

            d.append(" C ")
                .append(fmt(c1x*scale)).append(" ").append(fmt(c1y*scale)).append(" ")
                .append(fmt(c2x*scale)).append(" ").append(fmt(c2y*scale)).append(" ")
                .append(fmt(P2.x*scale)).append(" ").append(fmt(P2.y*scale));
        }
        return d.toString();
    }

    // ---------- Utils ----------

    static boolean[][] copy(boolean[][] a){
        int H=a.length,W=a[0].length; boolean[][] b=new boolean[H][W];
        for(int y=0;y<H;y++) System.arraycopy(a[y],0,b[y],0,W);
        return b;
    }
    static int clamp(int v,int lo,int hi){ return Math.max(lo, Math.min(hi,v)); }
    static double clamp01(double v){ return Math.max(0.0, Math.min(1.0, v)); }
    static String fmt(double v){ return ((v==Math.rint(v))? String.valueOf((long)Math.rint(v)) : String.format(java.util.Locale.US,"%.3f",v)); }

    static String defaultOutPath(String in){
        int idx=in.lastIndexOf('.');
        String base=(idx>=0)?in.substring(0,idx):in;
        return base+"_traced.svg";
    }

    // средний цвет чернил по бинарной маске (удобно для «ручки»)
    static String estimateInkColor(BufferedImage img, boolean[][] mask){
        int W=img.getWidth(), H=img.getHeight();
        long rSum=0,gSum=0,bSum=0,n=0;
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        for(int y=0;y<H;y++) for(int x=0;x<W;x++){
            if (!mask[y][x]) continue;
            int argb=px[y*W+x];
            int r=(argb>>>16)&0xff, g=(argb>>>8)&0xff, b=argb&0xff;
            rSum+=r; gSum+=g; bSum+=b; n++;
        }
        if (n==0) return "#000000";
        int r=(int)(rSum/n), g=(int)(gSum/n), b=(int)(bSum/n);
        Color c = new Color(r,g,b);
        // немного притемним для уверенного контура
        c = new Color((int)(c.getRed()*0.85), (int)(c.getGreen()*0.85), (int)(c.getBlue()*0.95));
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
