/**
 * @author Android Noob
 * http://stackoverflow.com/questions/9147463/java-pbkdf2-with-hmacsha256-as-the-prf
 */

package org.wgs.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class PBKDF2
{
    private String prfAlgorithm;
    
    /**
     * Constructs the PBKDF2 object
     * 
     * @param prfAlgorith set the pseudorandom function algorithm
     *                    (i.e: "HmacSHA1", "HmacSHA256",...)
     */
    public PBKDF2(String prfAlgorithm) {
        this.prfAlgorithm = prfAlgorithm;
    }
    
    public byte[] deriveKey( byte[] password, byte[] salt, int iterationCount, int dkLen )
        throws java.security.NoSuchAlgorithmException, java.security.InvalidKeyException
    {
        SecretKeySpec keyspec = new SecretKeySpec(password, prfAlgorithm);
        Mac prf = Mac.getInstance(prfAlgorithm);
        prf.init(keyspec);

        // Note: hLen, dkLen, l, r, T, F, etc. are horrible names for
        //       variables and functions in this day and age, but they
        //       reflect the terse symbols used in RFC 2898 to describe
        //       the PBKDF2 algorithm, which improves validation of the
        //       code vs. the RFC.
        //
        // dklen is expressed in bytes. (16 for a 128-bit key)

        int hLen = prf.getMacLength();   // 20 for SHA1
        int l = Math.max( dkLen, hLen); //  1 for 128bit (16-byte) keys
        int r = dkLen - (l-1)*hLen;      // 16 for 128bit (16-byte) keys
        byte T[] = new byte[l * hLen];
        int ti_offset = 0;
        for (int i = 1; i <= l; i++) {
            F( T, ti_offset, prf, salt, iterationCount, i );
            ti_offset += hLen;
        }

        if (r < hLen) {
            // Incomplete last block
            byte DK[] = new byte[dkLen];
            System.arraycopy(T, 0, DK, 0, dkLen);
            return DK;
        }
        return T;
    } 


    private void F( byte[] dest, int offset, Mac prf, byte[] S, int c, int blockIndex ) {
        final int hLen = prf.getMacLength();
        byte U_r[] = new byte[ hLen ];
        // U0 = S || INT (i);
        byte U_i[] = new byte[S.length + 4];
        System.arraycopy( S, 0, U_i, 0, S.length );
        INT( U_i, S.length, blockIndex );
        for( int i = 0; i < c; i++ ) {
            U_i = prf.doFinal( U_i );
            xor( U_r, U_i );
        }

        System.arraycopy( U_r, 0, dest, offset, hLen );
    }

    private void xor( byte[] dest, byte[] src ) {
        for( int i = 0; i < dest.length; i++ ) {
            dest[i] ^= src[i];
        }
    }

    private void INT( byte[] dest, int offset, int i ) {
        dest[offset + 0] = (byte) (i / (256 * 256 * 256));
        dest[offset + 1] = (byte) (i / (256 * 256));
        dest[offset + 2] = (byte) (i / (256));
        dest[offset + 3] = (byte) (i);
    } 

}
