package com.example.helloworld;

import android.util.Log;

import org.json.simple.JSONArray;

import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MyAppParallelDots {

        private final String api_key;
        private final String host="https://apis.paralleldots.com/v4/";

        public MyAppParallelDots(String api_key){
            this.api_key = api_key;
            try{
                setUpCert("apis.paralleldots.com");
            }catch(Exception ex){
                Log.e(MyAppParallelDots.class.getName(), ex.toString());
            }
        }
        public void setUpCert(String hostname) throws Exception{
            SSLSocketFactory factory = HttpsURLConnection.getDefaultSSLSocketFactory();

            SSLSocket socket = (SSLSocket) factory.createSocket(hostname, 443);
            try {
                socket.startHandshake();
                socket.close();
                Log.d(MyAppParallelDots.class.getName(), "No errors, certificate is already trusted");
                return;
            } catch (SSLException e) {
                Log.e(MyAppParallelDots.class.getName(), e.toString());
            }

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] password = "changeit".toCharArray();
            ks.load(null, password);

            SSLContext context = SSLContext.getInstance("TLS");
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
            SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
            context.init(null, new TrustManager[]{tm}, null);
            factory = context.getSocketFactory();

            socket = (SSLSocket) factory.createSocket(hostname, 443);
            try {
                socket.startHandshake();
            } catch (SSLException e) {
                Log.e(MyAppParallelDots.class.getName(), e.getMessage());
            }
            X509Certificate[] chain = tm.chain;
            if (chain == null) {
                Log.d(MyAppParallelDots.class.getName(), "Could not obtain server certificate chain");
                return;
            }

            X509Certificate cert = chain[0];
            ks.setCertificateEntry(hostname, cert);

            //System.out.println("saving file paralleldotscacerts to working dir");
            //System.out.println("copy this file to your jre/lib/security folder");
            FileOutputStream fos = new FileOutputStream("paralleldotscacerts");
            ks.store(fos, password);
            fos.close();
        }

        private static class SavingTrustManager implements X509TrustManager {

            private final X509TrustManager tm;
            private X509Certificate[] chain;

            SavingTrustManager(X509TrustManager tm) {
                this.tm = tm;
            }

            public X509Certificate[] getAcceptedIssuers() {

                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException{
                throw new UnsupportedOperationException();
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                this.chain = chain;
                tm.checkServerTrusted(chain, authType);
            }
        }

        public String emotion(String text) throws Exception {
            if (this.api_key != null) {
                String url = host + "emotion";
                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("api_key", this.api_key)
                        .addFormDataPart("text", text)
                        .addFormDataPart("lang_code", "en")
                        .build();
                Request request = new Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("cache-control", "no-cache")
                        .build();
                Response response = client.newCall(request).execute();
                return response.body().string();
            } else {
                return ""; //Error: API key does not exist
            }
        }

        public String emotion_batch(JSONArray text_list) throws Exception {
            if(this.api_key!=null){
                String url = host + "emotion_batch";
                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("api_key", this.api_key)
                        .addFormDataPart("text", text_list.toString())
                        .addFormDataPart("lang_code", "en")
                        .build();
                Request request = new Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("cache-control", "no-cache")
                        .build();
                Response response = client.newCall(request).execute();
                return response.body().string();
            }else{
                return ""; //Error : API key does not exist
            }
        }

}
