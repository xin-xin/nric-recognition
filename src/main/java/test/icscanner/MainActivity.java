package test.icscanner;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.chrisbanes.photoview.PhotoView;
import com.googlecode.leptonica.android.GrayQuant;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener, View.OnDragListener, Toolbar.OnMenuItemClickListener, com.github.chrisbanes.photoview.OnMatrixChangedListener {
    private Toolbar toolbar;
    private EditText etName, etNRIC, etDOB, etAddress;
    private TextView TVScanned;
    private PhotoView img;
    private ImageView imgOverlay;
    private String showText;
    ProgressDialog progressCopy; //progressOcr
    TessBaseAPI baseApi;
    private Pix pix;
    private Bitmap bitmap, temp;
    private Uri outputFileUri;
    private int startX = 0, startY = 0, endX = 0, endY = 0;
    AsyncTask<Void, Void, Void> copy = new copyTask();
    //AsyncTask<Bitmap, Void, Void> ocr = new ocrTask();
    private static final String DATA_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.icscanner/";
    private static final int PERMREQCODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMREQCODE);
        }
        //TODO remove the ocr progress dialog, check imaging(otsu algo)
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ViewCompat.setElevation(toolbar, 10);
        toolbar.setOnMenuItemClickListener(this);
        TVScanned = (TextView) findViewById(R.id.textView);
        etName = (EditText) findViewById(R.id.nameData);
        etNRIC = (EditText) findViewById(R.id.NRICData);
        etDOB = (EditText) findViewById(R.id.dobData);
        etAddress = (EditText) findViewById(R.id.addressData);
        img = (PhotoView) findViewById(R.id.imageView);
        imgOverlay = (ImageView) findViewById(R.id.imgOverlay);
        //test.setMovementMethod(new ScrollingMovementMethod());
        TVScanned.setOnTouchListener(this);
        etName.setOnDragListener(this);
        etNRIC.setOnDragListener(this);
        etDOB.setOnDragListener(this);
        etAddress.setOnDragListener(this);
        img.setOnMatrixChangeListener(this);
        img.setMaximumScale(100f);
        bitmap = BitmapFactory.decodeResource(this.getResources(), R.drawable.blank);
        img.setImageBitmap(bitmap);
        //progressOcr = new ProgressDialog(this);
        progressCopy = new ProgressDialog(MainActivity.this);
        progressCopy.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressCopy.setIndeterminate(true);
        progressCopy.setCancelable(false);
        progressCopy.setTitle("Dictionaries");
        progressCopy.setMessage("Copying dictionary files");
        copy.execute();
    }

    private Bitmap drawBox(Bitmap srcBitmap) {
        int borderWidth = 50;
        int newStartX = 0;
        int newStartY = (int) (srcBitmap.getHeight() * 0.4);
        int newEndX = srcBitmap.getWidth();
        int newEndY = (int) (srcBitmap.getHeight() * 0.6);
        Bitmap dstBitmap = Bitmap.createBitmap(
                srcBitmap.getWidth(),
                srcBitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(dstBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.argb(255, 32, 178, 170));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);
        paint.setAntiAlias(true);
        //left, top, right, bottom
        Rect rect = new Rect(newStartX, newStartY, newEndX, newEndY);
        //canvas.drawBitmap(srcBitmap, 0, 0, null);
        canvas.drawRect(rect, paint);
        return dstBitmap;
    }

    @Override
    public void onMatrixChanged(RectF rect) {
        startX = (int) Math.abs(rect.left);
        startY = (int) Math.abs(rect.top);
        endX = startX + img.getWidth();
        endY = startY + img.getHeight();
        double width, height;
        height = rect.bottom - rect.top;
        width = rect.right - rect.left;
        temp = zoomCrop(bitmap, height, width);
    }


    @Override
    public boolean onDrag(View v, DragEvent event) {
        if (event.getAction() == DragEvent.ACTION_DROP) {
            TextView dropped = (TextView) event.getLocalState();
            TextView dropTarget = (TextView) v;
            dropTarget.setFocusableInTouchMode(true);
            dropTarget.setFocusableInTouchMode(true);
            dropTarget.setText(dropped.getText());
        }
        return true;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int noOfTouches = event.getPointerCount();
        switch (v.getId()) {
            case R.id.textView:
                if (event.getAction() == MotionEvent.ACTION_DOWN && noOfTouches == 1) {
//                    progressOcr.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//                    progressOcr.setIndeterminate(true);
//                    progressOcr.setCancelable(false);
//                    progressOcr.setTitle("OCR");
//                    progressOcr.setMessage("Extracting text, please wait");
//                    progressOcr.show();
                    new ocrTask().execute(temp);
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
                    v.startDrag(null, shadowBuilder, v, 0);
                    return true;
                }
                break;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMREQCODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Toast.makeText(getApplicationContext(), "Permission granted!", Toast.LENGTH_SHORT).show();

                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        AlertDialog.Builder al = new AlertDialog.Builder(this);
                        al.setCancelable(false);
                        al.setTitle("Storage permission");
                        al.setMessage("You need to grant storage permission to use this app.");
                        al.setNeutralButton("Quit", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finish();
                            }
                        });
                        al.show();
                    } else {
                        AlertDialog.Builder al = new AlertDialog.Builder(this);
                        al.setCancelable(false);
                        al.setTitle("Storage permission denied").setMessage("This app downloads file to your SD card. Go to settings to grant storage permission.").show();
                        al.setNeutralButton("Quit", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int arg1) {
                                finish();
                            }
                        });
                        al.show();

                    }

                }
                break;
        }
    }

    private void loadImage(Uri uri) {
        InputStream image_stream = null;
        try {
            image_stream = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 16;
        bitmap = BitmapFactory.decodeStream(image_stream);
        pix = com.googlecode.leptonica.android.ReadFile.readBitmap(bitmap);
        OtsuThresholder otsuThresholder = new OtsuThresholder();
        int threshold = otsuThresholder.doThreshold(pix.getData());
        threshold += 15;
        bitmap = com.googlecode.leptonica.android.WriteFile.writeBitmap(GrayQuant.pixThresholdToBinary(pix, threshold));
        img.setImageBitmap(bitmap);
        imgOverlay.setImageBitmap(drawBox(bitmap));
    }

    private Bitmap zoomCrop(Bitmap srcBitmap, double height, double width) {
        int newStartX = (int) ((double) startX * (srcBitmap.getWidth() / width));
        int newStartY = (int) ((double) startY * (srcBitmap.getHeight() / height));
        int newEndX = (int) ((double) endX * (srcBitmap.getWidth() / width));
        int newEndY = (int) ((double) endY * (srcBitmap.getHeight() / height));
        newStartY += ((newEndY - newStartY) * 0.4);
        newEndY -= ((newEndY - newStartY) * 0.6);
        if (newEndX > newStartX && newEndY > newStartY && newEndX < srcBitmap.getWidth() && newEndY < srcBitmap.getHeight()) {
            srcBitmap = Bitmap.createBitmap(srcBitmap, newStartX, newStartY, newEndX - newStartX, newEndY - newStartY);
        }
        return srcBitmap;
    }

    private String recognizeText(Bitmap cropped) {
        String language = "eng";
        String textScanned;
        baseApi = new TessBaseAPI();
        baseApi.init(DATA_PATH, language, TessBaseAPI.OEM_TESSERACT_ONLY);
        baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-");
        baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);
        baseApi.setImage(cropped);
        textScanned = baseApi.getUTF8Text();
        return textScanned;
    }

    private void copyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("trainneddata");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        for (String filename : files) {
            Log.i("files", filename);
            InputStream in = null;
            OutputStream out = null;
            String dirout = DATA_PATH + "tessdata/";
            File outFile = new File(dirout, filename);
            if (!outFile.exists()) {
                try {
                    in = assetManager.open("trainneddata/" + filename);
                    (new File(dirout)).mkdirs();
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                    in.close();
                    in = null;
                    out.flush();
                    out.close();
                    out = null;
                } catch (IOException e) {
                    Log.e("tag", "Error creating files", e);
                }
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == 1) {
                final boolean isCamera;
                if (data == null || data.getData() == null) {
                    isCamera = true;
                } else {
                    final String action = data.getAction();
                    if (action == null) {
                        isCamera = false;
                    } else {
                        isCamera = action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    }
                }
                Uri selectedImageUri;
                if (isCamera) {
                    selectedImageUri = outputFileUri;
                    loadImage(selectedImageUri);

                } else {
                    selectedImageUri = data == null ? null : data.getData();
                    loadImage(selectedImageUri);
                }
            }
        }
    }

    private void selectImage() {
        final String fname = "img_" + System.currentTimeMillis() + ".jpg";
        final File sdImageMainDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), fname);
        outputFileUri = Uri.fromFile(sdImageMainDirectory);
        final List<Intent> cameraIntents = new ArrayList<Intent>();
        final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
        for (ResolveInfo res : listCam) {
            final String packageName = res.activityInfo.packageName;
            final Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(packageName);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            cameraIntents.add(intent);
        }

        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_PICK);
        final Intent chooserIntent = Intent.createChooser(galleryIntent, "Select Source");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[cameraIntents.size()]));
        startActivityForResult(chooserIntent, 1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.new_scan:
                selectImage();
                break;
        }
        return true;
    }

    private class copyTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressCopy.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.i("CopyTask", "copying..");
            copyAssets();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            progressCopy.cancel();

        }
    }

    private class ocrTask extends AsyncTask<Bitmap, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Bitmap... params) {
            Log.i("OCRTask", "extracting..");
            showText = recognizeText(params[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            super.onPostExecute(v);
            if (showText.equals("")) {
                TVScanned.setText("NO TEXT DETECTED!");
            } else {
                TVScanned.setText(showText);
            }
            //progressOcr.cancel();
        }
    }
}
