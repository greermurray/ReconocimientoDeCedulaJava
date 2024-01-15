package com.rodelag.tecnologia.cedulapruebajava;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import com.google.common.util.concurrent.ListenableFuture;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import org.jetbrains.annotations.NotNull;
import android.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.widget.Button;

public class MainActivity extends AppCompatActivity implements AnalizadorDeReconocimientoDeTexto.CallbackDeReconocimientoDeTexto {

    private TextView tvMensaje;
    private ImageCapture capturaDeImagen = null;
    private PreviewView vistaCamara;
    private ImageAnalysis analizarImagen;
    private Preview vistaPrevia;
    private Button btnFacturar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vistaCamara = findViewById(R.id.camara);
        tvMensaje = findViewById(R.id.tvMensaje);
        btnFacturar = findViewById(R.id.btnFacturar);

        comenzarConLaCamara();
    }

    private void comenzarConLaCamara() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider proveedorCamara = cameraProviderFuture.get();
                bindPreview(proveedorCamara);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("Cámara", "Error al vincular la cámara", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        analizarImagen = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        capturaDeImagen = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        //INFO: Aquí se puede cambiar la cédula que se desea detectar.
        AnalizadorDeReconocimientoDeTexto analizador = new AnalizadorDeReconocimientoDeTexto("0-000-000", this);
        analizarImagen.setAnalyzer(ContextCompat.getMainExecutor(this), analizador);

        Preview vistaPrevia = new Preview.Builder().build();
        vistaPrevia.setSurfaceProvider(vistaCamara.getSurfaceProvider());

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, vistaPrevia, analizarImagen, capturaDeImagen);
    }

    public Bitmap reducirTamanoImagen(String rutaImagen, int ancho, int alto) {
        final BitmapFactory.Options opcion = new BitmapFactory.Options();
        opcion.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(rutaImagen, opcion);
        opcion.inSampleSize = calcularFactorEscala(opcion, ancho, alto);
        opcion.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(rutaImagen, opcion);
    }

    public int calcularFactorEscala(BitmapFactory.Options opciones, int anchoRequerido, int altoRequerido) {
        final int altura = opciones.outHeight;
        final int ancho = opciones.outWidth;
        int tamano = 1;

        if (altura > altoRequerido || ancho > anchoRequerido) {
            final int mitadAltura = altura / 2;
            final int mitadAncho = ancho / 2;

            while ((mitadAltura / tamano) >= altoRequerido
                    && (mitadAncho / tamano) >= anchoRequerido) {
                tamano *= 2;
            }
        }

        return tamano;
    }

    public Bitmap orientarImagen(String rutaImagen, Bitmap bitmap) {
        ExifInterface exifInterface = null;
        try {
            exifInterface = new ExifInterface(rutaImagen);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int orientacion = ExifInterface.ORIENTATION_NORMAL;

        if (exifInterface != null) {
            orientacion = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }

        switch (orientacion) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                bitmap = rotarImagen(bitmap, 90);
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                bitmap = rotarImagen(bitmap, 180);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                bitmap = rotarImagen(bitmap, 270);
                break;

            default:
                break;
        }

        return bitmap;
    }

    public Bitmap rotarImagen(Bitmap bitmap, float angulo) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angulo);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public byte[] convertirBitmapABytes(Bitmap bitmap) {
        ByteArrayOutputStream flujoDeSalida = new ByteArrayOutputStream();
        //INFO: Bajamos la calidad de la imagen para que no sea tan pesada al enviarla al servidor y sea más rápida la subida. 80 es un buen número.
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, flujoDeSalida);
        return flujoDeSalida.toByteArray();
    }

    public void enviarImagen(Context contextoLocal, Uri fotoUri) {
        OkHttpClient clienteHttp = new OkHttpClient();

        try {
            //INFO: Obtener el InputStream del Uri
            InputStream flujoDeEntrada = contextoLocal.getContentResolver().openInputStream(fotoUri);
            assert flujoDeEntrada != null;
            byte[] bytes = new byte[flujoDeEntrada.available()];
            flujoDeEntrada.read(bytes);

            //INFO: Reducir el tamaño de la imagen para que no sea tan pesada al enviarla al servidor y sea más rápida la subida.
            Bitmap bitmap = reducirTamanoImagen(fotoUri.getPath(), 800, 600);
            //INFO: Orientar verticalmente la imagen para que se vea correctamente en el servidor.
            bitmap = orientarImagen(fotoUri.getPath(), bitmap);
            bytes = convertirBitmapABytes(bitmap);

            //INFO: Crear un RequestBody a partir de los bytes del archivo
            RequestBody imagen = RequestBody.create(bytes, MediaType.parse("image/jpeg"));

            //INFO: Obtener el nombre del archivo
            String nombreImagen = new File(Objects.requireNonNull(fotoUri.getPath())).getName();

            //INFO: Verificar si el archivo de la imagen existe
            if (!new File(fotoUri.getPath()).exists()) {
                return;
            }

            //INFO: Crear un cuerpo de solicitud de varias partes
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("imagenes[]", nombreImagen, imagen)
                    .addFormDataPart("productoElconix", "false")
                    .addFormDataPart("carpetaAmazonS3", "cedulasfacturacion")
                    .addFormDataPart("bucketAmazonS3", "rodelag-imagenes")
                    .build();

            //INFO: Crear una solicitud POST
            Request solicitud = new Request.Builder()
                    .url("https://dev.rodelag.com/amazonS3/")
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer TOKEN_DE_AUTORIZACION")
                    .build();

            clienteHttp.newCall(solicitud).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    if (!response.isSuccessful()) throw new IOException("Error inesperado: " + response);
                    Log.e("PRUEBA-RODELAG", response.body() != null ? response.body().string() : null);
                }
            });

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    //INFO: Este método se ejecuta solo cuando se detecta la cédula.
    @Override
    public void seEjecutaAlDetectarCedula(String texto) {
        //INFO: Verificar si la cámara está vinculada antes de intentar tomar una foto
        ProcessCameraProvider proveedorCamara = null;
        try {
            proveedorCamara = ProcessCameraProvider.getInstance(MainActivity.this).get();
            //INFO: Verificar si la cámara está vinculada antes de intentar tomar una foto
            if (proveedorCamara.isBound(capturaDeImagen)) {
                //INFO: Tomar la foto y enviarla al servidor
                tomarFotoYEnviarAlServidor();
                runOnUiThread(() -> {
                    tvMensaje.setBackgroundColor(Color.GREEN);
                    tvMensaje.setText("Cédula detectada: " + texto);
                    tvMensaje.setVisibility(View.VISIBLE);
                    btnFacturar.setEnabled(true);
                });
            }
            //INFO: Desvincular el caso de uso para que no se siga analizando la imagen
            proveedorCamara.unbind(analizarImagen);
            //INFO: Desvincular la vista previa para que la cámara de detenga y quede con el último frame.
            proveedorCamara.unbind(vistaPrevia);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    //INFO: Este método se ejecuta solo cuando NO se detecta la cédula.
    @Override
    public void seEjecutaAlNoDetectarCedula() {
        runOnUiThread(() -> {
            tvMensaje.setBackgroundColor(Color.RED);
            tvMensaje.setText("Cedula inválida");
            tvMensaje.setVisibility(View.VISIBLE);
            btnFacturar.setEnabled(false);

            //INFO: Ocultar el mensaje de error después de 3 segundos
            new Handler().postDelayed(() -> tvMensaje.setVisibility(View.GONE), 3000);
        });
    }

    private void tomarFotoYEnviarAlServidor() {
        //INFO: Crear el archivo donde se guardará la foto
        //INFO: Ponerle como nombre el ID de la cotización.
        File fotoOutputFile = new File(getExternalFilesDir(null), "000000.jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(fotoOutputFile).build();

        //INFO: Tomar la foto
        capturaDeImagen.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                //INFO: Enviar la imagen al servidor
                Uri imageUri = Uri.fromFile(fotoOutputFile);
                enviarImagen(MainActivity.this, imageUri);

                ProcessCameraProvider proveedorCamara = null;
                try {
                    proveedorCamara = ProcessCameraProvider.getInstance(MainActivity.this).get();

                    //INFO: Desvincular todos los casos de uso para con la cámara.
                    proveedorCamara.unbindAll();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("PRUEBA-RODELAG", "Error al tomar la foto", exception);
            }
        });
    }
}