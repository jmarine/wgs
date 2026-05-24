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
    
    public static String convertBase64UrlToBase64(String base64Url)
    {
        return base64Url.replace("-", "+").replace("_", "/");
    }
    
    public static String convertBase64ToBase64Url(String base64)
    {
        return base64.replace("+", "-").replace("/", "_");
    }    
    
}
