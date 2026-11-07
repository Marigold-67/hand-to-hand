package dev.marigold.handinhand;

/** Render-independent sphere intersection. Both rigid arms reach exactly the same world point. */
public final class HandGeometry {
    private HandGeometry() {}
    public record V(double x, double y, double z) {
        public V add(V b) { return new V(x+b.x,y+b.y,z+b.z); }
        public V sub(V b) { return new V(x-b.x,y-b.y,z-b.z); }
        public V mul(double n) { return new V(x*n,y*n,z*n); }
        public double dot(V b) { return x*b.x+y*b.y+z*b.z; }
        public double length() { return Math.sqrt(dot(this)); }
        public V unit() { return length() < 1e-9 ? new V(1,0,0) : mul(1/length()); }
    }
    public static V contact(V a, double ra, V b, double rb) {
        V diff=b.sub(a); double d=diff.length();
        if (!Double.isFinite(d+ra+rb) || ra <= 0 || rb <= 0 || d < 1e-5 || d > ra+rb || d < Math.abs(ra-rb)) return null;
        V axis=diff.mul(1/d);
        double along=(ra*ra-rb*rb+d*d)/(2*d);
        double height=Math.sqrt(Math.max(0,ra*ra-along*along));
        V down=new V(0,-1,0);
        V radial=down.sub(axis.mul(down.dot(axis)));
        if (radial.length()<1e-5) radial=new V(0,0,1).sub(axis.mul(axis.z));
        return a.add(axis.mul(along)).add(radial.unit().mul(height));
    }
    /** Nearest point on the current intersection circle; preserves both rigid arm lengths. */
    public static V projectContact(V a,double ra,V b,double rb,V preferred){
        V fallback=contact(a,ra,b,rb);if(fallback==null||preferred==null)return fallback;
        V axis=b.sub(a);double d=axis.length();axis=axis.mul(1/d);
        double along=(ra*ra-rb*rb+d*d)/(2*d),height=Math.sqrt(Math.max(0,ra*ra-along*along));
        V center=a.add(axis.mul(along)),radial=preferred.sub(center);radial=radial.sub(axis.mul(radial.dot(axis)));
        if(!Double.isFinite(radial.length())||radial.length()<1e-5)return fallback;
        return center.add(radial.unit().mul(height));
    }
}
