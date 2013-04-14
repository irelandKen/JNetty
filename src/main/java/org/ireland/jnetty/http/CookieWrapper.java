package org.ireland.jnetty.http;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import io.netty.handler.codec.http.Cookie;

/**
 * 
 * Wrape javax.servlet.http.Cookie as a io.netty.handler.codec.http.Cookie
 * 
 * @author KEN
 *
 */

public class CookieWrapper //implements Cookie
{
	/*private javax.servlet.http.Cookie cookie;
	
	private boolean discard;

	public CookieWrapper(javax.servlet.http.Cookie cookie)
	{
		super();
		this.cookie = cookie;
	}

    @Override
    public String getName() {
        return cookie.getName();
    }

    @Override
    public String getValue() {
        return cookie.getValue();
    }

    @Override
    public void setValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        
        cookie.setValue(value);
    }

    @Override
    public String getDomain() {
        return cookie.getDomain();
    }

    @Override
    public void setDomain(String domain) {
    	cookie.setDomain(validateValue("domain", domain));
    }

    @Override
    public String getPath() {
        return cookie.getPath();
    }

    @Override
    public void setPath(String path) {
    	cookie.setPath(validateValue("path", path));
    }

    @Override
    public String getComment() {
        return cookie.getComment();
    }

    @Override
    public void setComment(String comment) {
    	cookie.setComment( validateValue("comment", comment) );
    }

    @Override
    public String getCommentUrl() {
        return cookie.getPath();
    }

    @Override
    public void setCommentUrl(String commentUrl) {
    	cookie.setPath(validateValue("commentUrl", commentUrl));
    }

    @Override
    public boolean isDiscard() {
        return this.discard;
    }

    @Override
    public void setDiscard(boolean discard) {
        this.discard = discard;
    }

    @Override
    public Set<Integer> getPorts() {
        if (unmodifiablePorts == null) {
            unmodifiablePorts = Collections.unmodifiableSet(ports);
        }
        return unmodifiablePorts;
    }

    @Override
    public void setPorts(int... ports) {
        if (ports == null) {
            throw new NullPointerException("ports");
        }

        int[] portsCopy = ports.clone();
        if (portsCopy.length == 0) {
            unmodifiablePorts = this.ports = Collections.emptySet();
        } else {
            Set<Integer> newPorts = new TreeSet<Integer>();
            for (int p: portsCopy) {
                if (p <= 0 || p > 65535) {
                    throw new IllegalArgumentException("port out of range: " + p);
                }
                newPorts.add(Integer.valueOf(p));
            }
            this.ports = newPorts;
            unmodifiablePorts = null;
        }
    }

    @Override
    public void setPorts(Iterable<Integer> ports) {
        Set<Integer> newPorts = new TreeSet<Integer>();
        for (int p: ports) {
            if (p <= 0 || p > 65535) {
                throw new IllegalArgumentException("port out of range: " + p);
            }
            newPorts.add(Integer.valueOf(p));
        }
        if (newPorts.isEmpty()) {
            unmodifiablePorts = this.ports = Collections.emptySet();
        } else {
            this.ports = newPorts;
            unmodifiablePorts = null;
        }
    }

    @Override
    public long getMaxAge() {
        return maxAge;
    }

    @Override
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Cookie)) {
            return false;
        }

        Cookie that = (Cookie) o;
        if (!getName().equalsIgnoreCase(that.getName())) {
            return false;
        }

        if (getPath() == null) {
            if (that.getPath() != null) {
                return false;
            }
        } else if (that.getPath() == null) {
            return false;
        } else if (!getPath().equals(that.getPath())) {
            return false;
        }

        if (getDomain() == null) {
            if (that.getDomain() != null) {
                return false;
            }
        } else if (that.getDomain() == null) {
            return false;
        } else {
            return getDomain().equalsIgnoreCase(that.getDomain());
        }

        return true;
    }

    @Override
    public int compareTo(Cookie c) {
        int v;
        v = getName().compareToIgnoreCase(c.getName());
        if (v != 0) {
            return v;
        }

        if (getPath() == null) {
            if (c.getPath() != null) {
                return -1;
            }
        } else if (c.getPath() == null) {
            return 1;
        } else {
            v = getPath().compareTo(c.getPath());
            if (v != 0) {
                return v;
            }
        }

        if (getDomain() == null) {
            if (c.getDomain() != null) {
                return -1;
            }
        } else if (c.getDomain() == null) {
            return 1;
        } else {
            v = getDomain().compareToIgnoreCase(c.getDomain());
            return v;
        }

        return 0;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getName());
        buf.append('=');
        buf.append(getValue());
        if (getDomain() != null) {
            buf.append(", domain=");
            buf.append(getDomain());
        }
        if (getPath() != null) {
            buf.append(", path=");
            buf.append(getPath());
        }
        if (getComment() != null) {
            buf.append(", comment=");
            buf.append(getComment());
        }
        if (getMaxAge() >= 0) {
            buf.append(", maxAge=");
            buf.append(getMaxAge());
            buf.append('s');
        }
        if (isSecure()) {
            buf.append(", secure");
        }
        if (isHttpOnly()) {
            buf.append(", HTTPOnly");
        }
        return buf.toString();
    }

    private static String validateValue(String name, String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i ++) {
            char c = value.charAt(i);
            switch (c) {
            case '\r': case '\n': case '\f': case 0x0b: case ';':
                throw new IllegalArgumentException(
                        name + " contains one of the following prohibited characters: " +
                        ";\\r\\n\\f\\v (" + value + ')');
            }
        }
        return value;
    }*/
}
