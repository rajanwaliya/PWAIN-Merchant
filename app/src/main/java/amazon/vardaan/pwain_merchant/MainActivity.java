package amazon.vardaan.pwain_merchant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    EditText customerPhone;
    EditText amount;
    EditText item;
    EditText sellerNote;
    Button sendPaymentRequest;
    Button generateStaticQR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    1);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        customerPhone = (EditText) findViewById(R.id.editPhone);
        amount = (EditText) findViewById(R.id.editAmount);
        item = (EditText) findViewById(R.id.editItem);
        sellerNote = (EditText) findViewById(R.id.editSellerNote);
        sendPaymentRequest = (Button) findViewById(R.id.paymentRequestButton);
        generateStaticQR = (Button) findViewById(R.id.static_qr_button);
        sendPaymentRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {

                    String url = new ShortenUrl().execute(new MerchantBackendTask().execute().get()).get();
                    Log.wtf("test app", "obtained url=" + url);
                    Intent i = new Intent(getApplicationContext(), QrActivity.class);
                    i.putExtra("url", url);
                    sendSMS(customerPhone.getText().toString(), url);
                    startActivity(i);
                } catch (Exception e) {
                    Log.e("error", "error", e);
                }
            }
        });
        generateStaticQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Map<String, String> staticParams = getParams();
                staticParams.remove("orderTotalAmount");
                Intent i = new Intent(getApplicationContext(), QrActivity.class);
                i.putExtra("url", staticParams.toString());
                Log.wtf("static", staticParams.toString());
                startActivity(i);
            }
        });

    }


    public void sendSMS(String phoneNumber, String url) {

        Log.e("sms", "sending sms");
        String message = String.format("Here is your payment url: %s", url);
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, "PWAIN", message, null, null);
    }

    Map<String, String> getParams() {
        return new HashMap<String, String>() {{
            put("orderTotalAmount", amount.getText().toString());
            put("orderTotalCurrencyCode", "INR");
            put("isSandbox", "true");
            if (sellerNote.getText() != null && sellerNote.getText().toString().length() > 0) {
                String sellerNoteString = sellerNote.getText().toString();
                sellerNoteString = sellerNoteString.replaceAll("&quot;", "\"");
                Log.d("SellerNote", sellerNoteString);
                put("sellerNote", sellerNoteString);
            }
            put("sellerOrderId", item.getText().toString() + "_" + UUID.randomUUID().toString());
            put("transactionTimeout", "1000");
        }};
    }

    private class ShortenUrl extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            try {
                String shortenUrl = "http://tinyurl.com/api-create.php";
                shortenUrl = shortenUrl + "?url=" + strings[0];
                HttpGet httpGet = new HttpGet(shortenUrl);
                HttpClient httpclient = new DefaultHttpClient();
                HttpResponse response = httpclient.execute(httpGet);
                int status = response.getStatusLine().getStatusCode();
                if (status == 200) {
                    HttpEntity entity = response.getEntity();
                    String data = EntityUtils.toString(entity).trim();
                    Log.d("response", data);
                    return data;
                }
            } catch (Exception e) {
                Log.wtf("Exception","Exception here",e);
            }
            return null;
        }
    }

    private class MerchantBackendTask extends AsyncTask<Void, Void, String> {

        private static final String LOG_TAG = "merchant server";

        @Override
        protected String doInBackground(Void... strings) {
            try {
                Log.d(LOG_TAG, "Fetching from merchant endpoint");
                HttpGet httpGet;
                Uri uri = createUri(new URL("http://ec2-35-162-20-220.us-west-2.compute.amazonaws.com"),
                        getParams(),
                        "/prod/signAndEncrypt.jsp");
                Log.d("requestUri", uri.toString());
                httpGet = new HttpGet(uri.toString());

                HttpClient httpclient = new DefaultHttpClient();
                HttpResponse response = httpclient.execute(httpGet);

                int status = response.getStatusLine().getStatusCode();
                if (status == 200) {
                    HttpEntity entity = response.getEntity();
                    String data = EntityUtils.toString(entity).trim();
                    Log.d("response", data);
                    Map<String, String> decodedParams = getDecodedQueryParameters(data);
                    for (String key : decodedParams.keySet()) {
                        Log.d("key", key);
                        Log.d("value", decodedParams.get(key));
                    }
                    decodedParams.put("requestId", UUID.randomUUID().toString());
                    decodedParams.put("redirectUrl", "amzn" + "://amazonpay.amazon.in/" + "customerApp");

                    String url = createURLString(new URL("https://amazonpay.amazon.in"),
                            decodedParams,
                            "/initiatePayment");
                    Log.wtf(LOG_TAG, "url=" + url);
                    return url;
                } else

                {
                    Log.d(LOG_TAG, String.format("Unable to sign payload. Received following status code: %d",
                            status));
                }
            } catch (
                    Exception e)

            {
                Log.e(LOG_TAG, "ERROR IN MERCHANT SERVER", e);
            }
            return null;
        }


        String createURLString(URL endpoint, Map<String, String> parameters, String path) {
            return createUri(endpoint, parameters, path).toString();
        }

        /**
         * Returns the uri created from the supplied parameters
         *
         * @param endpoint   The endpoint of the uri
         * @param parameters The parameters to be added to the uri
         * @param path       The path of the uri
         * @return The created URI based on the supplied params
         */
        private Uri createUri(URL endpoint, Map<String, String> parameters, String path) {
            Uri uri = Uri.parse(endpoint.toString());

            if (path != null && !path.isEmpty()) {
                uri = uri.buildUpon().path(path).build();
            }

            if (parameters != null && parameters.size() > 0) {
                uri = addQueryParameters(uri, parameters);
            }

            return uri;
        }

        /**
         * Add the supplied query params to the supplied URI
         *
         * @param uri        the uri to which the params have to be added
         * @param parameters the params that are to be added
         * @return the uri with the params added
         */
        private Uri addQueryParameters(Uri uri, Map<String, String> parameters) {
            for (String key : parameters.keySet()) {
                uri = uri.buildUpon().appendQueryParameter(key, parameters.get(key)).build();
            }
            return uri;
        }

        /**
         * Get the decoded Query params
         *
         * @param query the encoded query params
         * @return the decoded query params
         * @throws UnsupportedEncodingException thrown if the encoding used on the query params is not supported
         */
        Map<String, String> getDecodedQueryParameters(String query) throws UnsupportedEncodingException {
            if (query == null || query.trim().length() < 1) {
                return null;
            }
            HashMap<String, String> parameters = new HashMap<>();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int index = pair.indexOf("=");
                parameters.put(URLDecoder.decode(pair.substring(0, index), "UTF-8"), URLDecoder.decode(pair.substring
                        (index + 1), "UTF-8"));
            }
            return parameters;

        }

    }
}
