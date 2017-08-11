package test.icscanner;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
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

public class MainActivity extends AppCompatActivity implements TextView.OnTouchListener, EditText.OnDragListener, Toolbar.OnMenuItemClickListener, com.github.chrisbanes.photoview.OnMatrixChangedListener, com.github.chrisbanes.photoview.OnViewDragListener {
    private Toolbar toolbar;
    private EditText etName, etNRIC, etDOB, etAddress;
    private TextView TVScanned;
    private PhotoView img;
    private ImageView imgOverlay;
    private String showText;
    ProgressDialog progressCopy; //progressOcr
    TessBaseAPI baseApi;
    private Pix pix;
    private Bitmap bitmap;
    private Uri outputFileUri;
    AsyncTask<Void, Void, Void> copy = new copyTask();
    //AsyncTask<Bitmap, Void, Void> ocr = new ocrTask();
    private static final String DATA_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.icscanner/";
    private static final int PERMREQCODE = 1;
    private int OMCHeight = 0, OMCWidth = 0, OMCStartX = 0, OMCStartY = 0, OMCEndX = 0, OMCEndY = 0;
    private int ODSx = 0, ODSy = 0, ODEx = 0, ODEy = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //TODO check imaging, REGEX O to 0
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMREQCODE);
        } else {
            //progressOcr = new ProgressDialog(this);
            progressCopy = new ProgressDialog(MainActivity.this);
            progressCopy.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressCopy.setIndeterminate(true);
            progressCopy.setCancelable(false);
            progressCopy.setTitle("Dictionaries");
            progressCopy.setMessage("Copying dictionary files");
            copy.execute();
        }

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
        img.setOnViewDragListener(this);
        img.setMaximumScale(100f);
        bitmap = BitmapFactory.decodeResource(this.getResources(), R.drawable.blank);
        img.setImageBitmap(bitmap);
    }

    @Override
    public void onDrags(float startX, float startY, float endX, float endY) {
        ODSx = (int) startX;
        ODSy = (int) startY;
        ODEx = (int) endX;
        ODEy = (int) endY;
        imgOverlay.setImageBitmap(drawBox(bitmap, (int) startX, (int) startY, (int) endX, (int) endY));
    }

    @Override
    public void onMatrixChanged(RectF rect) {
        OMCStartX = (int) Math.abs(rect.left);
        OMCStartY = (int) Math.abs(rect.top);
        OMCEndX = OMCStartX + img.getWidth();
        OMCEndY = OMCStartY + img.getHeight();
        OMCHeight = (int) (rect.bottom - rect.top);
        OMCWidth = (int) (rect.right - rect.left);
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
                    /*progressOcr.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    progressOcr.setIndeterminate(true);
                    progressOcr.setCancelable(false);
                    progressOcr.setTitle("OCR");
                    progressOcr.setMessage("Extracting text, please wait");
                    progressOcr.show();*/
                    Bitmap crop = zoomCrop(bitmap, OMCHeight, OMCWidth, OMCStartX, OMCStartY, OMCEndX, OMCEndY);
                    crop = zoomCrop(crop, img.getHeight(), img.getWidth(), ODSx, ODSy, ODEx, ODEy);
                    pix = com.googlecode.leptonica.android.ReadFile.readBitmap(crop);
                    OtsuThresholder otsuThresholder = new OtsuThresholder();
                    int threshold = otsuThresholder.doThreshold(pix.getData());
                    threshold += 15;
                    crop = com.googlecode.leptonica.android.WriteFile.writeBitmap(GrayQuant.pixThresholdToBinary(pix, threshold));
                    new ocrTask().execute(crop);
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
                    //progressOcr = new ProgressDialog(this);
                    progressCopy = new ProgressDialog(MainActivity.this);
                    progressCopy.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    progressCopy.setIndeterminate(true);
                    progressCopy.setCancelable(false);
                    progressCopy.setTitle("Dictionaries");
                    progressCopy.setMessage("Copying dictionary files");
                    copy.execute();

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

    private void loadImage(Uri uri) {
        InputStream image_stream = null;
        try {
            image_stream = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 32;
        bitmap = BitmapFactory.decodeStream(image_stream);
        try {
            ExifInterface exif = new ExifInterface(getRealPathFromURI(uri));
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotate = 0;

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }
            if (rotate != 0) {
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mtx, false);
            }
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        } catch (IOException e) {
        }
        img.setImageBitmap(bitmap);
        imgOverlay.setImageBitmap(drawBox(bitmap, 0, 0, 0, 0));
    }

    private String getRealPathFromURI(Uri contentURI) {
        Cursor cursor = this.getContentResolver()
                .query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file
            // path
            return contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            return cursor.getString(idx);
        }
    }

    private String recognizeText(Bitmap cropped) {
        String language = "eng";
        String textScanned;
        baseApi = new TessBaseAPI();
        baseApi.init(DATA_PATH, language, TessBaseAPI.OEM_TESSERACT_ONLY);
        baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-#");
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

    private Bitmap drawBox(Bitmap srcBitmap, int sX, int sY, int eX, int eY) {
        sX = sX * srcBitmap.getWidth() / img.getWidth();
        sY = sY * srcBitmap.getHeight() / img.getHeight();
        eX = eX * srcBitmap.getWidth() / img.getWidth();
        eY = eY * srcBitmap.getHeight() / img.getHeight();
        int borderWidth = 25;
        Bitmap dstBitmap = Bitmap.createBitmap(
                srcBitmap.getWidth(),
                srcBitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(dstBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#20B2AA"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);
        paint.setAntiAlias(true);
        Rect rect = new Rect(sX, sY, eX, eY);
        //canvas.drawBitmap(srcBitmap, 0, 0, null);
        canvas.drawRect(rect, paint);
        return dstBitmap;

    }

    private Bitmap zoomCrop(Bitmap srcBitmap, double height, double width, int startX, int startY, int endX, int endY) {
        int newStartX = (int) ((double) startX * (srcBitmap.getWidth() / width));
        int newStartY = (int) ((double) startY * (srcBitmap.getHeight() / height));
        int newEndX = (int) ((double) endX * (srcBitmap.getWidth() / width));
        int newEndY = (int) ((double) endY * (srcBitmap.getHeight() / height));
        if (newEndX > newStartX && newEndY > newStartY && newEndX < srcBitmap.getWidth() && newEndY < srcBitmap.getHeight()) {
            srcBitmap = Bitmap.createBitmap(srcBitmap, newStartX, newStartY, newEndX - newStartX, newEndY - newStartY);
        }
        return srcBitmap;
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