package ar.cabal.jpos;
import org.jpos.iso.*;
import org.json.simple.JSONValue;

import java.io.*;
import java.util.*;

/**
 * packs/unpacks ISOMsgs into JSON representation
 *
 * @author apr@jpos.org
 * @see ISOPackager
 */

public class JSONPackager implements ISOPackager
{
    private ByteArrayOutputStream out;
    private PrintStream p;

    public JSONPackager() throws ISOException {
        super();
    }

    public byte[] pack (ISOComponent m) throws ISOException {
        Map json = new LinkedHashMap();
        put (json, m, "");
        return JSONValue.toJSONString(json).getBytes(ISOUtil.CHARSET);
    }

    public synchronized int unpack (ISOComponent c, byte[] b)
            throws ISOException
    {
        Map map = (Map) JSONValue.parse(new String(b));
        ISOMsg m = (ISOMsg) c;
        for (Map.Entry entry : (Set<Map.Entry>) map.entrySet()) {
            String k = (String) entry.getKey();
            String v = (String) entry.getValue();
            if (k.endsWith("b")) {
                m.set(k.substring(0, k.length()-1), ISOUtil.hex2byte(v));
            } else {
                m.set(k, v);
            }
        }
        return b.length;
    }

    public synchronized void unpack (ISOComponent c, InputStream in)
            throws ISOException
    {
        throw new ISOException ("stream unpack not supported");
    }

    public String getFieldDescription(ISOComponent m, int fldNumber) {
        return "Field " + fldNumber;
    }
    public ISOMsg createISOMsg () {
        return new ISOMsg();
    }
    public String getDescription () {
        return getClass().getName();
    }

    private void put (Map map, ISOComponent c, String prefix) throws ISOException {
        if (c.getComposite() != null) {
            Map children = c.getChildren();
            for (Map.Entry entry : (Set<Map.Entry>) children.entrySet()) {
                ISOComponent cc = (ISOComponent) entry.getValue();
                put (map, cc, c.getFieldNumber() > 0 ? prefix + Integer.toString(c.getFieldNumber()) + "." : prefix);
            }
        }
        else if (c instanceof ISOField)
            map.put(prefix + c.getKey(), ((ISOField)c).getValue());
        else if (c instanceof ISOBinaryField)
            map.put(prefix + c.getKey() + "b", ISOUtil.hexString(((ISOBinaryField) c).getBytes()));
    }
}