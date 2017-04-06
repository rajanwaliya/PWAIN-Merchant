package amazon.vardaan.pwain_merchant;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/**
 * Responsible for converting the URL to a QR Code
 */
public class QrActivity extends AppCompatActivity {
    private static String LOG_TAG = "QRActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr);
        //9. gets the url that has been passed to it
        String url = getIntent().getExtras().getString("url");
        //10. gets a handle on where the QR image will be displayed. Refer to activity_qr.xml in res/layout
        ImageView imageView = (ImageView) findViewById(R.id.qrCode);
        try {
            Bitmap bitmap = encodeAsBitmap(url);
            //11. Sets QR in image view
            imageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            Log.wtf(LOG_TAG, "exception while creating QR", e);
        }
    }

    /**
     * Converts String to QR Code
     *
     * @param str
     * @return
     * @throws WriterException
     */
    Bitmap encodeAsBitmap(String str) throws WriterException {
        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(str,
                    BarcodeFormat.QR_CODE, 600, 600, null);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            Log.wtf(LOG_TAG, "exception while creating QR", iae);
            return null;
        }
        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? 0xFF000000 : 0xFFFFFFFF; // setting black and white colors.
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, 600, 0, 0, w, h);
        return bitmap;
    }
}
