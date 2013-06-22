package org.wgs.util;


public class HexUtils 
{
    public static byte[] hexStringToByteArray(String hex)
    {
       byte[] bytes = new byte[hex.length()/2];
       for (int i = 0; i < bytes.length; i++) {
          bytes[i] = (byte)Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
       }
       return bytes;
    }    
    
    public static String byteArrayToHexString(byte[] bytes)
    {
        StringBuffer retval = new StringBuffer((bytes!=null)? bytes.length*2 : 0);
        for (int n = 0; n < bytes.length; n++) {
            String hex = (java.lang.Integer.toHexString(bytes[n] & 0XFF));
            if (hex.length() == 1) retval.append("0");
            retval.append(hex);
        }
        return retval.toString();
    }    
    
}
