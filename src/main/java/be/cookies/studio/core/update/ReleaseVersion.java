package be.cookies.studio.core.update;

import java.math.BigInteger;
import java.util.regex.Pattern;

public record ReleaseVersion(String value) implements Comparable<ReleaseVersion> {
    private static final Pattern STABLE = Pattern.compile("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})");
    public ReleaseVersion { if (value == null || !STABLE.matcher(value).matches()) throw new IllegalArgumentException("Version stable invalide"); }
    public int compareTo(ReleaseVersion other) {
        var a=value.split("\\."); var b=other.value.split("\\.");
        for(int i=0;i<3;i++) { int c=new BigInteger(a[i]).compareTo(new BigInteger(b[i])); if(c!=0)return c; }
        return 0;
    }
    public String toString() { return value; }
}
