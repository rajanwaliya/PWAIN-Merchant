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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Collects required info from merchant and enables him to either send a payment request to his customer via SMS/QR
 * code or generate a static QR that the customer can scan to pay whenever
 */
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
        //1. Check if user has SEND_SMS Permission. If not request user if he wants to grant permission
        // P.S Permissions need to be declared in manifest.xml as well
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
        //2. gets a handle on the text fields we have declared in res/layout/activity_main.xml
        customerPhone = (EditText) findViewById(R.id.editPhone);
        amount = (EditText) findViewById(R.id.editAmount);
        item = (EditText) findViewById(R.id.editItem);
        sellerNote = (EditText) findViewById(R.id.editSellerNote);
        sendPaymentRequest = (Button) findViewById(R.id.paymentRequestButton);
        generateStaticQR = (Button) findViewById(R.id.static_qr_button);
        // Creating a listener for "send payment request" button click. This will be called every time the button is
        // clicked
        sendPaymentRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPaymentRequest();
            }
        });
        // Creating a listener for "send payment request" button click. This will be called every time the button is
        // clicked
        generateStaticQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getStaticQR();
            }
        });

    }

    /**
     * Sends an SMS to the user with the tiny URL and passes the URL to QRActivity so that it can be encoded into a
     * QR code
     */
    private void sendPaymentRequest() {
        try {
            // 3. Make a call to tinyUrl API to get a shortened URL.
            // ShortenUrl() is an ASYNC task. This is because all network calls have to be made asynchronously.
            // MerchantBackendTask() Makes a call to our Sign And Encrypt backend and returns the initiate payment url
            // .execute() starts the async task.
            // since we cant proceed till async task is finished we added the .get() at the end
            // .get() ensures that the code doesn't proceed further untill the async task is finished
            String url = new ShortenUrl().execute(new MerchantBackendTask().execute().get()).get();
            Log.wtf("test app", "obtained url=" + url);
            // 4. We are now going to take the initiate payment url and convert it to a QR code.
            // To do that we have to pass the url to QRActivity
            // only way to pass control to another activity is via an intent
            Intent i = new Intent(getApplicationContext(), QrActivity.class);
            i.putExtra("url", url);
            // 5. Sending an SMS to the customer with the tiny url and the targeted ad mail
            sendSMS(customerPhone.getText().toString(), String.format("Here is your payment url: %s", url));
            sendSMS(customerPhone.getText().toString(), "It looks like you recently purchased " + item.getText()
                    .toString() + ". You can get upto 20% off on " + item.getText()
                    .toString() + " using Jhinga Lala app and paying with Amazon Pay");
            //6. Sending the initiate payment URL to QR Activity so that it can be encoded into a QR
            startActivity(i);
        } catch (Exception e) {
            Log.e("error", "error", e);
        }
    }


    /**
     * Sends url needed for static payments to QR Activity so as to generate a resuable static QR
     */
    private void getStaticQR() {
        // 7. Gets the params needed to make a SIGN and ENCRYPT call
        Map<String, String> staticParams = getParams();
        // we remove total amount as that will be entered later by the customer in his app
        staticParams.remove("orderTotalAmount");
        Intent i = new Intent(getApplicationContext(), QrActivity.class);
        //8. here we give the SIGN and ENCRYPT url in the QR code. The customer app will add the amount and make a call
        // to merchant back to get a signed payload. We cant do that here as we cant determine the order amount
        i.putExtra("url", staticParams.toString());
        Log.wtf("static", staticParams.toString());
        startActivity(i);
    }


    /**
     * Sends an SMS to the customer with the supplied message
     *
     * @param phoneNumber
     * @param message
     */
    public void sendSMS(String phoneNumber, String message) {
        Log.e("sms", "sending sms");
       try {
           SmsManager smsManager = SmsManager.getDefault();
           smsManager.sendTextMessage(phoneNumber, "PWAIN", message, null, null);
       }catch (Exception e){
           Log.wtf("sms","",e);
       }
    }

    /**
     * Returns the params needed for an Amazon Pay URL
     *
     * @return
     */
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
            // for now using some random UUID as seller order id
            put("sellerOrderId", item.getText().toString() + "_" + UUID.randomUUID().toString());
            put("transactionTimeout", "1000");
        }};
    }


    String readStream(InputStream in) {
        BufferedReader reader = null;
        StringBuilder response = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return response.toString();
    }

    /**
     * Makes a call to tiny URL API to get a shortened url
     */
    private class ShortenUrl extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            try {
                String shortenUrl = "http://tinyurl.com/api-create.php";
                shortenUrl = shortenUrl + "?url=" + strings[0];
                HttpURLConnection urlConnection = (HttpURLConnection) new URL(shortenUrl).openConnection();
                int responseCode = urlConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String data = readStream(urlConnection.getInputStream()).trim();
                    Log.d("response", data);
                    return data;
                }
            } catch (Exception e) {
                Log.wtf("Exception", "Exception here", e);
            }
            return null;
        }


    }

    /**
     * Makes a call to Amazon Pay backend to sign the payload, then returns the final initiate payment url
     */
    private class MerchantBackendTask extends AsyncTask<Void, Void, String> {

        private static final String LOG_TAG = "merchant server";

        @Override
        protected String doInBackground(Void... strings) {
            try {
                Log.d(LOG_TAG, "Fetching from merchant endpoint");
                Uri uri = createUri(new URL("http://ec2-35-162-20-220.us-west-2.compute.amazonaws.com"),
                        getParams(),
                        "/prod/signAndEncrypt.jsp");
                Log.d("requestUri", uri.toString());
                HttpURLConnection urlConnection = (HttpURLConnection) new URL(uri.toString()).openConnection();
                int responseCode = urlConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String data = readStream(urlConnection.getInputStream()).trim();
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
                } else {
                    Log.d(LOG_TAG, String.format("Unable to sign payload. Received following status code: %d",
                            responseCode));
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
