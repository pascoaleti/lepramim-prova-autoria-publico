package com.lepramim.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;

public class CameraCaptureActivity extends ComponentActivity {
    static final String EXTRA_IMAGE_URI = "image_uri";

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Camera camera;
    private boolean flashEnabled = false;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createView());

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 3001);
        } else {
            startCamera();
        }
    }

    private LinearLayout createView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF071A33);

        TextView title = new TextView(this);
        title.setText("Ler papel");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 29);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(18), dp(18), dp(18), dp(6));
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("Aproxime o papel. Coloque mais luz. Segure parado.");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setPadding(dp(18), 0, dp(18), dp(12));
        root.addView(statusView);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(16), dp(12), dp(16), dp(16));
        actions.setBackgroundColor(0xFF071A33);

        TextView capture = actionButton("Tirar foto", 0xFFFF9800, 0xFF172033);
        capture.setOnClickListener(view -> takePhoto());
        actions.addView(capture, buttonParams());

        TextView flash = actionButton("Flash", 0xFFFFFFFF, 0xFF083B75);
        flash.setOnClickListener(view -> toggleFlash());
        actions.addView(flash, buttonParams());

        TextView cancel = actionButton("Voltar", 0xFF183A66, 0xFFFFFFFF);
        cancel.setOnClickListener(view -> finish());
        actions.addView(cancel, buttonParams());

        root.addView(actions);
        return root;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );
            } catch (Exception exception) {
                statusView.setText("Não consegui abrir a câmera. Tente escolher uma imagem.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            statusView.setText("Câmera ainda carregando. Tente novamente.");
            return;
        }

        File file = new File(getCacheDir(), "lepramim-ocr-" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(file).build();
        statusView.setText("Segure parado. Estou tirando a foto.");
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                Intent result = new Intent();
                result.putExtra(EXTRA_IMAGE_URI, Uri.fromFile(file).toString());
                setResult(RESULT_OK, result);
                finish();
            }

            @Override
            public void onError(ImageCaptureException exception) {
                statusView.setText("Não consegui tirar a foto. Coloque mais luz e tente de novo.");
            }
        });
    }

    private void toggleFlash() {
        flashEnabled = !flashEnabled;
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(flashEnabled);
            statusView.setText(flashEnabled ? "Flash ligado." : "Flash desligado.");
        } else {
            statusView.setText("Este aparelho não informou flash disponível.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 3001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            statusView.setText("Sem permissão de câmera. Use Ler print ou permita a câmera.");
        }
    }

    private TextView actionButton(String text, int background, int foreground) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(foreground);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(background);
        drawable.setCornerRadius(dp(8));
        view.setBackground(drawable);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
