package org.wgs.util;


public class Base64 
{
    public static byte[] decodeBase64ToByteArray(String str)
    {
        return java.util.Base64.getDecoder().decode(str);
    }

    public static String encodeByteArrayToBase64(byte[] data) 
    {
        return java.util.Base64.getEncoder().encodeToString(data);
    }
    
}
