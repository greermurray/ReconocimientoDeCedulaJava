package com.rodelag.tecnologia.cedulapruebajava;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalizadorDeReconocimientoDeTexto implements ImageAnalysis.Analyzer {

    private final String cedulaObjetivo;
    private final CallbackDeReconocimientoDeTexto callback;
    private final ExecutorService executorService;

    public AnalizadorDeReconocimientoDeTexto(String cedulaObjetivo, CallbackDeReconocimientoDeTexto callback) {
        this.cedulaObjetivo = cedulaObjetivo;
        this.callback = callback;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @OptIn(markerClass = ExperimentalGetImage.class) @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        executorService.execute(() -> {
            InputImage image = InputImage.fromMediaImage(Objects.requireNonNull(imageProxy.getImage()), imageProxy.getImageInfo().getRotationDegrees());
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(result -> {
                        for (Text.TextBlock block : result.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                for (Text.Element element : line.getElements()) {
                                    //INFO: Aquí se puede hacer para que solo se detecte el texto deseado, por ejemplo, que solo detecte el número de la cédula que sea igual a la de la cotización.
                                    if (element.getText().equals(cedulaObjetivo)) {
                                        callback.alDetectarTexto(element.getText());
                                    }
                                }
                            }
                        }
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        e.printStackTrace();
                        imageProxy.close();
                    });
        });
    }

    public interface CallbackDeReconocimientoDeTexto {
        void alDetectarTexto(String cedula);
    }
}