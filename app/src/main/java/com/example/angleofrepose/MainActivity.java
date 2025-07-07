package com.example.angleofrepose;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.exifinterface.media.ExifInterface;
import android.graphics.Matrix;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private Bitmap bitmap;
    private String currentPhotoPath;
    private TextView messageArea;

    private ArrayList<float[]> points = new ArrayList<>();  // Stores clicked points

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton btnCapture = findViewById(R.id.btnCapture);
        ImageButton btnSelect = findViewById(R.id.btnSelect);
        ImageButton btnReset = findViewById(R.id.btnReset);
        ImageButton btnUndo = findViewById(R.id.btnUndo);
        imageView = findViewById(R.id.imageView);

        messageArea = findViewById(R.id.messageArea);
        messageArea.setText("Welcome! Load an image to start.");

        ImageButton btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveAnnotatedImage());

        btnCapture.setOnClickListener(v -> {
            checkCameraPermission();
        });

/*      ImageButton infoButton = findViewById(R.id.infoButton);
        infoButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HelpActivity.class);
            startActivity(intent);
        }); */

        btnSelect.setOnClickListener(v -> selectImageFromGallery());
        btnReset.setOnClickListener(v -> resetDots());
        btnUndo.setOnClickListener(v -> undoLastPoint());

        imageView.setOnTouchListener(new View.OnTouchListener() {
            private int selectedPointIndex = -1;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (bitmap == null) {
                    Toast.makeText(MainActivity.this, "Load an image first!", Toast.LENGTH_SHORT).show();
                    return true;
                }

                float x = event.getX();
                float y = event.getY();
                float[] coords = mapTouchToImage(x, y);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        selectedPointIndex = findNearestPoint(coords);
                        if (selectedPointIndex == -1 && points.size() < 3) {
                            points.add(coords);
                            selectedPointIndex = points.size() - 1;
                        }
                        drawPoints();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (selectedPointIndex != -1) {
                            points.set(selectedPointIndex, coords);
                            drawPoints();
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        selectedPointIndex = -1;
                        if (points.size() == 3) {
                            calculateAngle();
                        }
                        break;
                }
                return true;
            }
        });

    }

    private float[] mapTouchToImage(float touchX, float touchY) {
        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();
        float imageWidth = bitmap.getWidth();
        float imageHeight = bitmap.getHeight();

        float xRatio = imageWidth / imageViewWidth;
        float yRatio = imageHeight / imageViewHeight;

        return new float[]{touchX * xRatio, touchY * yRatio};
    }

    // Function to check if permission is granted
    private boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    // Request permission
    private void requestPermission(String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // Needed only for Android 9 and below
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{permission}, 101);
            }
        }
    }

    private void checkCameraPermission() {
        // Check if camera permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            // Check if user denied before and show explanation
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, "Camera permission is required to capture images.", Toast.LENGTH_LONG).show();
            }

            // Request camera permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        } else {
            captureImage(); // Permission already granted
        }
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted! You can now capture images.", Toast.LENGTH_SHORT).show();
                captureImage();
            } else {
                // Check if user denied permanently
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "Camera permission denied permanently. Enable it from Settings.", Toast.LENGTH_LONG).show();
                    openAppSettings();
                } else {
                    Toast.makeText(this, "Camera permission denied. Please grant it to continue.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    // Capture Image Function
    private void captureImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, capture image
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Toast.makeText(this, "Error creating file!", Toast.LENGTH_SHORT).show();
                }
                if (photoFile != null) {
                    Uri imageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                    cameraLauncher.launch(intent);
                }
            }
        } else {
            // Check if the user has denied permission before
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "Camera permission is required to capture images.", Toast.LENGTH_LONG).show();
                } else {
                    // Directly request permission
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
                }
            }
        }
    }


    // Create Image File for Camera
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                "IMG_" + timeStamp, /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    // Handle Camera Result
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                            bitmap = rotateImageIfRequired(bitmap, currentPhotoPath);
                            imageView.setImageBitmap(bitmap);
                            showPlaceDotsPopup();
                            points.clear();
                        }
                    });

    // Select Image from Gallery
    private void selectImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    // Handle Gallery Result
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri selectedImage = result.getData().getData();
                            try {
                                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                                bitmap = rotateImageIfRequired(bitmap, getPathFromUri(selectedImage));
                                imageView.setImageBitmap(bitmap);
                                showPlaceDotsPopup();
                                points.clear();

                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    });

    private String getPathFromUri(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(columnIndex);
            cursor.close();
            return path;
        }
        return null;
    }

    private Bitmap rotateImageIfRequired(Bitmap bitmap, String imagePath) {
        if (imagePath == null) return bitmap;
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotationAngle = 0;

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationAngle = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationAngle = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationAngle = 270;
                    break;
            }

            if (rotationAngle != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationAngle);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    // ---------- Placing Dots on the Image ----------
    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    


    // Function to draw points on the image
    private void drawPoints() {
        if (bitmap == null) {
            Toast.makeText(this, "No image loaded!", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap tempBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(tempBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(10); // thickness of the lines

        // Draw circles for selected points
        float dotSize = bitmap.getWidth() * 0.02f;
        for (float[] point : points) {
            canvas.drawCircle(point[0], point[1], dotSize, paint);
        }

        // Draw lines between points (only if at least 2 points are selected)
        if (points.size() >= 2) {
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < points.size() - 1; i++) {
                float[] start = points.get(i);
                float[] end = points.get(i + 1);
                canvas.drawLine(start[0], start[1], end[0], end[1], paint);
            }
        }

        imageView.setImageBitmap(tempBitmap);
    }

    private void calculateAngle() {
        if (points.size() != 3) {
            messageArea.setText("Place exactly 3 dots.");
            return;
        }

        // Extract points
        float[] p1 = points.get(0);
        float[] p2 = points.get(1);
        float[] p3 = points.get(2);

        // Calculate the distances
        double a = Math.sqrt(Math.pow(p3[0] - p2[0], 2) + Math.pow(p3[1] - p2[1], 2));
        double b = Math.sqrt(Math.pow(p1[0] - p2[0], 2) + Math.pow(p1[1] - p2[1], 2));
        double c = Math.sqrt(Math.pow(p1[0] - p3[0], 2) + Math.pow(p1[1] - p3[1], 2));

        // Cosine Rule
        double angleRad = Math.acos((a * a + b * b - c * c) / (2 * a * b));
        double angleDeg = Math.toDegrees(angleRad);

        // Get classification message
        String classification = getClassification(angleDeg);

        // Display only the classification (hides the exact angle)
        messageArea.setText("Classification:  "+ classification);
    }

    private String getClassification(double angle) {
        int roundedAngle = (int) Math.round(angle);

        if (roundedAngle >= 25 && roundedAngle <= 30) return "Excellent";
        if (roundedAngle >= 31 && roundedAngle <= 35) return "Good";
        if (roundedAngle >= 36 && roundedAngle <= 40) return "Fair";
        if (roundedAngle >= 41 && roundedAngle <= 45) return "Passable";
        if (roundedAngle >= 46 && roundedAngle <= 55) return "Poor";
        if (roundedAngle >= 56 && roundedAngle <= 65) return "Very Poor";
        if (roundedAngle > 66) return "Poorest";
        return "Angle too small!";
    }

    private void resetDots() {
        if(!points.isEmpty()){
            points.clear();  // Remove all points
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);  // Reset to original image
            }
            messageArea.setText("All dots erased! Place new.");
        }  else {
            Toast.makeText(this, "No dots placed!", Toast.LENGTH_SHORT).show();
        }

    }

    private void undoLastPoint() {
        if (!points.isEmpty()) {
            points.remove(points.size() - 1);
            drawPoints();
            messageArea.setText("Last dot removed. Place new point.");
        } else {
            Toast.makeText(this, "No dots to undo!", Toast.LENGTH_SHORT).show();
        }
    }


    private int findNearestPoint(float[] touchPoint) {
        float threshold = bitmap.getWidth() * 0.05f; // Set a threshold for touch sensitivity
        int nearestIndex = -1;
        float minDistance = Float.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            float[] point = points.get(i);
            float distance = (float) Math.sqrt(Math.pow(touchPoint[0] - point[0], 2) + Math.pow(touchPoint[1] - point[1], 2));
            if (distance < threshold && distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    // ---------- Save Image to Gallery ----------
    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    private void saveAnnotatedImage() {
        if (bitmap == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }
        if (points.isEmpty()) {
            Toast.makeText(this, "Place dots before saving!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show "Saving..." toast before processing
        Toast savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_LONG);
        savingToast.show();

        new Thread(() -> {
            Bitmap savedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            int width = savedBitmap.getWidth();
            int height = savedBitmap.getHeight();
            int extraHeight = height / 8; // Space for the classification box (1/8 of image height)

            // Create a new bitmap with extra space at the bottom
            Bitmap finalBitmap = Bitmap.createBitmap(width, height + extraHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(finalBitmap);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(8);

            // Draw original image
            canvas.drawBitmap(savedBitmap, 0, 0, null);

            // Draw points
            float dotSize = width * 0.02f;
            for (float[] point : points) {
                canvas.drawCircle(point[0], point[1], dotSize, paint);
            }

            // Draw lines
            if (points.size() >= 2) {
                paint.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < points.size() - 1; i++) {
                    float[] start = points.get(i);
                    float[] end = points.get(i + 1);
                    canvas.drawLine(start[0], start[1], end[0], end[1], paint);
                }
            }

            // Draw black rectangle at the bottom
            Paint rectPaint = new Paint();
            rectPaint.setColor(Color.BLACK);
            rectPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0, height, width, height + extraHeight, rectPaint);

            // Get classification text
            String classification = messageArea.getText().toString();

//            // Remove ">>  " from the beginning
//            if (classification.startsWith(" >>  ")) {
//                classification = classification.substring(4); // Trim first 4 characters
//            }

            // Draw classification text
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(extraHeight * 0.5f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(classification, width / 2, height + (extraHeight / 2), textPaint);

            // Save Image to Gallery
            try {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AngleOfRepose");
                if (!dir.exists()) dir.mkdirs();

                String fileName = "repose_" + System.currentTimeMillis() + ".png";
                File file = new File(dir, fileName);

                FileOutputStream fos = new FileOutputStream(file);
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();

                // Notify gallery
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(file);
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);

                // Show success message on UI thread
                runOnUiThread(() -> Toast.makeText(this, "Image saved successfully!", Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to save image!", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    // ---------- Popup to place dots ----------
    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    private void showPlaceDotsPopup() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Place Dots")
                .setMessage("Tap on the image to place 3 dots for analysis.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
        messageArea.setText("Place Dots");
    }

}