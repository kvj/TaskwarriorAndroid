package kvj.taskw.sync;

import android.util.Base64;

import org.kvj.bravo7.log.Logger;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;

import kvj.taskw.sync.der.DerInputStream;
import kvj.taskw.sync.der.DerValue;

public class SSLHelper {

    static Logger logger = Logger.forClass(SSLHelper.class);

    protected static byte[] fromStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int size = 0;
        while ((size = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, size);
        }
        inputStream.close();
        outputStream.close();
        return outputStream.toByteArray();
    }

    protected static byte[] parseDERFromPEM(String data) throws IOException {
        String[] tokens = data.split("\n");
        StringBuilder b64 = new StringBuilder();
        int delimiters = 0;
        for (String token : tokens) {
            if (token.startsWith("-----") && token.endsWith("-----")) {
                delimiters++;
                if (delimiters  == 2) {
                    break;
                }
                continue;
            }
            if (delimiters == 1) {
                b64.append(token);
            }
        }
        return Base64.decode(b64.toString(), Base64.DEFAULT);
    }

    protected static java.security.cert.Certificate loadCertificate(InputStream stream) throws CertificateException, FileNotFoundException {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        return fact.generateCertificate(stream);
    }

    protected static PrivateKey loadPrivateKey(InputStream stream) throws IOException, GeneralSecurityException {
        byte[] key = fromStream(stream);
        DerInputStream derReader = new DerInputStream(parseDERFromPEM(new String(key)));
        DerValue[] seq = derReader.getSequence(0);
        if (seq.length < 9) {
            throw new GeneralSecurityException("Could not parse a PKCS1 private key.");
        }
        BigInteger modulus = seq[1].getBigInteger();
        BigInteger publicExp = seq[2].getBigInteger();
        BigInteger privateExp = seq[3].getBigInteger();
        BigInteger prime1 = seq[4].getBigInteger();
        BigInteger prime2 = seq[5].getBigInteger();
        BigInteger exp1 = seq[6].getBigInteger();
        BigInteger exp2 = seq[7].getBigInteger();
        BigInteger crtCoef = seq[8].getBigInteger();

        RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);

        KeyFactory factory = KeyFactory.getInstance("RSA");

        return factory.generatePrivate(keySpec);
    }

    protected static KeyManagerFactory keyManagerFactoryPEM(InputStream certStream, InputStream keyStream) throws GeneralSecurityException, IOException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        Certificate cert = loadCertificate(certStream);
        keyStore.load(null);
        logger.d("Keystore:", cert.getPublicKey().getAlgorithm(), cert.getPublicKey().getFormat());
        keyStore.setCertificateEntry("certificate", cert);
        keyStore.setKeyEntry("private-key", loadPrivateKey(keyStream), "".toCharArray(), new Certificate[]{cert});
        kmf.init(keyStore, "".toCharArray());
        return kmf;
    }

    protected static TrustManagerFactory trustManagerFactoryPEM(InputStream stream) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        Certificate cert = loadCertificate(stream);
        logger.d("Truststore:", cert.getPublicKey().getAlgorithm(), cert.getPublicKey().getFormat());
        keyStore.setCertificateEntry("ca", cert);
        tmf.init(keyStore);
        return tmf;
    }

    protected static SSLSocketFactory tlsSocket(KeyManagerFactory kmf, TrustManagerFactory tmf) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, InvalidKeySpecException, UnrecoverableKeyException, KeyManagementException, UnrecoverableKeyException {
        SSLContext context = SSLContext.getInstance("TLSv1");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    public static SSLSocketFactory tlsSocket(InputStream caStream, InputStream certStream, InputStream keyStream)
        throws GeneralSecurityException, IOException {
        return tlsSocket(keyManagerFactoryPEM(certStream, keyStream), trustManagerFactoryPEM(caStream));
    }
}
